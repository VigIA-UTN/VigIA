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
    /** Índice actual, 0–100. */
    Double actual;
    /** Promedio del rodeo en el período. */
    Double promedioRodeo;

    Double promedioPeriodoAnterior;
    /** Serie de puntos con marca de tiempo y valor. */
    List<CohesionPuntoSerie> serie;
}
