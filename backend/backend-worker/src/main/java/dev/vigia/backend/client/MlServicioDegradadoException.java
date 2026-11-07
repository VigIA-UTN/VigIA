package dev.vigia.backend.client;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Un microservicio de ML no respondió (timeout, error 5xx o circuito abierto)
 * aun después de los reintentos. La lanzan los fallbacks de los clientes ML del
 * worker para que cada batch decida cómo degradar.
 */
public class MlServicioDegradadoException extends RuntimeException {
    public MlServicioDegradadoException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Si la falla es del microservicio (caído, lento o con error interno) y no de los datos. En ese caso la
     * telemetría queda pendiente y el próximo ciclo del batch la vuelve a evaluar. Además de esta excepción acepta
     * el 5xx y el error de red sin envolver, que es como llegan cuando el cliente se usa sin los aspectos de
     * resilience4j.
     */
    public static boolean esDegradacion(Throwable t) {
        return t instanceof MlServicioDegradadoException
                || t instanceof HttpServerErrorException
                || t instanceof ResourceAccessException;
    }
}
