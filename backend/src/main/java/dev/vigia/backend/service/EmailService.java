package dev.vigia.backend.service;

public interface EmailService {
    void enviarInvitacionProductor(String to, String token, String establecimientoNombre);
    void enviarInvitacionEmpleado(String to, String token, String establecimientoNombre, String rol);
}
