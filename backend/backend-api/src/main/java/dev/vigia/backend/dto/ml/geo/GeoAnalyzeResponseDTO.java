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

    /** Celdas del campo poco o nada visitadas por los animales filtrados; null si no hay datos. */
    @JsonProperty("celdas_subutilizadas")
    private CeldasSubutilizadasDTO celdasSubutilizadas;
}
