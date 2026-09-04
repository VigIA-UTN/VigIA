package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un punto de la serie comparativa "comportamiento actual vs. perfil de
 * referencia" para el detalle de una alerta sanitaria. Cada bloque cubre 6hs
 * de la ventana de 24hs evaluada (0-6h, 6-12h, 12-18h, 18-24h), alineado con
 * los "quarters" que calcula ml-salud (ver features_salud.py).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloqueComparativoResponse {

    /** Etiqueta del bloque: "0-6h", "6-12h", "12-18h" o "18-24h". */
    private String bloque;

    /** Valor promedio observado en el bloque (actividad = moveState, desplazamiento = distancia GPS escalada). */
    private Double actual;

    /** Media del perfil de referencia individual del animal para ese bloque. Null si no hay perfil. */
    private Double referenciaMedia;

    /** Desvío estándar del perfil de referencia para ese bloque. Null si no hay perfil. */
    private Double referenciaDesvio;
}
