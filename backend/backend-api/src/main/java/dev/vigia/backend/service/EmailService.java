package dev.vigia.backend.service;

import dev.vigia.backend.model.Alerta;

public interface EmailService {
    void enviarInvitacionProductor(String to, String token, String establecimientoNombre);

    void enviarInvitacionEmpleado(String to, String token, String establecimientoNombre, String rol);

    void enviarNuevoEstablecimientoProductor(String to, String establecimientoNombre);

    void enviarInvitacionPendienteUsuarioExistente(String to, String establecimientoNombre, String rol);

    void enviarAlerta(String to, String establecimientoNombre, Alerta alerta);
}
