package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Pestaña "Resumen ejecutivo" del Dashboard (H0045), ya calculada para todo el establecimiento y el período.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumenEjecutivoResponse {

    private int periodoDias;
    /** Una fila por rodeo activo. */
    private List<FilaRodeo> rodeos;
    /** Promedio de los rodeos con animales, para destacar los que se apartan. */
    private Promedio promedio;
    private CentroMandoResponse.Tendencia tendencia;
    private Equipo equipo;
    private UmbralesDashboardResponse umbrales;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilaRodeo {
        private Long id;
        private String nombre;
        private long animales;
        /** Monitoreados sobre el total, redondeado. */
        private int coberturaPorcentaje;
        private long alertasPendientes;
        /** Alertas pendientes cada 100 animales, redondeado. */
        private int alertasCada100;
        /** Celos del período cuya ventana se perdió. */
        private long ventanasPerdidas;
        /** Vacías con más de {@code umbrales.diasVaciaLarga} desde el parto, sobre las reproductivas. */
        private int vaciasLargasPorcentaje;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Promedio {
        private int coberturaPorcentaje;
        private int alertasCada100;
        private int ventanasPerdidas;
        private int vaciasLargasPorcentaje;
    }

    /** ¿Está actuando el equipo? */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Equipo {
        /** Alertas pendientes ahora. */
        private long enCola;
        /** Confirmadas o descartadas sobre las generadas en el período, redondeado. */
        private int porcentajeAtendidas;
        /** Descartadas sobre las atendidas, redondeado. */
        private int porcentajeDescartadas;
        private long totalAtendidas;
        /** Desde que se generó hasta que se resolvió: menos de 6 h, 6 a 24 h, 1 a 3 días y más de 3 días. */
        private List<RangoAtencion> tiempoAtencion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RangoAtencion {
        private String rango;
        private long alertas;
    }
}
