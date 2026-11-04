package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Pestaña "Centro de mando" del Dashboard (H0043), ya calculada para el establecimiento y, si se pidió, el rodeo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CentroMandoResponse {

    private AlertasPendientes alertasPendientes;
    private VentanasAbiertas ventanasAbiertas;
    private BajoObservacion bajoObservacion;
    private Cobertura cobertura;
    /** Hasta {@code umbrales.colaMax} ítems, del más urgente al menos urgente. */
    private List<ItemCola> cola;
    /** Ventanas abiertas o por abrir: primero las abiertas, cada grupo por tiempo restante. */
    private List<VentanaAlertaResponse> ventanas;
    private Embudo embudo;
    private Tendencia tendencia;
    private UmbralesDashboardResponse umbrales;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertasPendientes {
        private long total;
        private long celo;
        private long salud;
        private long perimetro;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VentanasAbiertas {
        private long total;
        /** Ventanas ya abiertas que cierran en 3 h o menos. */
        private long porVencer;
        /** Celos de los últimos 30 días que se archivaron sin atender: la ventana se perdió. */
        private long perdidasUltimos30Dias;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BajoObservacion {
        /** Animales enfermos o en observación. */
        private long total;
        private long enfermos;
    }

    /** Cada animal activo cae en un solo grupo, en este orden: sin caravana, calibrando, sin señal, batería baja, ok. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cobertura {
        private long total;
        private long ok;
        private long calibrando;
        private long sinSenial;
        private long bateriaBaja;
        private long sinCaravana;
        /** Monitoreados sobre el total, redondeado. */
        private int porcentaje;
    }

    /**
     * Una alerta pendiente, o una caravana que no transmite (tipo {@code CARAVANA}, con id negativo) para un animal
     * que no tiene otra alerta.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemCola {
        private AlertaResponse alerta;
        private AnimalResumenResponse animal;
        /** Solo en los celos con ventana de inseminación. */
        private VentanaInseminacionResponse ventana;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Embudo {
        /** Animales activos de 1 año o más. */
        private long totalReproductivos;
        /** Por estado reproductivo actual, en el orden del embudo; {@code SIN_ESTADO} si no tiene. */
        private Map<String, Long> distribucion;
        /** Promedio, redondeado, desde el último parto de las vacas todavía no preñadas. */
        private long diasAbiertosPromedio;
        private long vacasAbiertas;
        private long inseminadasSinDiagnostico;
    }

    /** Alertas generadas por semana ISO (las 8 últimas, la actual al final) y por tipo. Los anestros no cuentan. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tendencia {
        /** Claves {@code 2026-W45}. */
        private List<String> semanas;
        private List<String> tipos;
        private Map<String, List<Long>> datos;
    }
}
