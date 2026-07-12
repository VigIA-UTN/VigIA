package dev.vigia.backend.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RodeoResponse {
    private Long id;
    private String nombre;
    private boolean activo;
    private LocalDateTime fechaAlta;
    private Long establecimientoId;
}
