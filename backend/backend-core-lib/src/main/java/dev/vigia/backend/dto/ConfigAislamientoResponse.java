package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta con el umbral de aislamiento configurado para un establecimiento.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigAislamientoResponse {
    private Long establecimientoId;
    private String nombreEstablecimiento;
    private Double umbralAislamientoM;
}
