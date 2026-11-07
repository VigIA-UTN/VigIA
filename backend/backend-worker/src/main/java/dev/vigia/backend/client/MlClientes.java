package dev.vigia.backend.client;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configuración HTTP común a los clientes de los microservicios de ML del worker.
 * <p>
 * En cada llamada, resilience4j aplica el reintento por fuera del circuit breaker: Retry(CircuitBreaker(llamada)).
 * Por eso el fallback va solo en {@code @Retry}: si también estuviera en {@code @CircuitBreaker}, el Retry recibiría
 * la excepción ya envuelta y no podría elegir qué reintentar. Se reintenta una vez ante 503, 504, timeout o
 * servicio inalcanzable (ver {@link FallaTransitoriaMl}); un 500, un 4xx o el circuito abierto van directo al
 * fallback.
 */
final class MlClientes {

    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(2);

    private MlClientes() {
    }

    /**
     * RestClient con timeouts explícitos: sin ellos un microservicio colgado bloquea el scheduler. El de lectura
     * es propio de cada microservicio (vigia.ml.*-timeout-ms).
     */
    @SuppressWarnings("null")
    static RestClient construir(RestClient.Builder builder, String baseUrl, long timeoutLecturaMs) {
        return builder
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(TIMEOUT_CONEXION)
                                .withReadTimeout(Duration.ofMillis(timeoutLecturaMs))))
                .build();
    }

    /**
     * Excepción a lanzar desde los fallbacks. Un 4xx no es una caída: el microservicio
     * respondió y rechazó el request (p. ej. 422 por datos insuficientes), así que se
     * propaga tal cual para que el log muestre el motivo real. Lo demás (timeouts,
     * 5xx, circuito abierto) se informa como servicio degradado, con la causa.
     */
    static RuntimeException errorDeServicio(String servicio, String correlationId, Throwable t) {
        if (t instanceof HttpClientErrorException clientError) {
            return clientError;
        }
        return new MlServicioDegradadoException(
                servicio + " degradado o inalcanzable (Correlation-ID: " + correlationId + "): " + t.getMessage(), t);
    }
}
