package dev.vigia.backend.service.ml;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.dto.ml.geo.AlertaAnimalDTO;
import dev.vigia.backend.dto.ml.geo.CoordenadaGPSDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GeoDetectorStrategyImpl implements DetectorStrategy {

    private final RestClient restClient;
    private final TelemetriaRepository telemetriaRepository;
    private final AlertaRepository alertaRepository;
    private final AnimalRepository animalRepository;
    private final RabbitTemplate rabbitTemplate;
    private final int cooldownHours;

    @SuppressWarnings("null")
    public GeoDetectorStrategyImpl(
            RestClient.Builder restClientBuilder,
            @Value("${vigia.ml.geo-url}") String geoUrl,
            TelemetriaRepository telemetriaRepository,
            AlertaRepository alertaRepository,
            AnimalRepository animalRepository,
            RabbitTemplate rabbitTemplate,
            @Value("${vigia.geofence.cooldown-hours:6}") int cooldownHours) {
        this.restClient = restClientBuilder.baseUrl(geoUrl).build();
        this.telemetriaRepository = telemetriaRepository;
        this.alertaRepository = alertaRepository;
        this.animalRepository = animalRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.cooldownHours = cooldownHours;
    }

    @Override
    @CircuitBreaker(name = "geoML", fallbackMethod = "fallbackProcess")
    @Retry(name = "geoML", fallbackMethod = "fallbackProcess")
    @SuppressWarnings("null")
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

        Double umbralAislamientoM = resolverUmbralAislamiento(telemetriasValidas);

        List<CoordenadaGPSDTO> coordenadas = telemetriasValidas.stream()
                .map(t -> CoordenadaGPSDTO.builder()
                        .animalId(t.getCaravana().getAnimal().getId().toString())
                        .lat(t.getLatitud())
                        .lng(t.getLongitud())
                        .timestamp(t.getFechaHora().toString())
                        .build())
                .collect(Collectors.toList());

        GeoAnalyzeRequestDTO request = GeoAnalyzeRequestDTO.builder()
                .historial7d(coordenadas)
                .umbralAislamientoM(umbralAislamientoM)
                .build();

        log.info("Llamando a microservicio de geo (analyze) con {} puntos GPS y correlationId: {}",
                coordenadas.size(), correlationId);

        GeoAnalyzeResponseDTO response = restClient.post()
                .uri("/api/internal/ml/geo/analyze")
                .header("X-Correlation-ID", correlationId)
                .body(request)
                .retrieve()
                .body(GeoAnalyzeResponseDTO.class);

        log.info("Respuesta de microservicio de geo recibida con {} alertas (correlationId: {})",
                response != null && response.getAlertas() != null ? response.getAlertas().size() : 0, correlationId);

        marcarCompletados(telemetriaList);

        if (response != null && response.getAlertas() != null) {
            for (AlertaAnimalDTO alertaAnimal : response.getAlertas()) {
                if (Boolean.TRUE.equals(alertaAnimal.getAlertaAislamiento())) {
                    procesarAlertaAislamiento(alertaAnimal, telemetriasValidas, umbralAislamientoM);
                }
            }
        }
    }

    private Double resolverUmbralAislamiento(List<Telemetria> telemetrias) {
        for (Telemetria t : telemetrias) {
            if (t.getCaravana() != null && t.getCaravana().getEstablecimiento() != null
                    && t.getCaravana().getEstablecimiento().getUmbralAislamientoM() != null) {
                return t.getCaravana().getEstablecimiento().getUmbralAislamientoM();
            }
            if (t.getCaravana() != null && t.getCaravana().getAnimal() != null
                    && t.getCaravana().getAnimal().getEstablecimiento() != null
                    && t.getCaravana().getAnimal().getEstablecimiento().getUmbralAislamientoM() != null) {
                return t.getCaravana().getAnimal().getEstablecimiento().getUmbralAislamientoM();
            }
        }
        return 100.0;
    }

    @SuppressWarnings("null")
    private void procesarAlertaAislamiento(AlertaAnimalDTO alertaAnimal, List<Telemetria> telemetriasValidas,
            Double umbralEfectivo) {
        Long animalId;
        try {
            animalId = Long.parseLong(alertaAnimal.getAnimalId());
        } catch (NumberFormatException e) {
            log.warn("ID de animal no numérico devuelto por ml-geo: {}", alertaAnimal.getAnimalId());
            return;
        }

        Optional<Animal> animalOpt = animalRepository.findById(animalId);
        if (animalOpt.isEmpty()) {
            log.warn("Animal con ID {} no encontrado al procesar alerta de aislamiento.", animalId);
            return;
        }
        Animal animal = animalOpt.get();

        Telemetria ultima = telemetriasValidas.stream()
                .filter(t -> t.getCaravana().getAnimal().getId().equals(animalId))
                .max(Comparator.comparing(Telemetria::getFechaHora))
                .orElse(null);

        Double latitud = ultima != null ? ultima.getLatitud() : null;
        Double longitud = ultima != null ? ultima.getLongitud() : null;
        LocalDateTime tsAlerta = ultima != null ? ultima.getFechaHora() : LocalDateTime.now();

        LocalDateTime limiteCooldown = LocalDateTime.now().minusHours(cooldownHours);
        Optional<Alerta> alertaReciente = alertaRepository
                .findFirstByAnimalAndDisparadorAndTsGeneracionAfterOrderByTsGeneracionDesc(
                        animal, DisparadorAlerta.ABIGEATO, limiteCooldown);

        if (alertaReciente.isPresent()) {
            Alerta activa = alertaReciente.get();
            if (activa.getEstado() == EstadoAlerta.PENDIENTE) {
                activa.setLatitud(latitud);
                activa.setLongitud(longitud);
                activa.setValorDetectado(alertaAnimal.getDistanciaCentroideM());
                if (alertaAnimal.getIndiceCohesion() != null) {
                    activa.setConfianzaModelo(alertaAnimal.getIndiceCohesion().floatValue());
                }
                alertaRepository.save(activa);
                log.info("Animal {} sigue aislado del rodeo. Se actualiza posición de la alerta {}.",
                        animal.getId(), activa.getId());
            } else {
                log.info(
                        "Animal {} está aislado pero ya posee una alerta reciente de abigeato/aislamiento (últimas {}hs). Se omite duplicado.",
                        animal.getId(), cooldownHours);
            }
            return;
        }

        log.warn("¡ALERTA! Animal {} (Caravana: {}) aislado del rodeo (distancia al centroide: {} m, cohesión: {})",
                animal.getId(),
                animal.getCaravana() != null ? animal.getCaravana().getCodigo() : "N/A",
                alertaAnimal.getDistanciaCentroideM(),
                alertaAnimal.getIndiceCohesion());

        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setTipo("Aislamiento de Rodeo");
        alerta.setSeveridad(SeveridadAlerta.ALTA);
        alerta.setTsGeneracion(tsAlerta);
        alerta.setLatitud(latitud);
        alerta.setLongitud(longitud);
        alerta.setDisparador(DisparadorAlerta.ABIGEATO);
        alerta.setValorUmbral(umbralEfectivo != null ? umbralEfectivo : 100.0);
        alerta.setValorDetectado(alertaAnimal.getDistanciaCentroideM());
        alerta.setConfianzaModelo(alertaAnimal.getIndiceCohesion() != null
                ? alertaAnimal.getIndiceCohesion().floatValue()
                : null);
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setPosibleCausa("Animal aislado del rodeo a "
                + Math.round(alertaAnimal.getDistanciaCentroideM() != null ? alertaAnimal.getDistanciaCentroideM() : 0.0)
                + " metros del centroide del grupo");
        alerta.setAccionRecomendada("Verificar ubicación del animal en el potrero para descartar extravío o abigeato.");

        alerta = alertaRepository.save(alerta);

        AlertaEventDTO eventDTO = AlertaEventDTO.builder()
                .id(alerta.getId())
                .animalId(animal.getId())
                .tipo(alerta.getTipo())
                .severidad(alerta.getSeveridad())
                .tsGeneracion(alerta.getTsGeneracion())
                .disparador(alerta.getDisparador())
                .valorUmbral(alerta.getValorUmbral())
                .valorDetectado(alerta.getValorDetectado())
                .estado(alerta.getEstado())
                .accionRecomendada(alerta.getAccionRecomendada())
                .posibleCausa(alerta.getPosibleCausa())
                .latitud(alerta.getLatitud())
                .longitud(alerta.getLongitud())
                .confianzaModelo(alerta.getConfianzaModelo())
                .build();

        rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", eventDTO);
        log.info("Alerta de abigeato/aislamiento publicada en RabbitMQ con ID: {} para animal {}",
                alerta.getId(), animal.getId());
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

    public void fallbackProcess(List<Telemetria> telemetriaList, String correlationId, Throwable t) {
        log.error(
                "Fallo al comunicarse con el microservicio ML de geo (Correlation-ID: {}). Marcando como inferencia pendiente. Motivo: {}",
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
