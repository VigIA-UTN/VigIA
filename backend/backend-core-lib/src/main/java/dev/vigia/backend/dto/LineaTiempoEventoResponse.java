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

    /**
     * Discriminador del evento. Valores posibles: {@code "ALERTA"}, {@code "INTERVENCION"}.
     */
    private String tipoEvento;

    // ── Campos comunes ────────────────────────────────────────────────────────

    /** ID del evento (alerta o intervención). */
    private Long id;

    /**
     * Fecha y hora del evento.
     * Para alertas corresponde a {@code tsGeneracion};
     * para intervenciones, a {@code fechaHora}.
     */
    private LocalDateTime fecha;

    // ── Campos de ALERTA ─────────────────────────────────────────────────────

    /**
     * Tipo/disparador de la alerta. Valores posibles:
     * {@code CELO}, {@code SALUD}, {@code ABIGEATO}, {@code FUERA_DE_PERIMETRO}.
     * {@code null} para eventos de tipo INTERVENCION.
     */
    private String tipo;

    /**
     * Severidad de la alerta: {@code ALTA}, {@code MEDIA}, {@code BAJA}.
     * {@code null} para eventos de tipo INTERVENCION.
     */
    private String severidad;

    /**
     * Estado de resolución de la alerta:
     * <ul>
     *   <li>{@code "CONFIRMADA"} — alerta confirmada por un usuario.</li>
     *   <li>{@code "DESCARTADA"} — alerta descartada por un usuario.</li>
     *   <li>{@code "SIN_ATENDER"} — alerta en estado PENDIENTE, VENCIDA o ARCHIVADA.</li>
     * </ul>
     * {@code null} para eventos de tipo INTERVENCION.
     */
    private String estadoResolucion;

    // ── Campos de INTERVENCION ────────────────────────────────────────────────

    /**
     * Tipo de intervención (p. ej. {@code INSEMINACION}, {@code REVISION_VETERINARIA}).
     * {@code null} para eventos de tipo ALERTA.
     */
    private String tipoIntervencion;

    /**
     * Descripción libre registrada por el usuario interventor.
     * {@code null} para eventos de tipo ALERTA.
     */
    private String descripcion;

    /**
     * Nombre del usuario que realizó la intervención.
     * {@code null} para eventos de tipo ALERTA.
     */
    private String interventor;

    /**
     * ID de la alerta a la que está asociada la intervención, si existe.
     * {@code null} si la intervención no está ligada a ninguna alerta, o si el evento es ALERTA.
     */
    private Long alertaAsociadaId;
}
