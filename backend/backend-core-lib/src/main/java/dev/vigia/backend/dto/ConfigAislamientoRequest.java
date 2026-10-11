package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para actualizar el umbral de aislamiento de un establecimiento.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigAislamientoRequest {
    /**
     * Umbral de distancia al centroide del rodeo a partir del cual se considera aislado el animal (en metros).
     * Rango permitido: 10.0 – 1000.0 m.
     */
    private Double umbralAislamientoM;
}
