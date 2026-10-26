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
     * Distancia máxima (en metros) entre dos animales vecinos para considerarlos parte del mismo grupo.
     * ml-geo arma los grupos del rodeo encadenando animales a esta distancia o menos; el grupo más
     * grande es el principal y los animales que quedan fuera de él se consideran alejados.
     * Rango permitido: 10.0 – 1000.0 m.
     */
    private Double umbralAislamientoM;
}
