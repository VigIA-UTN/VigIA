package dev.vigia.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class InvitacionResponse {
    private Long id;
    private String nombreCompleto;
    private String emailDestino;
    private String rol;
    private String establecimiento;
    private String token;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaExpiracion;
    private String estado;
}
