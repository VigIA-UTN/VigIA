package dev.vigia.backend.service.ml;

import dev.vigia.backend.client.MlGeoClient;
import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.AnimalAisladoResponse;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.dto.ml.geo.AlertaAnimalDTO;
import dev.vigia.backend.dto.ml.geo.CoordenadaGPSDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.AlertaAnimalAisladoRepository;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.RegistroCohesionRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cohesión y aislamiento social. Recibe la telemetría de la ventana de un
 * establecimiento y, por cada rodeo (los animales sin rodeo forman su propio
 * grupo), le pide a ml-geo el índice de cohesión de cada animal con el umbral de
 * aislamiento del establecimiento. Guarda un {@link RegistroCohesion} por animal y
 * mantiene los episodios de aislamiento ({@link AlertaAnimalAislado}): una alerta
 * AISLAMIENTO_SOCIAL por rodeo que lista a los animales que se alejaron.
 */
@Slf4j
@Service
public class GeoDetectorStrategyImpl implements DetectorStrategy {

    static final double UMBRAL_AISLAMIENTO_DEFAULT = 100.0;

    private final MlGeoClient mlGeoClient;
    private final TelemetriaRepository telemetriaRepository;
    private final AlertaRepository alertaRepository;
    private final AnimalRepository animalRepository;
    private final RegistroCohesionRepository registroCohesionRepository;
    private final AlertaAnimalAisladoRepository alertaAnimalAisladoRepository;
    private final RabbitTemplate rabbitTemplate;
    /** Minutos seguidos fuera del grupo antes de alertar: filtra lecturas GPS erráticas y salidas cortas. */
    private final long minutosFueraMinimo;

    public GeoDetectorStrategyImpl(
            MlGeoClient mlGeoClient,
            TelemetriaRepository telemetriaRepository,
            AlertaRepository alertaRepository,
            AnimalRepository animalRepository,
            RegistroCohesionRepository registroCohesionRepository,
            AlertaAnimalAisladoRepository alertaAnimalAisladoRepository,
            RabbitTemplate rabbitTemplate,
            @Value("${vigia.geo.minutos-fuera-minimo:15}") long minutosFueraMinimo) {
        this.mlGeoClient = mlGeoClient;
        this.telemetriaRepository = telemetriaRepository;
        this.alertaRepository = alertaRepository;
        this.animalRepository = animalRepository;
        this.registroCohesionRepository = registroCohesionRepository;
        this.alertaAnimalAisladoRepository = alertaAnimalAisladoRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.minutosFueraMinimo = minutosFueraMinimo;
    }

    @Override
    public void process(List<Telemetria> telemetriaList, String correlationId) {
        if (telemetriaList == null || telemetriaList.isEmpty()) {
            return;
        }

        List<Telemetria> telemetriasValidas = telemetriaList.stream()
                .filter(t -> t.getLatitud() != null && t.getLongitud() != null
                        && t.getCaravana() != null && t.getCaravana().getAnimal() != null)
                .collect(Collectors.toList());

        if (telemetriasValidas.isEmpty()) {
            log.info("No hay telemetría válida con coordenadas GPS para análisis geoespacial (correlationId: {}).",
                    correlationId);
            marcarCompletados(telemetriaList);
            return;
        }

        Establecimiento establecimiento = resolverEstablecimiento(telemetriasValidas);
        double umbral = establecimiento != null && establecimiento.getUmbralAislamientoM() != null
                ? establecimiento.getUmbralAislamientoM()
                : UMBRAL_AISLAMIENTO_DEFAULT;

        // La cohesión se mide contra el propio rodeo: con un centroide único por
        // establecimiento, un rodeo entero en otro potrero quedaría "aislado".
        Map<Optional<Long>, List<Telemetria>> porRodeo = telemetriasValidas.stream()
                .collect(Collectors.groupingBy(t -> Optional.ofNullable(rodeoIdDe(t.getCaravana().getAnimal())),
                        LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<Optional<Long>, List<Telemetria>> grupo : porRodeo.entrySet()) {
            // Una falla en un rodeo (ml-geo caído, respuesta inválida) no frena a los demás:
            // su telemetría queda pendiente de inferencia y el resto se procesa igual.
            try {
                analizarRodeo(establecimiento, grupo.getKey().orElse(null), umbral, grupo.getValue(), correlationId);
            } catch (RuntimeException e) {
                marcarInferenciaPendiente(grupo.getValue(), correlationId, e);
            }
        }

        marcarCompletados(telemetriaList);
    }

    private void analizarRodeo(Establecimiento establecimiento, Long rodeoId, double umbral,
            List<Telemetria> telemetriaDelRodeo, String correlationId) {
        GeoAnalyzeRequestDTO request = GeoAnalyzeRequestDTO.builder()
                .historial7d(telemetriaDelRodeo.stream()
                        .map(t -> CoordenadaGPSDTO.builder()
                                .animalId(t.getCaravana().getAnimal().getId().toString())
                                .lat(t.getLatitud())
                                .lng(t.getLongitud())
                                .timestamp(t.getFechaHora().toString())
                                .build())
                        .collect(Collectors.toList()))
                .umbralAislamientoM(umbral)
                .build();

        GeoAnalyzeResponseDTO response = mlGeoClient.analyze(request, correlationId);
        if (response == null || response.getAlertas() == null) {
            return;
        }

        Map<Long, Animal> animales = telemetriaDelRodeo.stream()
                .map(t -> t.getCaravana().getAnimal())
                .collect(Collectors.toMap(Animal::getId, Function.identity(), (a, b) -> a));
        LocalDateTime ahora = LocalDateTime.now();

        List<Analizado> analizados = new ArrayList<>();
        for (AlertaAnimalDTO resultado : response.getAlertas()) {
            Long animalId = parsearAnimalId(resultado.getAnimalId());
            Animal animal = animalId == null ? null
                    : animales.containsKey(animalId) ? animales.get(animalId)
                            : animalRepository.findById(animalId).orElse(null);
            if (animal == null) {
                continue;
            }
            analizados.add(new Analizado(animal, resultado));

            if (resultado.getIndiceCohesion() != null && establecimiento != null) {
                registroCohesionRepository.save(RegistroCohesion.builder()
                        .animal(animal)
                        .establecimiento(establecimiento)
                        .fecha(ahora)
                        .indiceCohesion(resultado.getIndiceCohesion())
                        .tiempoFueraGrupoS(segundos(resultado.getTiempoFueraGrupoS()))
                        .distanciaCentroideM(resultado.getDistanciaCentroideM())
                        .build());
            }
        }

        if (establecimiento != null) {
            reconciliarAislamiento(establecimiento, rodeoId, umbral, analizados, telemetriaDelRodeo, ahora);
        }
    }

    /** Un animal analizado por ml-geo en esta corrida. */
    private record Analizado(Animal animal, AlertaAnimalDTO resultado) {
        boolean alejado() {
            return Boolean.TRUE.equals(resultado.getAlertaAislamiento());
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
            List<Analizado> analizados, List<Telemetria> telemetriaDelRodeo, LocalDateTime ahora) {
        Map<Long, AlertaAnimalAislado> abiertos = alertaAnimalAisladoRepository
                .findByAnimal_Establecimiento_IdAndTsRegresoIsNull(est.getId()).stream()
                .collect(Collectors.toMap(e -> e.getAnimal().getId(), Function.identity(), (a, b) -> a));

        if (analizados.stream().anyMatch(a -> a.alejado() && a.resultado().getTiempoFueraContinuoS() == null)) {
            log.warn("ml-geo no devolvió tiempo_fuera_continuo_s: no se abren aislamientos hasta actualizar ml-geo.");
        }

        long minimoS = minutosFueraMinimo * 60;
        List<Analizado> nuevos = new ArrayList<>();
        for (Analizado analizado : analizados) {
            AlertaAnimalAislado abierto = abiertos.get(analizado.animal().getId());
            if (abierto != null) {
                if (analizado.alejado()) {
                    actualizar(abierto, analizado.resultado());
                } else {
                    abierto.setTsRegreso(ahora);
                }
                alertaAnimalAisladoRepository.save(abierto);
            } else if (analizado.alejado() && analizado.resultado().getTiempoFueraContinuoS() != null
                    && analizado.resultado().getTiempoFueraContinuoS() >= minimoS) {
                nuevos.add(analizado);
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
            sumarAAlertaPendiente(pendiente, nuevos, umbral, telemetriaDelRodeo, ahora);
            return;
        }

        List<AlertaAnimalAislado> episodios = nuevos.stream()
                .map(a -> nuevoEpisodio(a, ahora))
                .toList();
        Alerta alerta = new Alerta();
        alerta.setDisparador(DisparadorAlerta.AISLAMIENTO_SOCIAL);
        alerta.setTipo("AISLAMIENTO_SOCIAL");
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setTsGeneracion(ahora);
        alerta.setValorUmbral(umbral);
        alerta.setAccionRecomendada("Recorrer la zona y verificar el estado de los animales alejados del rodeo.");
        resumir(alerta, episodios, umbral, telemetriaDelRodeo);

        Alerta guardada = alertaRepository.save(alerta);
        for (AlertaAnimalAislado episodio : episodios) {
            episodio.setAlerta(guardada);
            alertaAnimalAisladoRepository.save(episodio);
        }
        log.info("Generada alerta de AISLAMIENTO_SOCIAL {} con {} animales", guardada.getId(), episodios.size());
        publicar(guardada, episodios);
    }

    /**
     * Suma los aislamientos nuevos a la alerta pendiente del rodeo. Si la alerta ya lista al animal
     * (volvió y se alejó otra vez), reabre su fila en vez de agregar otra. Re-publica la alerta con
     * el mismo id para que el frontend actualice la misma tarjeta.
     */
    private void sumarAAlertaPendiente(Alerta pendiente, List<Analizado> nuevos, double umbral,
            List<Telemetria> telemetriaDelRodeo, LocalDateTime ahora) {
        List<AlertaAnimalAislado> episodios = new ArrayList<>(
                alertaAnimalAisladoRepository.findByAlerta_IdOrderBySegundosFueraDesc(pendiente.getId()));
        for (Analizado nuevo : nuevos) {
            AlertaAnimalAislado episodio = episodios.stream()
                    .filter(e -> nuevo.animal().getId().equals(e.getAnimal().getId()))
                    .findFirst()
                    .orElse(null);
            if (episodio != null) {
                episodio.setTsRegreso(null);
                episodio.setTsInicio(inicioDe(nuevo.resultado(), ahora));
                actualizar(episodio, nuevo.resultado());
            } else {
                episodio = nuevoEpisodio(nuevo, ahora);
                episodio.setAlerta(pendiente);
                episodios.add(episodio);
            }
            alertaAnimalAisladoRepository.save(episodio);
        }

        resumir(pendiente, episodios, umbral, telemetriaDelRodeo);
        alertaRepository.save(pendiente);
        log.info("Alerta de AISLAMIENTO_SOCIAL {} actualizada: {} animales nuevos", pendiente.getId(), nuevos.size());
        publicar(pendiente, episodios);
    }

    private static AlertaAnimalAislado nuevoEpisodio(Analizado analizado, LocalDateTime ahora) {
        AlertaAnimalAislado episodio = AlertaAnimalAislado.builder()
                .animal(analizado.animal())
                .tsInicio(inicioDe(analizado.resultado(), ahora))
                .build();
        actualizar(episodio, analizado.resultado());
        return episodio;
    }

    /** Cuándo se alejó: el episodio se abre recién tras minutosFueraMinimo seguidos afuera. */
    private static LocalDateTime inicioDe(AlertaAnimalDTO resultado, LocalDateTime ahora) {
        return ahora.minusSeconds(segundos(resultado.getTiempoFueraContinuoS()));
    }

    private static void actualizar(AlertaAnimalAislado episodio, AlertaAnimalDTO resultado) {
        episodio.setSegundosFuera(segundos(resultado.getTiempoFueraGrupoS()));
        episodio.setDistanciaGrupoM(resultado.getDistanciaCentroideM());
    }

    /**
     * Animal principal (el que más tiempo lleva fuera) y su última posición, distancia máxima y
     * severidad: ALTA si algún animal está a más del doble del umbral.
     */
    private static void resumir(Alerta alerta, List<AlertaAnimalAislado> episodios, double umbral,
            List<Telemetria> telemetriaDelRodeo) {
        AlertaAnimalAislado principal = episodios.stream()
                .max(Comparator.comparing(AlertaAnimalAislado::getSegundosFuera))
                .orElseThrow();
        double distanciaMaxima = episodios.stream()
                .map(AlertaAnimalAislado::getDistanciaGrupoM)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(umbral);
        Telemetria ultima = telemetriaDelRodeo.stream()
                .filter(t -> principal.getAnimal().getId().equals(t.getCaravana().getAnimal().getId()))
                .max(Comparator.comparing(Telemetria::getFechaHora))
                .orElse(null);

        alerta.setAnimal(principal.getAnimal());
        alerta.setValorDetectado(distanciaMaxima);
        alerta.setSeveridad(distanciaMaxima > 2 * umbral ? SeveridadAlerta.ALTA : SeveridadAlerta.MEDIA);
        alerta.setLatitud(ultima != null ? ultima.getLatitud() : null);
        alerta.setLongitud(ultima != null ? ultima.getLongitud() : null);
    }

    /** Manda la lista de animales en el evento: las filas todavía no están commiteadas cuando el api lo consume. */
    private void publicar(Alerta alerta, List<AlertaAnimalAislado> episodios) {
        try {
            List<AnimalAisladoResponse> animalesAislados = episodios.stream()
                    .sorted(Comparator.comparing(AlertaAnimalAislado::getSegundosFuera).reversed())
                    .map(e -> AnimalAisladoResponse.from(e, alerta.getValorUmbral()))
                    .toList();
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
                    .animalesAislados(animalesAislados)
                    .build();
            rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", eventDTO);
        } catch (Exception e) {
            log.error("Error al emitir evento RabbitMQ para alerta {}: {}", alerta.getId(), e.getMessage());
        }
    }

    private static Long rodeoIdDe(Animal animal) {
        return animal != null && animal.getRodeoLote() != null ? animal.getRodeoLote().getId() : null;
    }

    private static long segundos(Double valor) {
        return valor != null ? valor.longValue() : 0L;
    }

    private static Long parsearAnimalId(String animalId) {
        try {
            return animalId != null ? Long.parseLong(animalId) : null;
        } catch (NumberFormatException e) {
            log.warn("ID de animal no numérico devuelto por ml-geo: {}", animalId);
            return null;
        }
    }

    private Establecimiento resolverEstablecimiento(List<Telemetria> telemetrias) {
        for (Telemetria t : telemetrias) {
            if (t.getCaravana().getEstablecimiento() != null) {
                return t.getCaravana().getEstablecimiento();
            }
            if (t.getCaravana().getAnimal().getEstablecimiento() != null) {
                return t.getCaravana().getAnimal().getEstablecimiento();
            }
        }
        return null;
    }

    private void marcarCompletados(List<Telemetria> telemetriaList) {
        List<Telemetria> pendientes = telemetriaList.stream()
                .filter(t -> t.getEstadoInferenciaGeo() == EstadoInferencia.PENDIENTE)
                .collect(Collectors.toList());
        if (!pendientes.isEmpty()) {
            pendientes.forEach(t -> t.setEstadoInferenciaGeo(EstadoInferencia.COMPLETADO));
            telemetriaRepository.saveAll(pendientes);
        }
    }

    /** ml-geo no respondió (o falló el procesamiento): se deja la telemetría para reintentar. */
    void marcarInferenciaPendiente(List<Telemetria> telemetriaList, String correlationId, Throwable t) {
        log.error("Fallo en el análisis geoespacial (Correlation-ID: {}). Marcando como inferencia pendiente. Motivo: {}",
                correlationId, t.getMessage());
        List<Telemetria> pendientes = telemetriaList.stream()
                .filter(t1 -> t1.getEstadoInferenciaGeo() == EstadoInferencia.PENDIENTE)
                .collect(Collectors.toList());
        if (!pendientes.isEmpty()) {
            pendientes.forEach(t1 -> t1.setEstadoInferenciaGeo(EstadoInferencia.INFERENCIA_PENDIENTE));
            telemetriaRepository.saveAll(pendientes);
        }
    }
}
