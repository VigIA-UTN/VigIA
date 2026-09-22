package dev.vigia.backend.client;

import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.exception.MlGeoDegradadoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class MlGeoClient {

    private final RestClient restClient;

    @SuppressWarnings("null")
    public MlGeoClient(@Value("${vigia.ml.geo-url:${ML_GEO_URL:http://localhost:8003}}") String geoUrl,
            RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(geoUrl).build();
    }

    @SuppressWarnings("null")
    public GeoAnalyzeResponseDTO analyze(GeoAnalyzeRequestDTO request) {
        log.info("Llamando a ml-geo/analyze");
        try {
            return restClient.post()
                    .uri("/api/internal/ml/geo/analyze")
                    .body(request)
                    .retrieve()
                    .body(GeoAnalyzeResponseDTO.class);
        } catch (RestClientException e) {
            log.warn("Fallo al comunicarse con ml-geo: {}", e.getMessage());
            throw new MlGeoDegradadoException("Microservicio ml-geo degradado o inalcanzable", e);
        }
    }
}
