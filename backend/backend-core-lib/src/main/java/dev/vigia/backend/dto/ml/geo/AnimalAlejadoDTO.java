package dev.vigia.backend.dto.ml.geo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa un animal identificado como alejado del grupo principal del rodeo.
 * El backend resuelve el animal_id recibido de ml-geo a los datos de presentación
 * (caravana oficial, alias) para que el frontend no necesite un segundo request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnimalAlejadoDTO {

    @JsonProperty("animal_id")
    private Long animalId;

    /** Identificador de caravana oficial (p. ej. "AR-001"). Puede ser null si no tiene. */
    @JsonProperty("caravana")
    private String caravana;

    /** Alias del animal (p. ej. "Manchita"). Puede ser null si no tiene. */
    private String alias;
}
