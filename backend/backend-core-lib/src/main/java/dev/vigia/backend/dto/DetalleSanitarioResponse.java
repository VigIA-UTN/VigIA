package dev.vigia.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DetalleSanitarioResponse {

    private Boolean desvioDetectado;

    private String severidad;

    private String posibleCausa;

    private String descripcionDesvio;

    private Double confianzaModelo;

    private LocalDateTime perfilCalculadoEn;

    private LocalDateTime ventanaDesde;

    private LocalDateTime ventanaHasta;

    private List<BloqueComparativoResponse> serieActividad;

    private List<BloqueComparativoResponse> serieDesplazamiento;
}
