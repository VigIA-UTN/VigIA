package dev.vigia.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InvitacionProductorRequest {

    @NotNull(message = "El email de destino es obligatorio")
    @Email(message = "El formato del email no es válido")
    private String email;

    @NotNull(message = "El ID del establecimiento es obligatorio")
    private Long establecimientoId;
}
