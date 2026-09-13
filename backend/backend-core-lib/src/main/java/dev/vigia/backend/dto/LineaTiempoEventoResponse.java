package dev.vigia.backend.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa un evento en la línea de tiempo de alertas de un animal.
 * <p>
 * El campo {@code tipoEvento} distingue entre dos tipos de eventos:
 * <ul>
 *   <li>{@code "ALERTA"} — evento generado automáticamente por el sistema de detección.</li>
 *   <li>{@code "INTERVENCION"} — acción registrada manualmente por un usuario (H0021).</li>
 * </ul>
 *
 * <p>Los campos relevantes según el tipo de evento son:
 * <ul>
 *   <li>Alertas: {@code tipo}, {@code severidad}, {@code estadoResolucion}.</li>
 *   <li>Intervenciones: {@code tipoIntervencion}, {@code descripcion}, {@code interventor},
 *       {@code alertaAsociadaId}.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineaTiempoEventoResponse {


    private String tipoEvento;
    private Long id;
    private LocalDateTime fecha;

    private String tipo;
    private String severidad;

    private String estadoResolucion;

    private String tipoIntervencion;
    private String descripcion;
    private String interventor;

    private Long alertaAsociadaId;
}
