package dev.vigia.backend.client;

import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente de ml-geo para los batch del worker. Circuit breaker y reintentos
 * envuelven solo la llamada HTTP; si persiste el fallo (timeout, 5xx, circuito abierto) lanza
 * {@link MlServicioDegradadoException}.
 */
@Slf4j
@Component
public class MlGeoClient {

    private final RestClient restClient;

    @Autowired
    public MlGeoClient(@Value("${vigia.ml.geo-url}") String geoUrl, RestClient.Builder restClientBuilder) {
        this(MlClientes.construir(restClientBuilder, geoUrl));
    }

    /** Para tests: recibe un RestClient ya configurado (p. ej. atado a MockRestServiceServer). */
    public MlGeoClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @CircuitBreaker(name = "geoML", fallbackMethod = "fallbackAnalyze")
    @Retry(name = "geoML", fallbackMethod = "fallbackAnalyze")
    @SuppressWarnings("null")
    public GeoAnalyzeResponseDTO analyze(GeoAnalyzeRequestDTO request, String correlationId) {
        log.info("Llamando a ml-geo/analyze con {} puntos (Correlation-ID: {})",
                request.getHistorial7d() != null ? request.getHistorial7d().size() : 0, correlationId);

        return restClient.post()
                .uri("/api/internal/ml/geo/analyze")
                .header("X-Correlation-ID", correlationId)
                .body(request)
                .retrieve()
                .body(GeoAnalyzeResponseDTO.class);
    }

    public GeoAnalyzeResponseDTO fallbackAnalyze(GeoAnalyzeRequestDTO request, String correlationId, Throwable t) {
        throw MlClientes.errorDeServicio("ml-geo", correlationId, t);
    }
}
