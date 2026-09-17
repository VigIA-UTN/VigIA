package dev.vigia.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SerieComparativaSnapshot {

    private String descripcionDesvio;

    private LocalDateTime ventanaDesde;

    private LocalDateTime ventanaHasta;

    private LocalDateTime perfilCalculadoEn;

    private List<BloqueComparativoResponse> serieActividad;

    private List<BloqueComparativoResponse> serieDesplazamiento;
}
