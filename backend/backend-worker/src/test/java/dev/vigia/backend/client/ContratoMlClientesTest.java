package dev.vigia.backend.client;

import dev.vigia.backend.deteccion.MlSimulado;
import dev.vigia.backend.dto.ml.CeloPredictResponseDTO;
import dev.vigia.backend.dto.ml.PerfilCalibracionRequestDTO;
import dev.vigia.backend.dto.ml.SaludPredictRequestDTO;
import dev.vigia.backend.dto.ml.SaludPredictResponseDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * INT02 - Contrato entre el backend-worker y los microservicios ml-celo, ml-salud y ml-geo, del lado de los clientes:
 * timeouts, reintento y {@code X-Correlation-ID}. Los clientes los levanta Spring con los aspectos reales de
 * resilience4j y la configuración del application.yaml de test, que es la misma que la de producción (timeout de
 * 2000 ms en celo y de 4000 ms en salud, un reintento solo ante 503, 504 o timeout). Los microservicios los simula
 * {@link MlSimulado}, que responde con la demora o el error que pide cada caso.
 * <p>
 * Lo que pasa con la telemetría cuando un microservicio falla (queda pendiente y se reprocesa) lo prueba
 * {@code dev.vigia.backend.deteccion.PipelineMlIT}, sobre la base real.
 * <p>
 * Los nombres de los tests llevan el caso de la hoja "Ciclo 1" del documento de Casos de Prueba (INT02-04 a INT02-11).
 */
@SpringJUnitConfig(classes = { MlCeloClient.class, MlSaludClient.class, MlGeoClient.class },
        initializers = ConfigDataApplicationContextInitializer.class)
@ImportAutoConfiguration({ AopAutoConfiguration.class, JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class, RestClientAutoConfiguration.class,
        CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class })
@DisplayName("INT02 - Contrato del worker con los microservicios ML: timeouts, reintento y Correlation-ID")
class ContratoMlClientesTest {

    private static final MlSimulado ML = new MlSimulado();

    private static final String CELO_OK = "{\"evento_celo\": true, \"confianza_modelo\": 0.91, \"ventana_optima_horas\": 12}";
    private static final String SALUD_OK =
            "{\"desvio_detectado\": true, \"severidad\": 2, \"posible_causa\": \"Inapetencia\", \"confianza_modelo\": 0.8}";
    private static final String ERROR = "{\"error\": \"Simulado\", \"mensaje\": \"falla simulada\"}";

    @Autowired
    private MlCeloClient mlCeloClient;

    @Autowired
    private MlSaludClient mlSaludClient;

    @Autowired
    private MlGeoClient mlGeoClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry registry) {
        registry.add("vigia.ml.celo-url", ML::url);
        registry.add("vigia.ml.salud-url", ML::url);
        registry.add("vigia.ml.geo-url", ML::url);
    }

    @BeforeEach
    void reiniciar() {
        ML.reiniciar();
        // Cada caso arranca con los circuitos cerrados: las fallas de un caso no pueden abrirlos en el siguiente.
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    // ── Timeouts ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("INT02-04 - ml-celo responde en 1500 ms - Respuesta mapeada, sin reintento ni timeout")
    void INT02_04_celoRespondeAntesDelTimeout() throws Exception {
        ML.responder(MlSimulado.CELO_PREDICT, 200, CELO_OK, 1500);

        Medicion<CeloPredictResponseDTO> m = medir(() -> mlCeloClient.predict(List.of(), "corr-int02-04"));

        assertThat(m.resultado().isEventoCelo()).isTrue();
        assertThat(m.resultado().getConfianzaModelo()).isEqualTo(0.91f);
        assertThat(m.resultado().getVentanaOptimaHoras()).isEqualTo(12);
        assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isEqualTo(1);
        assertThat(m.ms()).isBetween(1500L, 1999L);
    }

    @Test
    @DisplayName("INT02-05 - ml-celo tarda más de 2000 ms - Corta a los 2000 ms, reintenta una vez y aplica el fallback")
    void INT02_05_celoSuperaElTimeout() {
        ML.responder(MlSimulado.CELO_PREDICT, 200, CELO_OK, 2600);

        long ms = medirFalla(() -> mlCeloClient.predict(List.of(), "corr-int02-05"), MlServicioDegradadoException.class);

        assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isEqualTo(2);
        // Dos intentos de 2000 ms y 500 ms de espera entre ellos; nunca llega a esperar la respuesta de 2600 ms.
        assertThat(ms).isBetween(4500L, 5400L);
    }

    @Test
    @DisplayName("INT02-06 - ml-salud responde en 3500 ms - Respuesta mapeada, sin reintento ni timeout")
    void INT02_06_saludRespondeAntesDelTimeout() throws Exception {
        ML.responder(MlSimulado.SALUD_PREDICT, 200, SALUD_OK, 3500);

        Medicion<SaludPredictResponseDTO> m = medir(
                () -> mlSaludClient.predict(SaludPredictRequestDTO.builder().build(), "corr-int02-06"));

        assertThat(m.resultado().isDesvioDetectado()).isTrue();
        assertThat(m.resultado().getSeveridad()).isEqualTo(2);
        assertThat(m.resultado().getPosibleCausa()).isEqualTo("Inapetencia");
        assertThat(ML.llamadas(MlSimulado.SALUD_PREDICT)).isEqualTo(1);
        assertThat(m.ms()).isBetween(3500L, 3999L);
    }

    @Test
    @DisplayName("INT02-07 - ml-salud tarda más de 4000 ms - Corta a los 4000 ms, reintenta una vez y aplica el fallback")
    void INT02_07_saludSuperaElTimeout() {
        ML.responder(MlSimulado.SALUD_PREDICT, 200, SALUD_OK, 4600);

        long ms = medirFalla(() -> mlSaludClient.predict(SaludPredictRequestDTO.builder().build(), "corr-int02-07"),
                MlServicioDegradadoException.class);

        assertThat(ML.llamadas(MlSimulado.SALUD_PREDICT)).isEqualTo(2);
        // Dos intentos de 4000 ms y 500 ms de espera entre ellos.
        assertThat(ms).isBetween(8500L, 9400L);
    }

    // ── Reintento y fallback ─────────────────────────────────────────────────

    @Test
    @DisplayName("INT02-08 - ml-salud responde 503 - Reintenta una vez: si se recupera usa la respuesta, si no aplica el fallback")
    void INT02_08_503ReintentaUnaVez() {
        ML.responderUnaVez(MlSimulado.SALUD_PREDICT, 503, ERROR);
        ML.responder(MlSimulado.SALUD_PREDICT, SALUD_OK);

        SaludPredictResponseDTO recuperada = mlSaludClient.predict(SaludPredictRequestDTO.builder().build(),
                "corr-int02-08a");

        assertThat(recuperada.isDesvioDetectado()).isTrue();
        assertThat(ML.llamadas(MlSimulado.SALUD_PREDICT)).isEqualTo(2);

        ML.reiniciar();
        ML.responder(MlSimulado.SALUD_PREDICT, 503, ERROR, 0);

        assertThatThrownBy(() -> mlSaludClient.predict(SaludPredictRequestDTO.builder().build(), "corr-int02-08b"))
                .isInstanceOf(MlServicioDegradadoException.class)
                .hasMessageContaining("ml-salud")
                .hasMessageContaining("corr-int02-08b");
        assertThat(ML.llamadas(MlSimulado.SALUD_PREDICT)).isEqualTo(2);
    }

    @Test
    @DisplayName("INT02-09 - ml-geo responde 504 - Reintenta una vez y aplica el fallback")
    void INT02_09_504ReintentaYAplicaFallback() {
        ML.responder(MlSimulado.GEO_ANALYZE, 504, ERROR, 0);

        assertThatThrownBy(() -> mlGeoClient.analyze(GeoAnalyzeRequestDTO.builder().historial7d(List.of()).build(),
                "corr-int02-09"))
                .isInstanceOf(MlServicioDegradadoException.class)
                .hasMessageContaining("ml-geo");
        assertThat(ML.llamadas(MlSimulado.GEO_ANALYZE)).isEqualTo(2);
    }

    @Test
    @DisplayName("INT02-10 - Error 500 o 422 - No se reintenta: el 500 va al fallback y el 422 se propaga")
    void INT02_10_500y422NoSeReintentan() {
        ML.responder(MlSimulado.CELO_PREDICT, 500, ERROR, 0);

        assertThatThrownBy(() -> mlCeloClient.predict(List.of(), "corr-int02-10a"))
                .isInstanceOf(MlServicioDegradadoException.class);
        assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isEqualTo(1);

        ML.responder(MlSimulado.SALUD_PERFIL, 422, ERROR, 0);

        assertThatThrownBy(() -> mlSaludClient.calcularPerfil(PerfilCalibracionRequestDTO.builder().build(),
                "corr-int02-10b"))
                .isInstanceOf(HttpClientErrorException.UnprocessableEntity.class);
        assertThat(ML.llamadas(MlSimulado.SALUD_PERFIL)).isEqualTo(1);
    }

    // ── Correlation ID ───────────────────────────────────────────────────────

    @Test
    @DisplayName("INT02-11 - X-Correlation-ID - Toda llamada lo lleva, también el reintento, con el mismo valor")
    void INT02_11_todaLlamadaLlevaElCorrelationId() {
        ML.responderUnaVez(MlSimulado.CELO_PREDICT, 503, ERROR);
        ML.responder(MlSimulado.CELO_PREDICT, CELO_OK);
        ML.responder(MlSimulado.SALUD_PREDICT, SALUD_OK);
        ML.responder(MlSimulado.SALUD_PERFIL, "{\"perfil\": {\"n_dias_calibracion\": 7}}");
        ML.responder(MlSimulado.GEO_ANALYZE, "{\"mapa_calor\": [], \"alertas\": [], \"animales_alejados\": []}");

        mlCeloClient.predict(List.of(), "corr-celo");
        mlSaludClient.predict(SaludPredictRequestDTO.builder().build(), "corr-salud");
        mlSaludClient.calcularPerfil(PerfilCalibracionRequestDTO.builder().build(), "corr-perfil");
        mlGeoClient.analyze(GeoAnalyzeRequestDTO.builder().historial7d(List.of()).build(), "corr-geo");

        // El 503 y su reintento: las dos llamadas con el mismo ID, para seguirlas juntas en los logs.
        assertThat(ML.correlationIds(MlSimulado.CELO_PREDICT)).containsExactly("corr-celo", "corr-celo");
        assertThat(ML.correlationIds(MlSimulado.SALUD_PREDICT)).containsExactly("corr-salud");
        assertThat(ML.correlationIds(MlSimulado.SALUD_PERFIL)).containsExactly("corr-perfil");
        assertThat(ML.correlationIds(MlSimulado.GEO_ANALYZE)).containsExactly("corr-geo");
    }

    // ── Medición ─────────────────────────────────────────────────────────────

    private record Medicion<T>(T resultado, long ms) {
    }

    private static <T> Medicion<T> medir(Callable<T> llamada) throws Exception {
        long inicio = System.nanoTime();
        T resultado = llamada.call();
        return new Medicion<>(resultado, (System.nanoTime() - inicio) / 1_000_000);
    }

    private static long medirFalla(Runnable llamada, Class<? extends Throwable> esperada) {
        long inicio = System.nanoTime();
        assertThatThrownBy(llamada::run).isInstanceOf(esperada);
        return (System.nanoTime() - inicio) / 1_000_000;
    }
}
