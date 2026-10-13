package dev.vigia.backend.client;

import dev.vigia.backend.dto.ml.PerfilCalibracionRequestDTO;
import dev.vigia.backend.dto.ml.PerfilCalibracionResponseDTO;
import dev.vigia.backend.dto.ml.SaludPredictRequestDTO;
import dev.vigia.backend.dto.ml.SaludPredictResponseDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente de ml-salud (cálculo de perfil de referencia y predicción). Circuit
 * breaker y reintentos envuelven solo cada llamada HTTP; si persiste el fallo
 * lanza {@link MlServicioDegradadoException}.
 */
@Slf4j
@Component
public class MlSaludClient {

    private final RestClient restClient;

    @Autowired
    public MlSaludClient(@Value("${vigia.ml.salud-url}") String saludUrl, RestClient.Builder restClientBuilder) {
        this(MlClientes.construir(restClientBuilder, saludUrl));
    }

    /** Para tests: recibe un RestClient ya configurado (p. ej. atado a MockRestServiceServer). */
    public MlSaludClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @CircuitBreaker(name = "saludML", fallbackMethod = "fallbackPredict")
    @Retry(name = "saludML", fallbackMethod = "fallbackPredict")
    @SuppressWarnings("null")
    public SaludPredictResponseDTO predict(SaludPredictRequestDTO request, String correlationId) {
        log.info("Llamando a microservicio de salud (predict) con correlationId: {}", correlationId);
        return restClient.post()
                .uri("/api/internal/ml/salud/predict")
                .header("X-Correlation-ID", correlationId)
                .body(request)
                .retrieve()
                .body(SaludPredictResponseDTO.class);
    }

    @CircuitBreaker(name = "saludML", fallbackMethod = "fallbackPerfil")
    @Retry(name = "saludML", fallbackMethod = "fallbackPerfil")
    @SuppressWarnings("null")
    public PerfilCalibracionResponseDTO calcularPerfil(PerfilCalibracionRequestDTO request, String correlationId) {
        return restClient.post()
                .uri("/api/internal/ml/salud/perfil")
                .header("X-Correlation-ID", correlationId)
                .body(request)
                .retrieve()
                .body(PerfilCalibracionResponseDTO.class);
    }

    public SaludPredictResponseDTO fallbackPredict(SaludPredictRequestDTO request, String correlationId, Throwable t) {
        throw new MlServicioDegradadoException("ml-salud degradado o inalcanzable (Correlation-ID: " + correlationId + ")", t);
    }

    public PerfilCalibracionResponseDTO fallbackPerfil(PerfilCalibracionRequestDTO request, String correlationId,
            Throwable t) {
        throw new MlServicioDegradadoException("ml-salud degradado o inalcanzable (Correlation-ID: " + correlationId + ")", t);
    }
}
