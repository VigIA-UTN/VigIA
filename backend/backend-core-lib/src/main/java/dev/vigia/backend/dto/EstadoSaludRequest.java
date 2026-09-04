package dev.vigia.backend.dto;

import dev.vigia.backend.model.TipoEstadoSalud;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EstadoSaludRequest {

    @NotNull(message = "El estado de salud es obligatorio")
    private TipoEstadoSalud estado;

    private String diagnosticoNotas;

    private String tratamientoIndicado;
}
