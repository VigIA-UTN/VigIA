package dev.vigia.backend.service;

public interface EmailService {
    void enviarInvitacionProductor(String to, String token, String establecimientoNombre);
}
