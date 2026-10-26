package dev.vigia.backend.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CohesionResponse {
    /** Índice actual, 0–100; null si no hay análisis en el período. */
    Double actual;
    /** Promedio del rodeo en el período; null sin datos. */
    Double promedioRodeo;

    Double promedioPeriodoAnterior;
    /** Un punto por hora (rangos de hasta 48 h) o por día, incluidos los intervalos sin datos. */
    List<CohesionPuntoSerie> serie;
}
