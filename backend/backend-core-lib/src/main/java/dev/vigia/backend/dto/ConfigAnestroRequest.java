package dev.vigia.backend.dto;

import lombok.Data;

/**
 * Request para actualizar los parámetros de anestro de un establecimiento.
 */
@Data
public class ConfigAnestroRequest {
    /**
     * Días máximos sin ciclo reproductivo antes de generar una alerta de anestro.
     * Rango válido: 1 – 365.
     */
    private Integer diasDeteccionAnestro;

    /**
     * Prioridad de la alerta de anestro.
     * Valores válidos: BAJA, MEDIA, ALTA.
     */
    private String severidadAnestro;
}
