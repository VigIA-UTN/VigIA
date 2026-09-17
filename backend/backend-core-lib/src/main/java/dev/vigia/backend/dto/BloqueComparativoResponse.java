package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloqueComparativoResponse {

    private String bloque;

    private Double actual;

    private Double referenciaMedia;

    private Double referenciaDesvio;
}
