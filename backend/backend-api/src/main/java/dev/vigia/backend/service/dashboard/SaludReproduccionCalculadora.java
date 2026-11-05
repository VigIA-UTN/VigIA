package dev.vigia.backend.service.dashboard;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.AnimalResumenResponse;
import dev.vigia.backend.dto.CentroMandoResponse;
import dev.vigia.backend.dto.SaludReproduccionResponse;
import dev.vigia.backend.dto.SaludReproduccionResponse.AlertaAnimal;
import dev.vigia.backend.dto.SaludReproduccionResponse.AnimalAlertas;
import dev.vigia.backend.dto.SaludReproduccionResponse.AnimalEnObservacion;
import dev.vigia.backend.dto.SaludReproduccionResponse.AnimalSinIntervencion;
import dev.vigia.backend.dto.SaludReproduccionResponse.Desempenyo;
import dev.vigia.backend.dto.SaludReproduccionResponse.DiaPartos;
import dev.vigia.backend.dto.SaludReproduccionResponse.Embudo;
import dev.vigia.backend.dto.SaludReproduccionResponse.Indicadores;
import dev.vigia.backend.dto.SaludReproduccionResponse.LoteSalidas;
import dev.vigia.backend.dto.SaludReproduccionResponse.Partos;
import dev.vigia.backend.dto.SaludReproduccionResponse.Reincidencia;
import dev.vigia.backend.dto.SaludReproduccionResponse.SemanaPartos;
import dev.vigia.backend.dto.SaludReproduccionResponse.UltimaIntervencion;
import dev.vigia.backend.dto.UmbralesDashboardResponse;
import dev.vigia.backend.dto.VentanaAlertaResponse;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.TipoEstadoSalud;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.service.dashboard.DashboardDatos.AlertaGenerada;
import dev.vigia.backend.service.dashboard.DashboardDatos.AnimalDashboard;
import dev.vigia.backend.service.dashboard.DashboardDatos.IntervencionDashboard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cálculos de la pestaña "Salud y reproducción" (H0044) sobre datos ya cargados y filtrados por rodeo. No consulta
 * nada ni lee el reloj: recibe "ahora", así los tests fijan cada caso.
 */
public final class SaludReproduccionCalculadora {

    static final int SEMANAS_PARTOS = 12;
    /** La vista anual: 53 semanas desde el lunes de esta. */
    static final int DIAS_VISTA_ANUAL = 53 * 7;
    static final int MAX_REINCIDENTES = 5;
    static final int MAX_LOTES_REINCIDENTES = 3;
    static final int MIN_REINCIDENCIA = 2;
    static final int MAX_SIN_INTERVENCION = 5;
    static final String SIN_LOTE = "Sin lote";

    private SaludReproduccionCalculadora() {
    }

    /**
     * @param pendientes    las alertas pendientes de la pestaña (del rodeo, si se filtró)
     * @param anestros      animales con una alerta de anestro pendiente, de todo el establecimiento
     * @param generadas     las alertas generadas en el período, en cualquier estado
     * @param intervenciones la última intervención de cada animal de la pestaña
     */
    public static SaludReproduccionResponse calcular(List<AnimalDashboard> animales, List<AlertaResponse> pendientes,
            Set<Long> anestros, Map<Long, VentanaInseminacionResponse> ventanasPorAlerta,
            List<AlertaGenerada> generadas, List<IntervencionDashboard> intervenciones, int periodoDias,
            UmbralesDashboardResponse umbrales, LocalDateTime ahora) {
        Map<Long, AnimalDashboard> porId = animales.stream()
                .collect(Collectors.toMap(AnimalDashboard::id, Function.identity()));
        Map<Long, IntervencionDashboard> ultimaPorAnimal = intervenciones.stream()
                .collect(Collectors.toMap(IntervencionDashboard::animalId, Function.identity(), (a, b) -> a));

        CentroMandoResponse.Embudo embudo = DashboardDatos.embudo(animales, umbrales, ahora);
        List<VentanaAlertaResponse> ventanas = DashboardDatos.ventanas(pendientes, ventanasPorAlerta, porId);
        List<AlertaAnimal> saludSinAtender = saludSinAtender(pendientes, porId);

        return SaludReproduccionResponse.builder()
                .periodoDias(periodoDias)
                .indicadores(Indicadores.builder()
                        .diasAbiertosPromedio(embudo.getDiasAbiertosPromedio())
                        .vacasAbiertas(embudo.getVacasAbiertas())
                        .saludSinAtender(saludSinAtender.stream()
                                .filter(a -> DashboardDatos.horasDesde(a.getAlerta().getTsGeneracion(), ahora)
                                        > umbrales.getHorasSaludSinAtender())
                                .count())
                        .celosSinAtender(ventanas.size())
                        .celosPorVencer(DashboardDatos.porVencer(ventanas, umbrales))
                        .animalesEnCalibracion(animales.stream().filter(AnimalDashboard::enCalibracion).count())
                        .build())
                .ventanas(ventanas)
                .embudo(embudo(animales, embudo, anestros, umbrales, ahora))
                .alertasSaludSinAtender(saludSinAtender)
                .enObservacion(enObservacion(animales, ultimaPorAnimal, ahora))
                .partos(partos(animales, umbrales, ahora))
                .desempenyo(desempenyo(animales, generadas, periodoDias, ahora))
                .reincidencia(reincidencia(generadas, porId, periodoDias, ahora))
                .ultimaIntervencion(ultimaIntervencion(animales, ultimaPorAnimal, porId, ahora))
                .umbrales(umbrales)
                .build();
    }

    // ── Embudo y excepciones ─────────────────────────────────────────────────

    static Embudo embudo(List<AnimalDashboard> animales, CentroMandoResponse.Embudo base, Set<Long> anestros,
            UmbralesDashboardResponse umbrales, LocalDateTime ahora) {
        List<AnimalDashboard> reproductivos = animales.stream()
                .filter(a -> DashboardDatos.esReproductiva(a, ahora))
                .toList();
        long conEstado = reproductivos.stream().filter(a -> a.estadoReproductivo() != null).count();
        return Embudo.builder()
                .totalReproductivos(base.getTotalReproductivos())
                .distribucion(base.getDistribucion())
                .vaciasLargas(resumenes(reproductivos.stream()
                        .filter(a -> a.estadoReproductivo() == TipoReproductivo.VACIA && a.fechaUltimoParto() != null
                                && DashboardDatos.diasDesde(a.fechaUltimoParto(), ahora) > umbrales.getDiasVaciaLarga())
                        .toList()))
                .inseminadasSinDiagnostico(resumenes(reproductivos.stream()
                        .filter(a -> DashboardDatos.inseminadaSinDiagnostico(a, umbrales, ahora))
                        .toList()))
                .anestroSinIntervencion(resumenes(reproductivos.stream()
                        .filter(a -> anestros.contains(a.id()))
                        .toList()))
                .completitudPorcentaje(reproductivos.isEmpty() ? 0
                        : (int) Math.round(100.0 * conEstado / reproductivos.size()))
                .build();
    }

    // ── Animales a revisar ───────────────────────────────────────────────────

    static List<AlertaAnimal> saludSinAtender(List<AlertaResponse> pendientes, Map<Long, AnimalDashboard> porId) {
        return pendientes.stream()
                .filter(a -> DisparadorAlerta.SALUD.name().equals(a.getTipo()))
                .sorted(Comparator.comparing(AlertaResponse::getTsGeneracion))
                .map(a -> AlertaAnimal.builder()
                        .alerta(a)
                        .animal(DashboardDatos.resumen(porId.get(a.getAnimalId())))
                        .build())
                .toList();
    }

    static List<AnimalEnObservacion> enObservacion(List<AnimalDashboard> animales,
            Map<Long, IntervencionDashboard> ultimaPorAnimal, LocalDateTime ahora) {
        return animales.stream()
                .filter(a -> a.estadoSalud() == TipoEstadoSalud.ENFERMO
                        || a.estadoSalud() == TipoEstadoSalud.EN_OBSERVACION)
                .sorted(Comparator.comparing((AnimalDashboard a) -> a.estadoSalud() != TipoEstadoSalud.ENFERMO))
                .map(a -> {
                    IntervencionDashboard ultima = ultimaPorAnimal.get(a.id());
                    return AnimalEnObservacion.builder()
                            .animal(DashboardDatos.resumen(a))
                            .estadoSalud(a.estadoSalud())
                            .ultimaIntervencionTipo(ultima != null ? ultima.tipo() : null)
                            .diasSinIntervencion(ultima != null ? diasCompletos(ultima.fechaHora(), ahora) : null)
                            .build();
                })
                .toList();
    }

    // ── Partos esperados ─────────────────────────────────────────────────────

    static Partos partos(List<AnimalDashboard> animales, UmbralesDashboardResponse umbrales, LocalDateTime ahora) {
        LocalDate hoy = ahora.toLocalDate();
        List<AnimalDashboard> gestantes = animales.stream()
                .filter(a -> a.estadoReproductivo() == TipoReproductivo.GESTANTE)
                .toList();
        List<AnimalDashboard> conFecha = gestantes.stream().filter(a -> a.fechaUltimaInseminacion() != null).toList();

        List<SemanaPartos> semanas = new ArrayList<>();
        for (int w = 0; w < SEMANAS_PARTOS; w++) {
            LocalDate desde = hoy.plusWeeks(w);
            LocalDate hasta = desde.plusWeeks(1);
            semanas.add(SemanaPartos.builder()
                    .desde(desde)
                    .animales(resumenes(conFecha.stream()
                            .filter(a -> {
                                LocalDate parto = parto(a, umbrales);
                                return !parto.isBefore(desde) && parto.isBefore(hasta);
                            })
                            .toList()))
                    .build());
        }

        LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
        LocalDate finAnual = lunes.plusDays(DIAS_VISTA_ANUAL);
        Map<LocalDate, List<AnimalDashboard>> porFecha = new TreeMap<>();
        for (AnimalDashboard a : conFecha) {
            LocalDate parto = parto(a, umbrales);
            if (!parto.isBefore(lunes) && parto.isBefore(finAnual)) {
                porFecha.computeIfAbsent(parto, f -> new ArrayList<>()).add(a);
            }
        }

        return Partos.builder()
                .semanas(semanas)
                .porDia(porFecha.entrySet().stream()
                        .map(e -> DiaPartos.builder().fecha(e.getKey()).animales(resumenes(e.getValue())).build())
                        .toList())
                .gestantesSinFecha(gestantes.size() - conFecha.size())
                .vencidos(conFecha.stream().filter(a -> parto(a, umbrales).isBefore(hoy)).count())
                .totalGestantes(gestantes.size())
                .build();
    }

    private static LocalDate parto(AnimalDashboard a, UmbralesDashboardResponse umbrales) {
        return a.fechaUltimaInseminacion().plusDays(umbrales.getDiasGestacion());
    }

    // ── Desempeño reproductivo ───────────────────────────────────────────────

    static Desempenyo desempenyo(List<AnimalDashboard> animales, List<AlertaGenerada> generadas, int periodoDias,
            LocalDateTime ahora) {
        LocalDateTime desde = ahora.minusDays(periodoDias);
        List<AnimalDashboard> reproductivos = animales.stream()
                .filter(a -> DashboardDatos.esReproductiva(a, ahora))
                .toList();
        List<AlertaGenerada> celos = generadas.stream()
                .filter(g -> g.disparador() == DisparadorAlerta.CELO && !DashboardDatos.esAnestro(g.tipo()))
                .filter(g -> !g.tsGeneracion().isBefore(desde))
                .toList();
        List<AnimalDashboard> inseminadas = reproductivos.stream()
                .filter(a -> a.fechaUltimaInseminacion() != null
                        && !a.fechaUltimaInseminacion().isBefore(desde.toLocalDate()))
                .toList();
        return Desempenyo.builder()
                .reproductivos(reproductivos.size())
                .celosDetectados(celos.size())
                .celosAtendidos(celos.stream().filter(g -> g.estado() == EstadoAlerta.CONFIRMADA).count())
                .ventanasPerdidas(celos.stream().filter(DashboardDatos::ventanaPerdida).count())
                .inseminadas(inseminadas.size())
                .gestantes(inseminadas.stream()
                        .filter(a -> a.estadoReproductivo() == TipoReproductivo.GESTANTE)
                        .count())
                .build();
    }

    // ── Reincidencia ─────────────────────────────────────────────────────────

    static Reincidencia reincidencia(List<AlertaGenerada> generadas, Map<Long, AnimalDashboard> porId,
            int periodoDias, LocalDateTime ahora) {
        LocalDateTime desde = ahora.minusDays(periodoDias);
        List<AlertaGenerada> delPeriodo = generadas.stream().filter(g -> !g.tsGeneracion().isBefore(desde)).toList();

        Map<Long, Long> porAnimal = new LinkedHashMap<>();
        delPeriodo.forEach(g -> porAnimal.merge(g.animalId(), 1L, Long::sum));
        List<AnimalAlertas> animales = porAnimal.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_REINCIDENCIA)
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(MAX_REINCIDENTES)
                .map(e -> AnimalAlertas.builder()
                        .animal(DashboardDatos.resumen(porId.get(e.getKey())))
                        .alertas(e.getValue())
                        .build())
                .toList();

        Map<String, Long> porLote = new LinkedHashMap<>();
        delPeriodo.stream()
                .filter(g -> g.disparador() == DisparadorAlerta.FUERA_DE_PERIMETRO)
                .forEach(g -> {
                    AnimalDashboard animal = porId.get(g.animalId());
                    String lote = animal != null && animal.rodeoNombre() != null ? animal.rodeoNombre() : SIN_LOTE;
                    porLote.merge(lote, 1L, Long::sum);
                });
        List<LoteSalidas> lotes = porLote.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_REINCIDENCIA)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_LOTES_REINCIDENTES)
                .map(e -> LoteSalidas.builder().nombre(e.getKey()).salidas(e.getValue()).build())
                .toList();

        return Reincidencia.builder().animales(animales).lotes(lotes).build();
    }

    // ── Última intervención ──────────────────────────────────────────────────

    static UltimaIntervencion ultimaIntervencion(List<AnimalDashboard> animales,
            Map<Long, IntervencionDashboard> ultimaPorAnimal, Map<Long, AnimalDashboard> porId,
            LocalDateTime ahora) {
        IntervencionDashboard masReciente = animales.stream()
                .map(a -> ultimaPorAnimal.get(a.id()))
                .filter(i -> i != null)
                .max(Comparator.comparing(IntervencionDashboard::fechaHora))
                .orElse(null);

        // Los que nunca tuvieron primero, después de más a menos días; a igualdad, el orden de la lista.
        List<AnimalSinIntervencion> ranking = animales.stream()
                .map(a -> {
                    IntervencionDashboard ultima = ultimaPorAnimal.get(a.id());
                    return AnimalSinIntervencion.builder()
                            .animal(DashboardDatos.resumen(a))
                            .dias(ultima != null ? diasCompletos(ultima.fechaHora(), ahora) : null)
                            .build();
                })
                .sorted(Comparator.comparing((AnimalSinIntervencion s) -> s.getDias() != null)
                        .thenComparing(s -> s.getDias() == null ? 0L : -s.getDias()))
                .limit(MAX_SIN_INTERVENCION)
                .toList();

        return UltimaIntervencion.builder()
                .masReciente(masReciente == null ? null : SaludReproduccionResponse.Intervencion.builder()
                        .animal(DashboardDatos.resumen(porId.get(masReciente.animalId())))
                        .tipo(masReciente.tipo())
                        .fechaHora(masReciente.fechaHora())
                        .interventor(masReciente.interventor())
                        .build())
                .sinIntervencionHaceMas(ranking)
                .build();
    }

    // ── Comunes ──────────────────────────────────────────────────────────────

    private static long diasCompletos(LocalDateTime desde, LocalDateTime ahora) {
        return (long) Math.floor(DashboardDatos.diasDesde(desde, ahora));
    }

    private static List<AnimalResumenResponse> resumenes(List<AnimalDashboard> animales) {
        return animales.stream().map(DashboardDatos::resumen).toList();
    }
}
