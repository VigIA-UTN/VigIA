package dev.vigia.backend.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CeloPredictRequestDTO {
    private List<VectorAceleracionDTO> seriesTemporales;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorAceleracionDTO {
        private Double x;
        private Double y;
        private Double z;
    }
}
