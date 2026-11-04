package dev.vigia.backend.service;

import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.model.Alerta;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Estado de la ventana de inseminación de una alerta de celo en un momento dado. La usan el detalle de la alerta y el
 * Dashboard, así los dos muestran lo mismo.
 * <p>
 * Si la alerta no trae la ventana, se toma la por defecto: de 6 a 24 h desde la detección.
 */
public final class VentanaInseminacionCalculadora {

    /** A 2 h o menos del cierre la ventana se destaca como próxima a vencer. */
    private static final long SEGUNDOS_PROXIMA_A_VENCER = 2 * 3600;

    private VentanaInseminacionCalculadora() {
    }

    public static VentanaInseminacionResponse calcular(Alerta alerta, LocalDateTime ahora) {
        LocalDateTime inicio = alerta.getVentanaInicioIns();
        LocalDateTime fin = alerta.getVentanaFinIns();
        if (inicio == null || fin == null) {
            inicio = alerta.getTsGeneracion().plusHours(6);
            fin = alerta.getTsGeneracion().plusHours(24);
        }

        if (!ahora.isBefore(fin)) {
            return ventana("VENCIDA", 0, 100.0, false);
        }
        if (ahora.isBefore(inicio)) {
            // Hasta que abre: así lo muestran el detalle de la alerta ("Tiempo hasta inicio de ventana óptima"), su
            // contador y el Dashboard ("Abre en ...").
            return ventana("FUTURA", Duration.between(ahora, inicio).getSeconds(), 0.0, false);
        }

        long restante = Duration.between(ahora, fin).getSeconds();
        long total = Duration.between(inicio, fin).getSeconds();
        double porcentaje = 100.0 * Duration.between(inicio, ahora).getSeconds() / total;
        porcentaje = Math.max(0.0, Math.min(100.0, porcentaje));
        return ventana("ACTIVA", restante, porcentaje, restante <= SEGUNDOS_PROXIMA_A_VENCER);
    }

    private static VentanaInseminacionResponse ventana(String estado, long restante, double porcentaje,
            boolean proximaAVencer) {
        return VentanaInseminacionResponse.builder()
                .estado(estado)
                .tiempoRestanteSegundos(restante)
                .porcentajeTranscurrido(porcentaje)
                .proximaAVencer(proximaAVencer)
                .build();
    }
}
