package dev.vigia.backend.service.dashboard;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.AnimalResumenResponse;
import dev.vigia.backend.dto.CentroMandoResponse.Embudo;
import dev.vigia.backend.dto.UmbralesDashboardResponse;
import dev.vigia.backend.dto.VentanaAlertaResponse;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.TipoEstadoSalud;
import dev.vigia.backend.model.TipoIntervencion;
import dev.vigia.backend.model.TipoReproductivo;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Datos de entrada de las calculadoras del Dashboard y los criterios que comparten sus pestañas, para que cada uno
 * esté en un solo lugar.
 */
public final class DashboardDatos {

    /** Estados en los que la vaca sigue abierta (sin preñez confirmada) desde el último parto. */
    static final Set<TipoReproductivo> ESTADOS_ABIERTOS = EnumSet.of(TipoReproductivo.VACIA, TipoReproductivo.CELO,
            TipoReproductivo.POSIBLE_CELO, TipoReproductivo.INSEMINADA, TipoReproductivo.PARIDA,
            TipoReproductivo.ANESTRO);

    /** Orden de los segmentos del embudo. */
    static final List<String> ORDEN_EMBUDO = List.of("VACIA", "CELO", "POSIBLE_CELO", "INSEMINADA", "GESTANTE",
            "PARIDA", "ANESTRO", "SIN_ESTADO");

    private static final double MS_POR_HORA = 3_600_000.0;

    private DashboardDatos() {
    }

    /** Lo que los cálculos leen de cada animal activo. */
    public record AnimalDashboard(Long id, String idCaravanaOficial, String alias, Long rodeoId, String rodeoNombre,
            LocalDate fechaNacimiento, LocalDate fechaAlta, boolean tieneCaravana, boolean enCalibracion,
            LocalDateTime ultimaSenial, Double bateria, TipoReproductivo estadoReproductivo,
            LocalDate fechaUltimoParto, LocalDate fechaUltimaInseminacion, TipoEstadoSalud estadoSalud) {
    }

    /** Una alerta generada en el período que se analiza, en cualquier estado. */
    public record AlertaGenerada(Long animalId, DisparadorAlerta disparador, String tipo, LocalDateTime tsGeneracion,
            EstadoAlerta estado) {
    }

    /** La última intervención registrada sobre un animal. */
    public record IntervencionDashboard(Long animalId, TipoIntervencion tipo, LocalDateTime fechaHora,
            String interventor) {
    }

    // ── Criterios compartidos ────────────────────────────────────────────────

    /** De 1 año o más: entra en el embudo y en el desempeño reproductivo. */
    static boolean esReproductiva(AnimalDashboard animal, LocalDateTime ahora) {
        return animal.fechaNacimiento() != null
                && !animal.fechaNacimiento().plusYears(1).isAfter(ahora.toLocalDate());
    }

    /** Celo pendiente con ventana de inseminación: el anestro también llega como CELO, pero no la tiene. */
    public static boolean esCeloConVentana(AlertaResponse alerta) {
        return DisparadorAlerta.CELO.name().equals(alerta.getTipo()) && !esAnestro(alerta.getSubtipo());
    }

    /**
     * Celo cuya ventana se perdió sin atenderlo: el job lo archiva al cerrarse la ventana (ARCHIVADA) y las alertas
     * cargadas como VENCIDA también la perdieron.
     */
    static boolean ventanaPerdida(AlertaGenerada alerta) {
        return alerta.estado() == EstadoAlerta.ARCHIVADA || alerta.estado() == EstadoAlerta.VENCIDA;
    }

    static boolean esAnestro(String tipo) {
        return tipo != null && tipo.toLowerCase().contains("anestro");
    }

    static double horasDesde(LocalDateTime momento, LocalDateTime ahora) {
        return Duration.between(momento, ahora).toMillis() / MS_POR_HORA;
    }

    static double diasDesde(LocalDate fecha, LocalDateTime ahora) {
        return horasDesde(fecha.atStartOfDay(), ahora) / 24;
    }

    static double diasDesde(LocalDateTime momento, LocalDateTime ahora) {
        return horasDesde(momento, ahora) / 24;
    }

    static AnimalResumenResponse resumen(AnimalDashboard animal) {
        if (animal == null) {
            return null;
        }
        return AnimalResumenResponse.builder()
                .id(animal.id())
                .idCaravanaOficial(animal.idCaravanaOficial())
                .alias(animal.alias())
                .rodeoLoteNombre(animal.rodeoNombre())
                .build();
    }

    // ── Bloques compartidos ──────────────────────────────────────────────────

    /** Celos con la ventana abierta o por abrir: primero las abiertas, cada grupo por tiempo restante. */
    static List<VentanaAlertaResponse> ventanas(List<AlertaResponse> pendientes,
            Map<Long, VentanaInseminacionResponse> ventanasPorAlerta, Map<Long, AnimalDashboard> porId) {
        return pendientes.stream()
                .filter(a -> DisparadorAlerta.CELO.name().equals(a.getTipo()))
                .filter(a -> ventanasPorAlerta.containsKey(a.getId()))
                .filter(a -> {
                    String estado = ventanasPorAlerta.get(a.getId()).getEstado();
                    return "ACTIVA".equals(estado) || "FUTURA".equals(estado);
                })
                .map(a -> VentanaAlertaResponse.builder()
                        .alerta(a)
                        .animal(resumen(porId.get(a.getAnimalId())))
                        .ventana(ventanasPorAlerta.get(a.getId()))
                        .build())
                .sorted(Comparator.comparing((VentanaAlertaResponse v) -> "FUTURA".equals(v.getVentana().getEstado()))
                        .thenComparingLong(v -> v.getVentana().getTiempoRestanteSegundos()))
                .toList();
    }

    /** Ventanas ya abiertas que cierran dentro del umbral. */
    static long porVencer(List<VentanaAlertaResponse> ventanas, UmbralesDashboardResponse umbrales) {
        return ventanas.stream()
                .filter(v -> "ACTIVA".equals(v.getVentana().getEstado())
                        && v.getVentana().getTiempoRestanteSegundos() <= umbrales.getHorasVentanaPorVencer() * 3600L)
                .count();
    }

    /** Distribución de las de 1 año o más, días abiertos e inseminadas sin diagnóstico. */
    static Embudo embudo(List<AnimalDashboard> animales, UmbralesDashboardResponse umbrales, LocalDateTime ahora) {
        Map<String, Long> distribucion = new LinkedHashMap<>();
        ORDEN_EMBUDO.forEach(estado -> distribucion.put(estado, 0L));
        double diasAbiertos = 0;
        long abiertas = 0;
        long inseminadasSinDiagnostico = 0;

        List<AnimalDashboard> reproductivos = animales.stream().filter(a -> esReproductiva(a, ahora)).toList();
        for (AnimalDashboard a : reproductivos) {
            String estado = a.estadoReproductivo() != null ? a.estadoReproductivo().name() : "SIN_ESTADO";
            distribucion.merge(estado, 1L, Long::sum);

            if (a.estadoReproductivo() != null && ESTADOS_ABIERTOS.contains(a.estadoReproductivo())
                    && a.fechaUltimoParto() != null) {
                diasAbiertos += diasDesde(a.fechaUltimoParto(), ahora);
                abiertas++;
            }
            if (inseminadaSinDiagnostico(a, umbrales, ahora)) {
                inseminadasSinDiagnostico++;
            }
        }
        return Embudo.builder()
                .totalReproductivos(reproductivos.size())
                .distribucion(distribucion)
                .diasAbiertosPromedio(abiertas == 0 ? 0 : Math.round(diasAbiertos / abiertas))
                .vacasAbiertas(abiertas)
                .inseminadasSinDiagnostico(inseminadasSinDiagnostico)
                .build();
    }

    static boolean inseminadaSinDiagnostico(AnimalDashboard a, UmbralesDashboardResponse umbrales,
            LocalDateTime ahora) {
        return a.estadoReproductivo() == TipoReproductivo.INSEMINADA && a.fechaUltimaInseminacion() != null
                && diasDesde(a.fechaUltimaInseminacion(), ahora) > umbrales.getDiasDiagnosticoInseminada();
    }
}
