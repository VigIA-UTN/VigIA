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
public class CeldasSubutilizadasDTO {
    /** Alto y ancho de cada celda (grados), para dibujarlas como rectángulos. */
    @JsonProperty("lat_step")
    private Double latStep;

    @JsonProperty("lng_step")
    private Double lngStep;

    /** Centro de cada celda subutilizada. */
    private List<Celda> celdas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Celda {
        private Double lat;
        private Double lng;
    }
}
