package dev.vigia.backend.dto;

import dev.vigia.backend.model.TipoEstadoSalud;
import dev.vigia.backend.model.TipoIntervencion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pestaña "Salud y reproducción" del Dashboard (H0044), ya calculada para el establecimiento, el rodeo (si se pidió)
 * y el período.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaludReproduccionResponse {

    private int periodoDias;
    private Indicadores indicadores;
    /** Celos con la ventana abierta o por abrir: primero las abiertas, cada grupo por tiempo restante. */
    private List<VentanaAlertaResponse> ventanas;
    private Embudo embudo;
    /** Alertas sanitarias pendientes, de la más vieja a la más nueva. */
    private List<AlertaAnimal> alertasSaludSinAtender;
    /** Enfermos primero, después en observación. */
    private List<AnimalEnObservacion> enObservacion;
    private Partos partos;
    private Desempenyo desempenyo;
    private Reincidencia reincidencia;
    private UltimaIntervencion ultimaIntervencion;
    private UmbralesDashboardResponse umbrales;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Indicadores {
        private long diasAbiertosPromedio;
        /** Vacas todavía no preñadas con parto registrado: la base del promedio. */
        private long vacasAbiertas;
        /** Alertas sanitarias pendientes generadas hace más de {@code umbrales.horasSaludSinAtender}. */
        private long saludSinAtender;
        /** Celos con ventana abierta o por abrir. */
        private long celosSinAtender;
        private long celosPorVencer;
        /** Con caravana y todavía sin perfil de salud: sus alertas sanitarias no son confiables aún. */
        private long animalesEnCalibracion;
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
        private List<AnimalResumenResponse> vaciasLargas;
        private List<AnimalResumenResponse> inseminadasSinDiagnostico;
        /** Con la alerta de anestro todavía pendiente: intervenir la cierra. */
        private List<AnimalResumenResponse> anestroSinIntervencion;
        /** Reproductivas con estado registrado, sobre el total, redondeado. */
        private int completitudPorcentaje;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertaAnimal {
        private AlertaResponse alerta;
        private AnimalResumenResponse animal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnimalEnObservacion {
        private AnimalResumenResponse animal;
        private TipoEstadoSalud estadoSalud;
        /** Null si nunca tuvo una intervención. */
        private TipoIntervencion ultimaIntervencionTipo;
        /** Días completos desde la última intervención; null si nunca tuvo. */
        private Long diasSinIntervencion;
    }

    /** Partos esperados a {@code umbrales.diasGestacion} días de la última inseminación de cada gestante. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Partos {
        /** Las próximas 12 semanas, cada una de 7 días desde hoy. */
        private List<SemanaPartos> semanas;
        /** Por fecha, desde el lunes de esta semana y por un año; solo los días con partos. */
        private List<DiaPartos> porDia;
        private long gestantesSinFecha;
        /** Gestantes cuya fecha esperada ya pasó: posible parto sin registrar. */
        private long vencidos;
        private long totalGestantes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SemanaPartos {
        private LocalDate desde;
        private List<AnimalResumenResponse> animales;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiaPartos {
        private LocalDate fecha;
        private List<AnimalResumenResponse> animales;
    }

    /** Lo que pasó con los celos del período y con las inseminadas en él. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Desempenyo {
        private long reproductivos;
        private long celosDetectados;
        private long celosAtendidos;
        /** Celos del período archivados sin atender. */
        private long ventanasPerdidas;
        private long inseminadas;
        /** De las inseminadas en el período, las que hoy están gestantes. */
        private long gestantes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reincidencia {
        /** Hasta 5 animales con 2 o más alertas en el período, de más a menos. */
        private List<AnimalAlertas> animales;
        /** Hasta 3 rodeos con 2 o más salidas de perímetro en el período, de más a menos. */
        private List<LoteSalidas> lotes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnimalAlertas {
        /** Null si el animal ya no está activo. */
        private AnimalResumenResponse animal;
        private long alertas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoteSalidas {
        private String nombre;
        private long salidas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UltimaIntervencion {
        /** La más reciente entre los animales de la pestaña; null si no hay. */
        private Intervencion masReciente;
        /** Hasta 5, los que nunca tuvieron primero y después los que llevan más días. */
        private List<AnimalSinIntervencion> sinIntervencionHaceMas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Intervencion {
        private AnimalResumenResponse animal;
        private TipoIntervencion tipo;
        private LocalDateTime fechaHora;
        private String interventor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnimalSinIntervencion {
        private AnimalResumenResponse animal;
        /** Null si nunca tuvo. */
        private Long dias;
    }
}
