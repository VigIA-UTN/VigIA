package dev.vigia.backend.dto.ml.geo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoAnalyzeRequestDTO {
    @JsonProperty("animal_id")
    private String animalId;

    /** Animales a incluir en el mapa de calor (p. ej. los de un rodeo); null = todos. */
    @JsonProperty("animal_ids")
    private List<String> animalIds;

    @JsonProperty("historial_7d")
    private List<CoordenadaGPSDTO> historial7d;

    /**
     * Umbral de aislamiento en metros, específico del establecimiento.
     * Si es null, ml-geo usa su valor por defecto (variable de entorno UMBRAL_AISLAMIENTO_METROS).
     */
    @JsonProperty("umbral_aislamiento_m")
    private Double umbralAislamientoM;
}
