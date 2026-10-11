package dev.vigia.backend.config;

import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import lombok.Builder;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class CohesionBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EstablecimientoRepository establecimientoRepository;
    private final TelemetriaRepository telemetriaRepository;
    private final AlertaRepository alertaRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    public CohesionBatchConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            EstablecimientoRepository establecimientoRepository, TelemetriaRepository telemetriaRepository,
            AlertaRepository alertaRepository, RabbitTemplate rabbitTemplate) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.establecimientoRepository = establecimientoRepository;
        this.telemetriaRepository = telemetriaRepository;
        this.alertaRepository = alertaRepository;
        this.rabbitTemplate = rabbitTemplate;
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
    public ItemProcessor<Establecimiento, Establecimiento> cohesionProcessor() {
        return establecimiento -> {
            LocalDateTime hace24h = LocalDateTime.now().minusHours(24);
            var telemetriaList = telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(
                    establecimiento.getId(), hace24h, LocalDateTime.now(), null);

            if (telemetriaList.isEmpty()) {
                return establecimiento;
            }

            List<Map<String, Object>> coordenadas = telemetriaList.stream()
                    .filter(t -> t.getLatitud() != null && t.getLongitud() != null)
                    .filter(t -> t.getCaravana().getAnimal() != null)
                    .map(t -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("animal_id", t.getCaravana().getAnimal().getId().toString());
                        m.put("lat", t.getLatitud());
                        m.put("lng", t.getLongitud());
                        m.put("timestamp", t.getFechaHora().toString());
                        return m;
                    })
                    .collect(Collectors.toList());

            double umbral = establecimiento.getUmbralAislamientoM() != null ? establecimiento.getUmbralAislamientoM() : 100.0;
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
                        for (GeoAlerta ga : alertas) {
                            if (Boolean.TRUE.equals(ga.getAlertaAislamiento())) {
                                Long animalId = Long.parseLong(ga.getAnimalId());
                                Double distancia = ga.getDistanciaCentroideM();
                                
                                // Buscar última posición de este animal
                                var ultimaTelemetria = telemetriaList.stream()
                                        .filter(t -> t.getCaravana().getAnimal() != null && animalId.equals(t.getCaravana().getAnimal().getId()))
                                        .reduce((first, second) -> second)
                                        .orElse(null);

                                Double lat = ultimaTelemetria != null ? ultimaTelemetria.getLatitud() : null;
                                Double lng = ultimaTelemetria != null ? ultimaTelemetria.getLongitud() : null;

                                generarAlertaSiNoExiste(animalId, establecimiento, distancia, umbral, lat, lng);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Fallo al llamar a ml-geo para cohesion: {}", e.getMessage());
            }

            return establecimiento;
        };
    }

    private void generarAlertaSiNoExiste(Long animalId, Establecimiento est, Double distancia, double umbral, Double lat, Double lng) {
        boolean existe = alertaRepository.alertasAnimalActivas(animalId).stream()
                .anyMatch(al -> al.getDisparador() == DisparadorAlerta.AISLAMIENTO_SOCIAL);
        
        if (!existe) {
            dev.vigia.backend.model.Animal a = new dev.vigia.backend.model.Animal();
            a.setId(animalId);

            Alerta alerta = new Alerta();
            alerta.setDisparador(DisparadorAlerta.AISLAMIENTO_SOCIAL);
            alerta.setTipo("AISLAMIENTO_SOCIAL");
            alerta.setEstado(EstadoAlerta.PENDIENTE);
            alerta.setSeveridad(SeveridadAlerta.ALTA);
            alerta.setTsGeneracion(LocalDateTime.now());
            alerta.setAnimal(a);
            alerta.setValorDetectado(distancia != null ? distancia : umbral);
            alerta.setValorUmbral(umbral);
            alerta.setLatitud(lat);
            alerta.setLongitud(lng);
            alerta.setAccionRecomendada("Recorrer la zona y verificar el estado de los animales alejados del rodeo.");

            Alerta guardada = alertaRepository.save(alerta);
            log.info("Generada nueva alerta de AISLAMIENTO_SOCIAL para animal {}", animalId);

            try {
                AlertaEventDTO eventDTO = AlertaEventDTO.builder()
                        .id(guardada.getId())
                        .animalId(animalId)
                        .tipo(guardada.getTipo())
                        .severidad(guardada.getSeveridad())
                        .tsGeneracion(guardada.getTsGeneracion())
                        .disparador(guardada.getDisparador())
                        .valorUmbral(guardada.getValorUmbral())
                        .valorDetectado(guardada.getValorDetectado())
                        .estado(guardada.getEstado())
                        .accionRecomendada(guardada.getAccionRecomendada())
                        .latitud(lat)
                        .longitud(lng)
                        .build();

                rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", eventDTO);
                log.info("Emitido evento RabbitMQ para alerta de AISLAMIENTO_SOCIAL ID: {}", guardada.getId());
            } catch (Exception e) {
                log.error("Error al emitir evento RabbitMQ para alerta {}: {}", guardada.getId(), e.getMessage());
            }
        }
    }

    @Bean
    public ItemWriter<Establecimiento> cohesionWriter() {
        return items -> {};
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

        @JsonProperty("alerta_aislamiento")
        private Boolean alertaAislamiento;

        @JsonProperty("distancia_centroide_m")
        private Double distanciaCentroideM;
    }
}
