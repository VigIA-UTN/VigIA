package dev.vigia.backend.service.dashboard;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.ResumenEjecutivoResponse;
import dev.vigia.backend.dto.ResumenEjecutivoResponse.Equipo;
import dev.vigia.backend.dto.ResumenEjecutivoResponse.FilaRodeo;
import dev.vigia.backend.dto.ResumenEjecutivoResponse.Promedio;
import dev.vigia.backend.dto.ResumenEjecutivoResponse.RangoAtencion;
import dev.vigia.backend.dto.UmbralesDashboardResponse;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.service.dashboard.DashboardDatos.AlertaGenerada;
import dev.vigia.backend.service.dashboard.DashboardDatos.AnimalDashboard;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Cálculos de la pestaña "Resumen ejecutivo" (H0045) sobre datos ya cargados de todo el establecimiento. No consulta
 * nada ni lee el reloj: recibe "ahora", así los tests fijan cada caso.
 */
public final class ResumenEjecutivoCalculadora {

    /** Rangos del tiempo de atención, en horas: [desde, hasta). */
    private static final List<Rango> RANGOS_ATENCION = List.of(
            new Rango("<6h", Double.NEGATIVE_INFINITY, 6),
            new Rango("6-24h", 6, 24),
            new Rango("1-3d", 24, 72),
            new Rango(">3d", 72, Double.POSITIVE_INFINITY));

    /** Sin rodeos con animales no hay contra qué comparar: valores de referencia para no destacar nada. */
    static final Promedio PROMEDIO_SIN_DATOS = Promedio.builder()
            .coberturaPorcentaje(80)
            .alertasCada100(10)
            .ventanasPerdidas(2)
            .vaciasLargasPorcentaje(15)
            .build();

    private ResumenEjecutivoCalculadora() {
    }

    private record Rango(String nombre, double desde, double hasta) {
    }

    /** Un rodeo activo del establecimiento. */
    public record RodeoDashboard(Long id, String nombre) {
    }

    /**
     * @param animales   los animales activos del establecimiento
     * @param pendientes las alertas pendientes del establecimiento
     * @param generadas  las alertas generadas desde el inicio del período o de la tendencia (el más antiguo)
     */
    public static ResumenEjecutivoResponse calcular(List<AnimalDashboard> animales, List<RodeoDashboard> rodeos,
            List<AlertaResponse> pendientes, List<AlertaGenerada> generadas, int periodoDias,
            UmbralesDashboardResponse umbrales, LocalDateTime ahora) {
        LocalDateTime desde = ahora.minusDays(periodoDias);
        List<AlertaGenerada> delPeriodo = generadas.stream().filter(g -> !g.tsGeneracion().isBefore(desde)).toList();
        List<FilaRodeo> filas = rodeos.stream()
                .map(r -> fila(r, animales, pendientes, delPeriodo, umbrales, ahora))
                .toList();

        return ResumenEjecutivoResponse.builder()
                .periodoDias(periodoDias)
                .rodeos(filas)
                .promedio(promedio(filas))
                .tendencia(CentroMandoCalculadora.tendencia(generadas, ahora))
                .equipo(equipo(pendientes, delPeriodo))
                .umbrales(umbrales)
                .build();
    }

    // ── Tabla por rodeo ──────────────────────────────────────────────────────

    static FilaRodeo fila(RodeoDashboard rodeo, List<AnimalDashboard> animales, List<AlertaResponse> pendientes,
            List<AlertaGenerada> delPeriodo, UmbralesDashboardResponse umbrales, LocalDateTime ahora) {
        List<AnimalDashboard> delRodeo = animales.stream().filter(a -> rodeo.id().equals(a.rodeoId())).toList();
        FilaRodeo.FilaRodeoBuilder fila = FilaRodeo.builder().id(rodeo.id()).nombre(rodeo.nombre());
        if (delRodeo.isEmpty()) {
            return fila.build();
        }
        Set<Long> ids = delRodeo.stream().map(AnimalDashboard::id).collect(Collectors.toSet());
        int total = delRodeo.size();

        long ok = CentroMandoCalculadora.cobertura(delRodeo, umbrales, ahora).getOk();
        long alertasPendientes = pendientes.stream().filter(a -> ids.contains(a.getAnimalId())).count();
        long ventanasPerdidas = delPeriodo.stream()
                .filter(g -> ids.contains(g.animalId()) && g.disparador() == DisparadorAlerta.CELO
                        && !DashboardDatos.esAnestro(g.tipo()) && DashboardDatos.ventanaPerdida(g))
                .count();
        List<AnimalDashboard> reproductivas = delRodeo.stream()
                .filter(a -> DashboardDatos.esReproductiva(a, ahora))
                .toList();
        long vaciasLargas = reproductivas.stream()
                .filter(a -> a.estadoReproductivo() == TipoReproductivo.VACIA && a.fechaUltimoParto() != null
                        && DashboardDatos.diasDesde(a.fechaUltimoParto(), ahora) > umbrales.getDiasVaciaLarga())
                .count();

        return fila.animales(total)
                .coberturaPorcentaje(porcentaje(ok, total))
                .alertasPendientes(alertasPendientes)
                .alertasCada100(porcentaje(alertasPendientes, total))
                .ventanasPerdidas(ventanasPerdidas)
                .vaciasLargasPorcentaje(porcentaje(vaciasLargas, reproductivas.size()))
                .build();
    }

    static Promedio promedio(List<FilaRodeo> filas) {
        List<FilaRodeo> conAnimales = filas.stream().filter(f -> f.getAnimales() > 0).toList();
        if (conAnimales.isEmpty()) {
            return PROMEDIO_SIN_DATOS;
        }
        return Promedio.builder()
                .coberturaPorcentaje(promedio(conAnimales, FilaRodeo::getCoberturaPorcentaje))
                .alertasCada100(promedio(conAnimales, FilaRodeo::getAlertasCada100))
                .ventanasPerdidas(promedio(conAnimales, f -> (int) f.getVentanasPerdidas()))
                .vaciasLargasPorcentaje(promedio(conAnimales, FilaRodeo::getVaciasLargasPorcentaje))
                .build();
    }

    private static int promedio(List<FilaRodeo> filas, ToIntFunction<FilaRodeo> valor) {
        return (int) Math.round(filas.stream().mapToInt(valor).sum() / (double) filas.size());
    }

    // ── ¿Está actuando el equipo? ────────────────────────────────────────────

    /**
     * Sobre las alertas generadas en el período. Los celos que perdieron la ventana cuentan como no atendidos; los
     * anestros archivados se cerraron solos al registrarse un ciclo, así que no son trabajo del equipo.
     */
    static Equipo equipo(List<AlertaResponse> pendientes, List<AlertaGenerada> delPeriodo) {
        List<AlertaGenerada> generadas = delPeriodo.stream()
                .filter(g -> !(g.estado() == EstadoAlerta.ARCHIVADA && DashboardDatos.esAnestro(g.tipo())))
                .toList();
        List<AlertaGenerada> resueltas = generadas.stream()
                .filter(g -> g.estado() == EstadoAlerta.CONFIRMADA || g.estado() == EstadoAlerta.DESCARTADA)
                .toList();
        long descartadas = resueltas.stream().filter(g -> g.estado() == EstadoAlerta.DESCARTADA).count();

        // Horas desde que se generó hasta que se resolvió.
        List<Double> tiempos = resueltas.stream()
                .filter(g -> g.tsConfirmacion() != null)
                .map(g -> DashboardDatos.horasDesde(g.tsGeneracion(), g.tsConfirmacion()))
                .toList();
        List<RangoAtencion> rangos = RANGOS_ATENCION.stream()
                .map(r -> RangoAtencion.builder()
                        .rango(r.nombre())
                        .alertas(tiempos.stream().filter(h -> h >= r.desde() && h < r.hasta()).count())
                        .build())
                .toList();

        return Equipo.builder()
                .enCola(pendientes.size())
                .porcentajeAtendidas(porcentaje(resueltas.size(), generadas.size()))
                .porcentajeDescartadas(porcentaje(descartadas, resueltas.size()))
                .totalAtendidas(tiempos.size())
                .tiempoAtencion(rangos)
                .build();
    }

    private static int porcentaje(long parte, long total) {
        return total == 0 ? 0 : (int) Math.round(100.0 * parte / total);
    }
}
