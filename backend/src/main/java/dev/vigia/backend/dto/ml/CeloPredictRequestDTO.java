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
    private List<PuntoTelemetriaDTO> seriesTemporales;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PuntoTelemetriaDTO {
        private Double x;
        private Double y;
        private Double z;
        private Double latitud;
        private Double longitud;
    }
}
