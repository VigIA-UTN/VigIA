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
public class GeoAnalyzeResponseDTO {
    @JsonProperty("mapa_calor")
    private List<MatrizDensidadDTO> mapaCalor;

    private List<AlertaAnimalDTO> alertas;

    /**
     * Lista de animales identificados como alejados del grupo principal del rodeo.
     * El backend resuelve cada animal_id de ml-geo a sus datos de presentación
     * (caravana oficial, alias) para simplificar el rendering en el frontend.
     * Nunca es null: lista vacía si no hay animales alejados o si ml-geo no devolvió alertas.
     */
    @JsonProperty("animales_alejados")
    private List<AnimalAlejadoDTO> animalesAlejados;
}
