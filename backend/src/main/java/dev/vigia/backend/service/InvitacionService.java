package dev.vigia.backend.service;

import dev.vigia.backend.dto.InvitacionEmpleadoRequest;
import dev.vigia.backend.dto.InvitacionProductorRequest;
import dev.vigia.backend.dto.InvitacionResponse;

import java.util.List;

public interface InvitacionService {
    InvitacionResponse generarInvitacionProductor(InvitacionProductorRequest request, String emailAdmin);

    InvitacionResponse generarInvitacionEmpleado(InvitacionEmpleadoRequest request, String emailProductor);

    List<InvitacionResponse> listarInvitacionesProductor();
}
