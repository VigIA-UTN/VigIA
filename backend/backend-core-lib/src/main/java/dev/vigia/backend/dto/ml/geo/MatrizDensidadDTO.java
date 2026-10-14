package dev.vigia.backend.dto.ml.geo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatrizDensidadDTO {
    private Double lat;
    private Double lng;
    private Double intensidad;
}
