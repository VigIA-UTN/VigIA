package dev.vigia.backend.service;

import dev.vigia.backend.dto.InvitacionEmpleadoRequest;
import dev.vigia.backend.dto.InvitacionProductorRequest;
import dev.vigia.backend.dto.InvitacionResponse;

import java.util.List;

public interface InvitacionService {
    InvitacionResponse generarInvitacionProductor(InvitacionProductorRequest request, String emailAdmin);

    InvitacionResponse generarInvitacionEmpleado(InvitacionEmpleadoRequest request, String emailProductor);

    List<InvitacionResponse> listarInvitacionesProductor();

    List<InvitacionResponse> listarInvitacionesEmpleado(String emailProductor, Long establecimientoId);

    List<InvitacionResponse> listarMisInvitaciones(String email);

    void aceptarInvitacion(Long id, String email);

    void aceptarInvitacionPorToken(String token, String email);

    void rechazarInvitacion(Long id, String email);

    void cancelarInvitacion(Long id, String email);
}
