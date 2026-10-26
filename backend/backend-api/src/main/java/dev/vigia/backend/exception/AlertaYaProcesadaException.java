package dev.vigia.backend.exception;

/**
 * La alerta ya fue confirmada, descartada o archivada por otro usuario → 409 Conflict.
 */
public class AlertaYaProcesadaException extends RuntimeException {
    public AlertaYaProcesadaException(String message) {
        super(message);
    }
}
