package dev.vigia.backend.client;

import com.sun.net.httpserver.HttpServer;
import dev.vigia.backend.dto.ml.PerfilCalibracionRequestDTO;
import dev.vigia.backend.dto.ml.SaludPredictRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los clientes ML levantados por Spring, con los aspectos reales de resilience4j y
 * la configuración de reintentos/circuit breaker del application.yaml de test.
 */
@SpringJUnitConfig(classes = { MlCeloClient.class, MlSaludClient.class, MlGeoClient.class },
        initializers = ConfigDataApplicationContextInitializer.class)
@ImportAutoConfiguration({ AopAutoConfiguration.class, JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class, RestClientAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class })
@DisplayName("Clientes ML - resiliencia")
class MlClientesResilienciaTest {

    /** ml-salud simulado: rechaza todo con 422, como cuando la calibración es insuficiente. */
    private static final HttpServer ML_SALUD_422 = iniciarServidor422();
    private static final AtomicInteger REQUESTS_SALUD = new AtomicInteger();

    @Autowired
    private MlCeloClient mlCeloClient;

    @Autowired
    private MlSaludClient mlSaludClient;

    @Autowired
    private MlGeoClient mlGeoClient;

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry registry) {
        // Puerto 1: conexión rechazada al instante.
        registry.add("vigia.ml.celo-url", () -> "http://localhost:1");
        registry.add("vigia.ml.geo-url", () -> "http://localhost:1");
        registry.add("vigia.ml.salud-url", () -> "http://localhost:" + ML_SALUD_422.getAddress().getPort());
    }

    private static HttpServer iniciarServidor422() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                REQUESTS_SALUD.incrementAndGet();
                byte[] body = "{\"detail\":\"Calibración insuficiente\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(HttpStatus.UNPROCESSABLE_ENTITY.value(), body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void detenerServidor() {
        ML_SALUD_422.stop(0);
    }

    @BeforeEach
    void reiniciarContador() {
        REQUESTS_SALUD.set(0);
    }

    @Test
    @DisplayName("Microservicio inalcanzable: reintenta y termina en MlServicioDegradadoException con la causa")
    void inalcanzable_lanzaMlServicioDegradadoConCausa() {
        assertThatThrownBy(() -> mlCeloClient.predict(List.of(), "corr-celo"))
                .isInstanceOf(MlServicioDegradadoException.class)
                .hasMessageContaining("ml-celo")
                .hasMessageContaining("Connection refused");
        assertThatThrownBy(() -> mlGeoClient.analyze(GeoAnalyzeRequestDTO.builder().historial7d(List.of()).build(), "corr-geo"))
                .isInstanceOf(MlServicioDegradadoException.class)
                .hasMessageContaining("ml-geo");
    }

    @Test
    @DisplayName("4xx (ej. 422 por datos insuficientes): se propaga tal cual y no se reintenta")
    void error4xx_sePropagaSinReintentar() {
        assertThatThrownBy(() -> mlSaludClient.calcularPerfil(PerfilCalibracionRequestDTO.builder().build(), "corr-perfil"))
                .isInstanceOf(HttpClientErrorException.UnprocessableEntity.class)
                .hasMessageContaining("Calibración insuficiente");
        assertThat(REQUESTS_SALUD.get()).isEqualTo(1);

        assertThatThrownBy(() -> mlSaludClient.predict(SaludPredictRequestDTO.builder().build(), "corr-salud"))
                .isInstanceOf(HttpClientErrorException.class);
        assertThat(REQUESTS_SALUD.get()).isEqualTo(2);
    }
}
