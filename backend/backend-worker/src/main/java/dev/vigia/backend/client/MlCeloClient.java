package dev.vigia.backend.client;

import dev.vigia.backend.dto.ml.CeloPredictResponseDTO;
import dev.vigia.backend.dto.ml.PuntoTelemetriaDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Cliente de ml-celo. Circuit breaker y reintentos envuelven solo la llamada
 * HTTP; si persiste el fallo lanza {@link MlServicioDegradadoException}.
 */
@Slf4j
@Component
public class MlCeloClient {

    private final RestClient restClient;

    @Autowired
    public MlCeloClient(@Value("${vigia.ml.celo-url}") String celoUrl, RestClient.Builder restClientBuilder) {
        this(MlClientes.construir(restClientBuilder, celoUrl));
    }

    /** Para tests: recibe un RestClient ya configurado (p. ej. atado a MockRestServiceServer). */
    public MlCeloClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @CircuitBreaker(name = "celoML", fallbackMethod = "fallbackPredict")
    @Retry(name = "celoML", fallbackMethod = "fallbackPredict")
    @SuppressWarnings("null")
    public CeloPredictResponseDTO predict(List<PuntoTelemetriaDTO> serie, String correlationId) {
        log.info("Llamando a microservicio de celo con correlationId: {}", correlationId);
        return restClient.post()
                .uri("/api/internal/ml/celo/predict")
                .header("X-Correlation-ID", correlationId)
                .body(serie)
                .retrieve()
                .body(CeloPredictResponseDTO.class);
    }

    public CeloPredictResponseDTO fallbackPredict(List<PuntoTelemetriaDTO> serie, String correlationId, Throwable t) {
        throw new MlServicioDegradadoException("ml-celo degradado o inalcanzable (Correlation-ID: " + correlationId + ")", t);
    }
}
