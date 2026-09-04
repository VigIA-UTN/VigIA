package dev.vigia.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import dev.vigia.backend.model.OrigenEstado;
import dev.vigia.backend.model.TipoEstadoSalud;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EstadoSaludResponse {
    private Long id;
    private TipoEstadoSalud estado;
    private LocalDate fechaRevision;
    private String diagnosticoNotas;
    private String tratamientoIndicado;
    private OrigenEstado origen;
    private LocalDateTime fechaRegistro;
    private String veterinarioNombre;
}
