package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * DTO que describe el estado actual de la ventana optima de inseminacion
 * para una alerta de celo.
 *
 * La ventana optima va de ventanaInicioIns a ventanaFinIns
 * (6 a 24 horas desde la deteccion del celo). Cuando el instante actual supera
 * ventanaFinIns la ventana se considera VENCIDA.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VentanaInseminacionResponse {

    /** ID de la alerta consultada. */
    private Long alertaId;

    /**
     * Segundos que restan hasta el fin de la ventana optima.
     * Vale 0 cuando la ventana ya vencio.
     */
    private long tiempoRestanteSegundos;

    /**
     * Porcentaje de la ventana que ya ha transcurrido (0.0-100.0).
     * Vale 100.0 cuando la ventana vencio.
     */
    private double porcentajeTranscurrido;

    /**
     * true cuando quedan menos de 2 horas para que venza la ventana
     * y esta aun no ha vencido.
     */
    private boolean proximaAVencer;

    /**
     * Estado de la ventana: ACTIVA o VENCIDA.
     */
    private String estado;
}