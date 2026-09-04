package dev.vigia.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot congelado de la serie comparativa "comportamiento actual vs. perfil
 * de referencia" de una alerta sanitaria. Lo calcula el worker
 * ({@link dev.vigia.backend.util.SerieComparativaBuilder}) al crear la alerta,
 * a partir de la ventana de telemetría que evaluó ml-salud y del perfil de
 * referencia vigente en ese momento, y se persiste como JSON en
 * {@code alertas.serie_comparativa_json}. El endpoint de detalle solo lo
 * deserializa; nunca se recalcula.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SerieComparativaSnapshot {

    /** Descripción del desvío generada en el backend a partir de la serie. */
    private String descripcionDesvio;

    /** Inicio de la ventana evaluada (primer punto de telemetría). */
    private LocalDateTime ventanaDesde;

    /** Fin de la ventana evaluada (último punto de telemetría). */
    private LocalDateTime ventanaHasta;

    /** Fecha de cálculo del perfil de referencia usado. Null si no había perfil. */
    private LocalDateTime perfilCalculadoEn;

    /** Actividad (moveState) por bloque de 6hs: actual vs. perfil. Null si no había telemetría. */
    private List<BloqueComparativoResponse> serieActividad;

    /** Desplazamiento (distancia GPS) por bloque de 6hs: actual vs. perfil. Null si no había telemetría. */
    private List<BloqueComparativoResponse> serieDesplazamiento;
}
