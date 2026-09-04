package dev.vigia.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Bloque sanitario del detalle de una alerta. Se completa únicamente cuando la
 * alerta tiene {@code disparador = SALUD}; para el resto de las alertas queda
 * {@code null} dentro de {@link AlertaDetalleResponse}.
 */
@Data
@Builder
public class DetalleSanitarioResponse {

    /** Desvío detectado por ml-salud respecto del perfil individual del animal. */
    private Boolean desvioDetectado;

    /** Severidad de la alerta (BAJA, MEDIA, ALTA). */
    private String severidad;

    /** Causa probable devuelta por ml-salud. */
    private String posibleCausa;

    /** Descripción del desvío generada por el backend a partir de la serie comparativa. */
    private String descripcionDesvio;

    /** Confianza del modelo (0-1). */
    private Double confianzaModelo;

    /** Fecha de cálculo del perfil de referencia usado como comparación. Null si no hay perfil. */
    private LocalDateTime perfilCalculadoEn;

    /** Inicio de la ventana de 24hs evaluada (ts_generacion - 24h). */
    private LocalDateTime ventanaDesde;

    /** Fin de la ventana de 24hs evaluada (ts_generacion). */
    private LocalDateTime ventanaHasta;

    /** Serie de actividad (moveState) por bloque de 6hs: actual vs. perfil. Null si no hay telemetría. */
    private List<BloqueComparativoResponse> serieActividad;

    /** Serie de desplazamiento (distancia GPS) por bloque de 6hs: actual vs. perfil. Null si no hay telemetría. */
    private List<BloqueComparativoResponse> serieDesplazamiento;
}
