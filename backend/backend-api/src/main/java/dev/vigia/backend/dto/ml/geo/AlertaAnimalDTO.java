package dev.vigia.backend.dto.ml.geo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertaAnimalDTO {
    @JsonProperty("animal_id")
    private String animalId;
    
    @JsonProperty("indice_cohesion")
    private Double indiceCohesion;
    
    @JsonProperty("alerta_aislamiento")
    private Boolean alertaAislamiento;
}
