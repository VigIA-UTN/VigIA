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
public class CoordenadaGPSDTO {
    @JsonProperty("animal_id")
    private String animalId;
    
    private Double lat;
    private Double lng;
    
    private String timestamp;
}
