package dev.vigia.backend.config;

import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.AlertaAnimalAislado;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.RegistroCohesion;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.AlertaAnimalAisladoRepository;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.RegistroCohesionRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class CohesionBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EstablecimientoRepository establecimientoRepository;
    private final TelemetriaRepository telemetriaRepository;
    private final AlertaRepository alertaRepository;
    private final RegistroCohesionRepository registroCohesionRepository;
    private final AnimalRepository animalRepository;
    private final AlertaAnimalAisladoRepository alertaAnimalAisladoRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    /** Minutos seguidos fuera del grupo antes de alertar: filtra lecturas GPS erráticas y salidas cortas. */
    @Value("${vigia.cohesion.minutos-fuera-minimo:15}")
    private long minutosFueraMinimo = 15;

    public CohesionBatchConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            EstablecimientoRepository establecimientoRepository, TelemetriaRepository telemetriaRepository,
            AlertaRepository alertaRepository, RabbitTemplate rabbitTemplate,
            RegistroCohesionRepository registroCohesionRepository, AnimalRepository animalRepository,
            AlertaAnimalAisladoRepository alertaAnimalAisladoRepository) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.establecimientoRepository = establecimientoRepository;
        this.telemetriaRepository = telemetriaRepository;
        this.alertaRepository = alertaRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.registroCohesionRepository = registroCohesionRepository;
        this.animalRepository = animalRepository;
        this.alertaAnimalAisladoRepository = alertaAnimalAisladoRepository;
    }

    @Bean
    @SuppressWarnings("null")
    public RepositoryItemReader<Establecimiento> establecimientoItemReader() {
        return new RepositoryItemReaderBuilder<Establecimiento>()
                .name("establecimientoItemReader")
                .repository(establecimientoRepository)
                .methodName("findAll")
                .sorts(Collections.singletonMap("id", Sort.Direction.ASC))
                .build();
    }

    @Bean
    @SuppressWarnings("null")
    public ItemProcessor<Establecimiento, Establecimiento> cohesionProcessor() {
        return establecimiento -> {
            LocalDateTime hace24h = LocalDateTime.now().minusHours(24);
            var telemetriaList = telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(
                    establecimiento.getId(), hace24h, LocalDateTime.now(), null);

            if (telemetriaList.isEmpty()) {
                return establecimiento;
            }

            double umbral = establecimiento.getUmbralAislamientoM() != null ? establecimiento.getUmbralAislamientoM()
                    : 100.0;

            // Un request a ml-geo por rodeo: cada animal se compara solo contra su propio rodeo.
            // Los animales sin rodeo forman un grupo aparte (clave null). Un rodeo con un solo
            // animal reportando nunca queda aislado: ml-geo no tiene contra quién compararlo.
            Map<Long, List<Telemetria>> porRodeo = new LinkedHashMap<>();
            for (Telemetria t : telemetriaList) {
                Animal animal = t.getCaravana().getAnimal();
                if (t.getLatitud() == null || t.getLongitud() == null || animal == null) {
                    continue;
                }
                Long rodeoId = animal.getRodeoLote() != null ? animal.getRodeoLote().getId() : null;
                porRodeo.computeIfAbsent(rodeoId, k -> new ArrayList<>()).add(t);
            }

            porRodeo.forEach((rodeoId, telemetriaDelRodeo) -> analizarRodeo(establecimiento, rodeoId, umbral,
                    telemetriaDelRodeo));
            return establecimiento;
        };
    }

    private void analizarRodeo(Establecimiento establecimiento, Long rodeoId, double umbral,
            List<Telemetria> telemetriaList) {
        List<Map<String, Object>> coordenadas = telemetriaList.stream()
                .map(t -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("animal_id", t.getCaravana().getAnimal().getId().toString());
                    m.put("lat", t.getLatitud());
                    m.put("lng", t.getLongitud());
                    m.put("timestamp", t.getFechaHora().toString());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> request = new java.util.HashMap<>();
        request.put("historial_7d", coordenadas);
        request.put("umbral_aislamiento_m", umbral);

        try {
            // ml-geo call
            String mlGeoUrl = System.getenv().getOrDefault("ML_GEO_URL", "http://vigia-ml-geo:8003");
            ResponseEntity<GeoResponse> resp = restTemplate.postForEntity(
                    mlGeoUrl + "/api/internal/ml/geo/analyze", request, GeoResponse.class);

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                List<GeoAlerta> alertas = resp.getBody().getAlertas();
                if (alertas != null) {
                    List<GeoAlerta> analizadas = new ArrayList<>();
                    for (GeoAlerta ga : alertas) {
                        if (ga.getAnimalId() == null) {
                            continue;
                        }
                        Long animalId = Long.parseLong(ga.getAnimalId());
                        analizadas.add(ga);

                        // Guardar registro histórico del índice de cohesión por animal
                        if (ga.getIndiceCohesion() != null) {
                            Animal animal = animalDe(animalId, telemetriaList);

                            if (animal != null) {
                                RegistroCohesion reg = RegistroCohesion.builder()
                                        .animal(animal)
                                        .establecimiento(establecimiento)
                                        .fecha(LocalDateTime.now())
                                        .indiceCohesion(ga.getIndiceCohesion())
                                        .tiempoFueraGrupoS(ga.getTiempoFueraGrupoS() != null
                                                ? ga.getTiempoFueraGrupoS().longValue()
                                                : 0L)
                                        .distanciaCentroideM(ga.getDistanciaCentroideM())
                                        .build();
                                registroCohesionRepository.save(reg);
                            }
                        }
                    }
                    reconciliarAislamiento(establecimiento, rodeoId, umbral, analizadas, telemetriaList);
                }
            }
        } catch (Exception e) {
            log.error("Fallo al llamar a ml-geo para cohesion: {}", e.getMessage());
        }
    }

    /**
     * Mantiene los episodios de aislamiento del rodeo con el resultado de ml-geo:
     * - alejado con episodio abierto: refresca tiempo y distancia (mismo aislamiento, sin alerta nueva);
     * - en el grupo con episodio abierto: lo cierra (volvió). La alerta no se resuelve sola;
     * - sin datos en esta corrida: no se toca (sin datos no es "volvió");
     * - alejado al menos minutosFueraMinimo seguidos y sin episodio abierto: aislamiento nuevo, que se
     *   suma a la alerta pendiente del rodeo si hay una, o abre una alerta nueva.
     */
    private void reconciliarAislamiento(Establecimiento est, Long rodeoId, double umbral,
            List<GeoAlerta> analizadas, List<Telemetria> telemetriaList) {
        Map<Long, AlertaAnimalAislado> abiertos = alertaAnimalAisladoRepository
                .findByAnimal_Establecimiento_IdAndTsRegresoIsNull(est.getId()).stream()
                .collect(Collectors.toMap(e -> e.getAnimal().getId(), e -> e, (a, b) -> a));

        if (analizadas.stream().anyMatch(
                ga -> Boolean.TRUE.equals(ga.getAlertaAislamiento()) && ga.getTiempoFueraContinuoS() == null)) {
            log.warn("ml-geo no devolvió tiempo_fuera_continuo_s: no se abren aislamientos hasta actualizar ml-geo.");
        }

        long minimoS = minutosFueraMinimo * 60;
        LocalDateTime ahora = LocalDateTime.now();
        List<GeoAlerta> nuevos = new ArrayList<>();
        for (GeoAlerta ga : analizadas) {
            boolean alejado = Boolean.TRUE.equals(ga.getAlertaAislamiento());
            AlertaAnimalAislado abierto = abiertos.get(Long.parseLong(ga.getAnimalId()));
            if (abierto != null) {
                if (alejado) {
                    actualizar(abierto, ga);
                } else {
                    abierto.setTsRegreso(ahora);
                }
                alertaAnimalAisladoRepository.save(abierto);
            } else if (alejado && ga.getTiempoFueraContinuoS() != null && ga.getTiempoFueraContinuoS() >= minimoS
                    && animalDe(Long.parseLong(ga.getAnimalId()), telemetriaList) != null) {
                nuevos.add(ga);
            }
        }
        if (nuevos.isEmpty()) {
            return;
        }

        Alerta pendiente = alertaRepository
                .findByFiltros(est.getId(), EstadoAlerta.PENDIENTE, DisparadorAlerta.AISLAMIENTO_SOCIAL, null).stream()
                .filter(a -> Objects.equals(rodeoIdDe(a.getAnimal()), rodeoId))
                .findFirst()
                .orElse(null);
        if (pendiente != null) {
            sumarAAlertaPendiente(pendiente, nuevos, umbral, telemetriaList, ahora);
            return;
        }

        List<AlertaAnimalAislado> episodios = nuevos.stream()
                .map(ga -> nuevoEpisodio(ga, telemetriaList, ahora))
                .toList();
        Alerta alerta = new Alerta();
        alerta.setDisparador(DisparadorAlerta.AISLAMIENTO_SOCIAL);
        alerta.setTipo("AISLAMIENTO_SOCIAL");
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setTsGeneracion(ahora);
        alerta.setValorUmbral(umbral);
        alerta.setAccionRecomendada("Recorrer la zona y verificar el estado de los animales alejados del rodeo.");
        resumir(alerta, episodios, umbral, telemetriaList);

        Alerta guardada = alertaRepository.save(alerta);
        for (AlertaAnimalAislado episodio : episodios) {
            episodio.setAlerta(guardada);
            alertaAnimalAisladoRepository.save(episodio);
        }
        log.info("Generada alerta de AISLAMIENTO_SOCIAL {} con {} animales", guardada.getId(), episodios.size());
        publicar(guardada);
    }

    /**
     * Suma los aislamientos nuevos a la alerta pendiente del rodeo. Si la alerta ya lista al animal
     * (volvió y se alejó otra vez), reabre su fila en vez de agregar otra. Re-publica la alerta con
     * el mismo id para que el frontend actualice la misma tarjeta.
     */
    private void sumarAAlertaPendiente(Alerta pendiente, List<GeoAlerta> nuevos, double umbral,
            List<Telemetria> telemetriaList, LocalDateTime ahora) {
        List<AlertaAnimalAislado> episodios = new ArrayList<>(
                alertaAnimalAisladoRepository.findByAlerta_IdOrderBySegundosFueraDesc(pendiente.getId()));
        for (GeoAlerta ga : nuevos) {
            Long animalId = Long.parseLong(ga.getAnimalId());
            AlertaAnimalAislado episodio = episodios.stream()
                    .filter(e -> animalId.equals(e.getAnimal().getId()))
                    .findFirst()
                    .orElse(null);
            if (episodio != null) {
                episodio.setTsRegreso(null);
                episodio.setTsInicio(ahora);
                actualizar(episodio, ga);
            } else {
                episodio = nuevoEpisodio(ga, telemetriaList, ahora);
                episodio.setAlerta(pendiente);
                episodios.add(episodio);
            }
            alertaAnimalAisladoRepository.save(episodio);
        }

        resumir(pendiente, episodios, umbral, telemetriaList);
        alertaRepository.save(pendiente);
        log.info("Alerta de AISLAMIENTO_SOCIAL {} actualizada: {} animales nuevos", pendiente.getId(), nuevos.size());
        publicar(pendiente);
    }

    private AlertaAnimalAislado nuevoEpisodio(GeoAlerta ga, List<Telemetria> telemetriaList, LocalDateTime ahora) {
        AlertaAnimalAislado episodio = AlertaAnimalAislado.builder()
                .animal(animalDe(Long.parseLong(ga.getAnimalId()), telemetriaList))
                .tsInicio(ahora)
                .build();
        actualizar(episodio, ga);
        return episodio;
    }

    private static void actualizar(AlertaAnimalAislado episodio, GeoAlerta ga) {
        episodio.setSegundosFuera(ga.getTiempoFueraGrupoS() != null ? ga.getTiempoFueraGrupoS().longValue() : 0L);
        episodio.setDistanciaGrupoM(ga.getDistanciaCentroideM());
    }

    private static Long rodeoIdDe(Animal animal) {
        return animal != null && animal.getRodeoLote() != null ? animal.getRodeoLote().getId() : null;
    }

    /**
     * Animal principal (el que más tiempo lleva fuera) y su última posición, distancia máxima y
     * severidad: ALTA si algún animal está a más del doble del umbral.
     */
    private static void resumir(Alerta alerta, List<AlertaAnimalAislado> episodios, double umbral,
            List<Telemetria> telemetriaList) {
        AlertaAnimalAislado principal = episodios.stream()
                .max(Comparator.comparing(AlertaAnimalAislado::getSegundosFuera))
                .orElseThrow();
        double distanciaMaxima = episodios.stream()
                .map(AlertaAnimalAislado::getDistanciaGrupoM)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(umbral);
        Telemetria ultima = ultimaPosicion(principal.getAnimal().getId(), telemetriaList);

        alerta.setAnimal(principal.getAnimal());
        alerta.setValorDetectado(distanciaMaxima);
        alerta.setSeveridad(distanciaMaxima > 2 * umbral ? SeveridadAlerta.ALTA : SeveridadAlerta.MEDIA);
        alerta.setLatitud(ultima != null ? ultima.getLatitud() : null);
        alerta.setLongitud(ultima != null ? ultima.getLongitud() : null);
    }

    private void publicar(Alerta alerta) {
        try {
            AlertaEventDTO eventDTO = AlertaEventDTO.builder()
                    .id(alerta.getId())
                    .animalId(alerta.getAnimal().getId())
                    .tipo(alerta.getTipo())
                    .severidad(alerta.getSeveridad())
                    .tsGeneracion(alerta.getTsGeneracion())
                    .disparador(alerta.getDisparador())
                    .valorUmbral(alerta.getValorUmbral())
                    .valorDetectado(alerta.getValorDetectado())
                    .estado(alerta.getEstado())
                    .accionRecomendada(alerta.getAccionRecomendada())
                    .latitud(alerta.getLatitud())
                    .longitud(alerta.getLongitud())
                    .build();

            rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", eventDTO);
            log.info("Emitido evento RabbitMQ para alerta de AISLAMIENTO_SOCIAL ID: {}", alerta.getId());
        } catch (Exception e) {
            log.error("Error al emitir evento RabbitMQ para alerta {}: {}", alerta.getId(), e.getMessage());
        }
    }

    private Animal animalDe(Long animalId, List<Telemetria> telemetriaList) {
        return telemetriaList.stream()
                .map(t -> t.getCaravana().getAnimal())
                .filter(a -> animalId.equals(a.getId()))
                .findFirst()
                .orElseGet(() -> animalRepository.findById(animalId).orElse(null));
    }

    private static Telemetria ultimaPosicion(Long animalId, List<Telemetria> telemetriaList) {
        return telemetriaList.stream()
                .filter(t -> animalId.equals(t.getCaravana().getAnimal().getId()))
                .max(Comparator.comparing(Telemetria::getFechaHora))
                .orElse(null);
    }

    @Bean
    public ItemWriter<Establecimiento> cohesionWriter() {
        return items -> {
        };
    }

    @Bean
    @SuppressWarnings("null")
    public Step cohesionStep() {
        return new StepBuilder("cohesionStep", jobRepository)
                .<Establecimiento, Establecimiento>chunk(10, transactionManager)
                .reader(establecimientoItemReader())
                .processor(cohesionProcessor())
                .writer(cohesionWriter())
                .build();
    }

    @Bean
    @SuppressWarnings("null")
    public Job cohesionJob() {
        return new JobBuilder("cohesionJob", jobRepository)
                .start(cohesionStep())
                .build();
    }

    @Data
    public static class GeoResponse {
        private List<GeoAlerta> alertas;
    }

    @Data
    public static class GeoAlerta {
        @JsonProperty("animal_id")
        private String animalId;

        @JsonProperty("indice_cohesion")
        private Double indiceCohesion;

        @JsonProperty("alerta_aislamiento")
        private Boolean alertaAislamiento;

        @JsonProperty("distancia_centroide_m")
        private Double distanciaCentroideM;

        @JsonProperty("tiempo_fuera_grupo_s")
        private Double tiempoFueraGrupoS;

        @JsonProperty("tiempo_fuera_continuo_s")
        private Double tiempoFueraContinuoS;
    }
}
