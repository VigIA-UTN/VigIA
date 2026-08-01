package dev.vigia.backend.dto.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CeloPredictResponseDTO {

    @JsonProperty("evento_celo")
    private boolean eventoCelo;

    @JsonProperty("confianza_modelo")
    private float confianzaModelo;

    @JsonProperty("ventana_optima_horas")
    private int ventanaOptimaHoras;
}
