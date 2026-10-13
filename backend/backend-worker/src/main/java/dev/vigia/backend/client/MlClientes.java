package dev.vigia.backend.client;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
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
}
