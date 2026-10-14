package dev.vigia.backend.dto;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CohesionPuntoSerie {
    /** Inicio del intervalo (hora o día) con su offset. */
    OffsetDateTime ts;
    /** Promedio del animal en el intervalo, 0–100; null = sin datos. */
    Double valor;
    Double promedioRodeo;
}
