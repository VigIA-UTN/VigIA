package dev.vigia.backend.service.ml;

import dev.vigia.backend.dto.ml.CeloPredictRequestDTO;
import dev.vigia.backend.dto.ml.CeloPredictResponseDTO;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.TelemetriaRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CeloDetectorStrategyImpl implements DetectorStrategy {

    private final RestClient restClient;
    private final TelemetriaRepository telemetriaRepository;

    @SuppressWarnings("null")
    public CeloDetectorStrategyImpl(
            RestClient.Builder restClientBuilder,
            @Value("${vigia.ml.celo-url}") String celoUrl,
            TelemetriaRepository telemetriaRepository) {
        this.restClient = restClientBuilder.baseUrl(celoUrl).build();
        this.telemetriaRepository = telemetriaRepository;
    }

    @Override
    @CircuitBreaker(name = "celoML", fallbackMethod = "fallbackProcess")
    @Retry(name = "celoML", fallbackMethod = "fallbackProcess")
    @SuppressWarnings("null")
    public void process(List<Telemetria> telemetriaList, String correlationId) {
        if (telemetriaList == null || telemetriaList.isEmpty()) {
            return;
        }

        CeloPredictRequestDTO requestDTO = buildRequest(telemetriaList);

        log.info("Llamando a microservicio de celo con correlationId: {}", correlationId);

        CeloPredictResponseDTO response = restClient.post()
                .uri("/api/internal/ml/celo/predict")
                .header("X-Correlation-ID", correlationId)
                .body(requestDTO)
                .retrieve()
                .body(CeloPredictResponseDTO.class);

        log.info("Respuesta de microservicio de celo recibida: {}", response);

        // Actualizamos estado a completado tras procesar
        telemetriaList.forEach(t -> t.setEstadoInferencia(EstadoInferencia.COMPLETADO));
        telemetriaRepository.saveAll(telemetriaList);

        // Aquí se crearía la alerta si eventoCelo es true
        if (response != null && response.isEventoCelo()) {
            log.info("¡Evento de celo detectado con confianza {}!", response.getConfianzaModelo());
            // TODO: Publicar en RabbitMQ el evento
        }
    }

    public void fallbackProcess(List<Telemetria> telemetriaList, String correlationId, Throwable t) {
        log.error(
                "Fallo al comunicarse con el microservicio ML de celo (Correlation-ID: {}). Marcando como inferencia pendiente. Motivo: {}",
                correlationId, t.getMessage());
        telemetriaList.forEach(telemetria -> telemetria.setEstadoInferencia(EstadoInferencia.INFERENCIA_PENDIENTE));
        telemetriaRepository.saveAll(telemetriaList);
    }

    private CeloPredictRequestDTO buildRequest(List<Telemetria> telemetriaList) {
        List<CeloPredictRequestDTO.VectorAceleracionDTO> series = telemetriaList.stream()
                .map(t -> CeloPredictRequestDTO.VectorAceleracionDTO.builder()
                        .x(t.getAccelX())
                        .y(t.getAccelY())
                        .z(t.getAccelZ())
                        .build())
                .collect(Collectors.toList());

        return CeloPredictRequestDTO.builder()
                .seriesTemporales(series)
                .build();
    }
}
