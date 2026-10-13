package dev.vigia.backend.client;

import dev.vigia.backend.dto.ml.PerfilCalibracionRequestDTO;
import dev.vigia.backend.dto.ml.SaludPredictRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los clientes ML levantados por Spring, con los aspectos reales de resilience4j:
 * con el microservicio inalcanzable, reintentan y terminan en
 * {@link MlServicioDegradadoException} (no en una excepción HTTP cruda).
 */
@SpringJUnitConfig({ MlCeloClient.class, MlSaludClient.class, MlGeoClient.class })
@ImportAutoConfiguration({ AopAutoConfiguration.class, JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class, RestClientAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class })
@TestPropertySource(properties = {
        // Puerto 1: conexión rechazada al instante.
        "vigia.ml.celo-url=http://localhost:1",
        "vigia.ml.salud-url=http://localhost:1",
        "vigia.ml.geo-url=http://localhost:1",
        "resilience4j.retry.configs.default.maxAttempts=2",
        "resilience4j.retry.configs.default.waitDuration=10ms"
})
@DisplayName("Clientes ML - resiliencia")
class MlClientesResilienciaTest {

    @Autowired
    private MlCeloClient mlCeloClient;

    @Autowired
    private MlSaludClient mlSaludClient;

    @Autowired
    private MlGeoClient mlGeoClient;

    @Test
    void celoInalcanzable_lanzaMlServicioDegradado() {
        assertThatThrownBy(() -> mlCeloClient.predict(List.of(), "corr-celo"))
                .isInstanceOf(MlServicioDegradadoException.class)
                .hasMessageContaining("ml-celo");
    }

    @Test
    void saludInalcanzable_lanzaMlServicioDegradadoEnPredictYPerfil() {
        assertThatThrownBy(() -> mlSaludClient.predict(SaludPredictRequestDTO.builder().build(), "corr-salud"))
                .isInstanceOf(MlServicioDegradadoException.class);
        assertThatThrownBy(() -> mlSaludClient.calcularPerfil(PerfilCalibracionRequestDTO.builder().build(), "corr-perfil"))
                .isInstanceOf(MlServicioDegradadoException.class);
    }

    @Test
    void geoInalcanzable_lanzaMlServicioDegradado() {
        assertThatThrownBy(() -> mlGeoClient.analyze(GeoAnalyzeRequestDTO.builder().historial7d(List.of()).build(), "corr-geo"))
                .isInstanceOf(MlServicioDegradadoException.class)
                .hasMessageContaining("ml-geo");
    }
}
