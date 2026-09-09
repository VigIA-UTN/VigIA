package dev.vigia.backend.exception;

public class PoligonoInvalidoException extends IllegalArgumentException {

    public PoligonoInvalidoException(String message) {
        super(message);
    }

    public PoligonoInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
