package dev.vigia.backend.client;

/**
 * Un microservicio de ML no respondió (timeout, error 5xx o circuito abierto)
 * aun después de los reintentos. La lanzan los fallbacks de los clientes ML del
 * worker para que cada batch decida cómo degradar.
 */
public class MlServicioDegradadoException extends RuntimeException {
    public MlServicioDegradadoException(String message, Throwable cause) {
        super(message, cause);
    }
}
