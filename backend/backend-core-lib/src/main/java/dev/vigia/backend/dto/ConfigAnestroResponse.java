package dev.vigia.backend.dto;

import dev.vigia.backend.model.SeveridadAlerta;
import lombok.Builder;
import lombok.Data;

/**
 * Respuesta con los parámetros de configuración de anestro de un establecimiento.
 */
@Data
@Builder
public class ConfigAnestroResponse {
    private Long establecimientoId;
    private String nombreEstablecimiento;
    private Integer diasDeteccionAnestro;
    private SeveridadAlerta severidadAnestro;
}
