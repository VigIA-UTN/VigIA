package dev.vigia.backend.client;

import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.exception.MlGeoDegradadoException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class MlGeoClient {

        private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(4);
        private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(2);

        private final RestClient restClient;

        @SuppressWarnings("null")
        public MlGeoClient(@Value("${vigia.ml.geo-url:${ML_GEO_URL:http://localhost:8003}}") String geoUrl,
                        RestClient.Builder restClientBuilder) {
                this.restClient = restClientBuilder
                                .baseUrl(geoUrl)
                                .requestFactory(ClientHttpRequestFactories.get(
                                                ClientHttpRequestFactorySettings.DEFAULTS
                                                                .withConnectTimeout(TIMEOUT_CONEXION)
                                                                .withReadTimeout(TIMEOUT_LECTURA)))
                                .build();
        }

        /**
         * Llama al análisis geoespacial. Los fallos de comunicación se reintentan y, si
         * persisten, el fallback los traduce a {@link MlGeoDegradadoException} para que
         * el
         * controller degrade a 206 en vez de romper la pantalla del mapa.
         */
        @CircuitBreaker(name = "geoML", fallbackMethod = "fallbackAnalyze")
        @Retry(name = "geoML", fallbackMethod = "fallbackAnalyze")
        @SuppressWarnings("null")
        public GeoAnalyzeResponseDTO analyze(GeoAnalyzeRequestDTO request) {
                String correlationId = UUID.randomUUID().toString();
                log.info("Llamando a ml-geo/analyze con {} puntos (Correlation-ID: {})",
                                request.getHistorial7d() != null ? request.getHistorial7d().size() : 0, correlationId);

                return restClient.post()
                                .uri("/api/internal/ml/geo/analyze")
                                .header("X-Correlation-ID", correlationId)
                                .body(request)
                                .retrieve()
                                .body(GeoAnalyzeResponseDTO.class);
        }

        public GeoAnalyzeResponseDTO fallbackAnalyze(GeoAnalyzeRequestDTO request, Throwable t) {
                log.warn("Fallo al comunicarse con ml-geo: {}", t.getMessage());
                throw new MlGeoDegradadoException("Microservicio ml-geo degradado o inalcanzable", t);
        }
}
