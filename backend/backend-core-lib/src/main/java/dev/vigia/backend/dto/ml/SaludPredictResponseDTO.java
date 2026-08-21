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
public class SaludPredictResponseDTO {

    @JsonProperty("desvio_detectado")
    private boolean desvioDetectado;

    private int severidad;

    @JsonProperty("posible_causa")
    private String posibleCausa;

    @JsonProperty("confianza_modelo")
    private float confianzaModelo;
}
