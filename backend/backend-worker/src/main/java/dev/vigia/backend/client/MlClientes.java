package dev.vigia.backend.client;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Configuración HTTP común a los clientes de los microservicios de ML del worker. */
final class MlClientes {

    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(2);
    private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(4);

    private MlClientes() {
    }

    /** RestClient con timeouts explícitos: sin ellos un microservicio colgado bloquea el scheduler. */
    @SuppressWarnings("null")
    static RestClient construir(RestClient.Builder builder, String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(TIMEOUT_CONEXION)
                                .withReadTimeout(TIMEOUT_LECTURA)))
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
