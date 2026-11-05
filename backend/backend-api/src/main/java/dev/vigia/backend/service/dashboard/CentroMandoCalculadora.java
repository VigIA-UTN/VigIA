package dev.vigia.backend.service.dashboard;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.CentroMandoResponse;
import dev.vigia.backend.dto.CentroMandoResponse.AlertasPendientes;
import dev.vigia.backend.dto.CentroMandoResponse.BajoObservacion;
import dev.vigia.backend.dto.CentroMandoResponse.Cobertura;
import dev.vigia.backend.dto.CentroMandoResponse.Embudo;
import dev.vigia.backend.dto.CentroMandoResponse.ItemCola;
import dev.vigia.backend.dto.CentroMandoResponse.Tendencia;
import dev.vigia.backend.dto.CentroMandoResponse.VentanasAbiertas;
import dev.vigia.backend.dto.UmbralesDashboardResponse;
import dev.vigia.backend.dto.VentanaAlertaResponse;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.service.dashboard.DashboardDatos.AlertaGenerada;
import dev.vigia.backend.service.dashboard.DashboardDatos.AnimalDashboard;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.TipoEstadoSalud;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cálculos de la pestaña "Centro de mando" (H0043) sobre datos ya cargados y filtrados por rodeo. No consulta nada ni
 * lee el reloj: recibe "ahora", así los tests fijan cada caso.
 */
public final class CentroMandoCalculadora {

    /** Tipos de la tendencia, en el orden en que se grafican. */
    public static final List<DisparadorAlerta> TIPOS_TENDENCIA = List.of(DisparadorAlerta.CELO,
            DisparadorAlerta.SALUD, DisparadorAlerta.FUERA_DE_PERIMETRO);

    static final int SEMANAS_TENDENCIA = 8;

    static final int DIAS_VENTANAS_PERDIDAS = 30;

    static final String TIPO_CARAVANA = "CARAVANA";

    private static final String ACTIVA = "ACTIVA";

    // Grupos de la cola de atención, del más urgente al menos.
    private static final int GRUPO_CELO_ABIERTO = 0;
    private static final int GRUPO_PERIMETRO = 1;
    private static final int GRUPO_RESTO = 2;

    private CentroMandoCalculadora() {
    }

    public static CentroMandoResponse calcular(List<AnimalDashboard> animales, List<AlertaResponse> pendientes,
            Map<Long, VentanaInseminacionResponse> ventanasPorAlerta, List<AlertaGenerada> generadas,
            UmbralesDashboardResponse umbrales, LocalDateTime ahora) {
        Map<Long, AnimalDashboard> porId = animales.stream()
                .collect(Collectors.toMap(AnimalDashboard::id, Function.identity()));

        List<VentanaAlertaResponse> ventanas = DashboardDatos.ventanas(pendientes, ventanasPorAlerta, porId);
        List<AnimalDashboard> aRevisar = animales.stream()
                .filter(a -> a.estadoSalud() == TipoEstadoSalud.ENFERMO
                        || a.estadoSalud() == TipoEstadoSalud.EN_OBSERVACION)
                .toList();

        return CentroMandoResponse.builder()
                .alertasPendientes(AlertasPendientes.builder()
                        .total(pendientes.size())
                        .celo(contarTipo(pendientes, DisparadorAlerta.CELO))
                        .salud(contarTipo(pendientes, DisparadorAlerta.SALUD))
                        .perimetro(contarTipo(pendientes, DisparadorAlerta.FUERA_DE_PERIMETRO))
                        .build())
                // La lista trae también las que están por abrir; el indicador cuenta solo las abiertas.
                .ventanasAbiertas(VentanasAbiertas.builder()
                        .total(ventanas.stream().filter(v -> ACTIVA.equals(v.getVentana().getEstado())).count())
                        .porVencer(DashboardDatos.porVencer(ventanas, umbrales))
                        .perdidasUltimos30Dias(ventanasPerdidas(generadas, ahora))
                        .build())
                .bajoObservacion(BajoObservacion.builder()
                        .total(aRevisar.size())
                        .enfermos(aRevisar.stream().filter(a -> a.estadoSalud() == TipoEstadoSalud.ENFERMO).count())
                        .build())
                .cobertura(cobertura(animales, umbrales, ahora))
                .cola(cola(animales, pendientes, ventanasPorAlerta, porId, umbrales, ahora))
                .ventanas(ventanas)
                .embudo(DashboardDatos.embudo(animales, umbrales, ahora))
                .tendencia(tendencia(generadas, ahora))
                .umbrales(umbrales)
                .build();
    }

    // ── Cola de atención ─────────────────────────────────────────────────────

    /**
     * Las alertas pendientes, más una entrada por cada animal sin caravana o cuya caravana no transmite hace más del
     * umbral (si no tiene ya una alerta), de la más urgente a la menos.
     */
    static List<ItemCola> cola(List<AnimalDashboard> animales, List<AlertaResponse> pendientes,
            Map<Long, VentanaInseminacionResponse> ventanasPorAlerta, Map<Long, AnimalDashboard> porId,
            UmbralesDashboardResponse umbrales, LocalDateTime ahora) {
        List<ItemCola> items = new ArrayList<>();
        for (AlertaResponse alerta : pendientes) {
            items.add(ItemCola.builder()
                    .alerta(alerta)
                    .animal(DashboardDatos.resumen(porId.get(alerta.getAnimalId())))
                    .ventana(ventanasPorAlerta.get(alerta.getId()))
                    .build());
        }

        Set<Long> conAlerta = pendientes.stream().map(AlertaResponse::getAnimalId).collect(Collectors.toSet());
        for (AnimalDashboard animal : animales) {
            boolean silenciosa = !animal.tieneCaravana() || (animal.ultimaSenial() != null
                    && DashboardDatos.horasDesde(animal.ultimaSenial(), ahora) > umbrales.getHorasSinSenial());
            if (silenciosa && !conAlerta.contains(animal.id())) {
                items.add(ItemCola.builder()
                        .alerta(AlertaResponse.builder()
                                .id(-animal.id())
                                .animalId(animal.id())
                                .tipo(TIPO_CARAVANA)
                                .estado(EstadoAlerta.PENDIENTE)
                                .tsGeneracion(animal.ultimaSenial() != null ? animal.ultimaSenial()
                                        : animal.fechaAlta() != null ? animal.fechaAlta().atStartOfDay() : null)
                                .build())
                        .animal(DashboardDatos.resumen(animal))
                        .build());
            }
        }

        return items.stream()
                .sorted(POR_URGENCIA)
                .limit(umbrales.getColaMax())
                .toList();
    }

    /**
     * De la más urgente a la menos: primero los celos con la ventana abierta, por tiempo restante; después las salidas
     * de perímetro; después el resto, por severidad. Dentro de cada grupo, la que espera hace más tiempo va antes (un
     * celo con la ventana todavía cerrada no se puede atender: va con el resto).
     */
    static final Comparator<ItemCola> POR_URGENCIA = Comparator
            .comparingInt(CentroMandoCalculadora::grupo)
            .thenComparingLong(item -> grupo(item) == GRUPO_CELO_ABIERTO
                    ? item.getVentana().getTiempoRestanteSegundos() : 0)
            .thenComparingInt(item -> grupo(item) == GRUPO_RESTO ? severidad(item.getAlerta()) : 0)
            .thenComparing(item -> item.getAlerta().getTsGeneracion(),
                    Comparator.nullsLast(Comparator.naturalOrder()));

    private static int grupo(ItemCola item) {
        String tipo = item.getAlerta().getTipo();
        VentanaInseminacionResponse ventana = item.getVentana();
        if (DisparadorAlerta.CELO.name().equals(tipo) && ventana != null && ACTIVA.equals(ventana.getEstado())) {
            return GRUPO_CELO_ABIERTO;
        }
        return DisparadorAlerta.FUERA_DE_PERIMETRO.name().equals(tipo) ? GRUPO_PERIMETRO : GRUPO_RESTO;
    }

    /** ALTA primero; sin severidad (la caravana que no transmite) cuenta como BAJA. */
    private static int severidad(AlertaResponse alerta) {
        return "ALTA".equals(alerta.getSeveridad()) ? 0 : "MEDIA".equals(alerta.getSeveridad()) ? 1 : 2;
    }

    /** Celos de los últimos 30 días cuya ventana se perdió (los anestros también se archivan, pero sin ventana). */
    static long ventanasPerdidas(List<AlertaGenerada> generadas, LocalDateTime ahora) {
        LocalDateTime desde = ahora.minusDays(DIAS_VENTANAS_PERDIDAS);
        return generadas.stream()
                .filter(g -> g.disparador() == DisparadorAlerta.CELO && !DashboardDatos.esAnestro(g.tipo()))
                .filter(g -> DashboardDatos.ventanaPerdida(g) && !g.tsGeneracion().isBefore(desde))
                .count();
    }

    // ── Cobertura ────────────────────────────────────────────────────────────

    static Cobertura cobertura(List<AnimalDashboard> animales, UmbralesDashboardResponse umbrales,
            LocalDateTime ahora) {
        long ok = 0, calibrando = 0, sinSenial = 0, bateriaBaja = 0, sinCaravana = 0;
        for (AnimalDashboard a : animales) {
            if (!a.tieneCaravana()) {
                sinCaravana++;
            } else if (a.enCalibracion()) {
                calibrando++;
            } else if (a.ultimaSenial() == null
                    || DashboardDatos.horasDesde(a.ultimaSenial(), ahora) > umbrales.getHorasSinSenial()) {
                sinSenial++;
            } else if (a.bateria() != null && a.bateria() <= umbrales.getBateriaBajaPorcentaje()) {
                bateriaBaja++;
            } else {
                ok++;
            }
        }
        int total = animales.size();
        return Cobertura.builder()
                .total(total)
                .ok(ok)
                .calibrando(calibrando)
                .sinSenial(sinSenial)
                .bateriaBaja(bateriaBaja)
                .sinCaravana(sinCaravana)
                .porcentaje(total == 0 ? 0 : (int) Math.round(100.0 * ok / total))
                .build();
    }

    // ── Tendencia ────────────────────────────────────────────────────────────

    /**
     * Desde cuándo hacen falta las alertas generadas: el lunes de la primera de las 8 semanas (cubre también los 30
     * días de las ventanas perdidas).
     */
    public static LocalDateTime inicioTendencia(LocalDateTime ahora) {
        return ahora.toLocalDate().with(DayOfWeek.MONDAY).minusWeeks(SEMANAS_TENDENCIA - 1L).atStartOfDay();
    }

    static Tendencia tendencia(List<AlertaGenerada> generadas, LocalDateTime ahora) {
        List<String> semanas = new ArrayList<>();
        for (int i = SEMANAS_TENDENCIA - 1; i >= 0; i--) {
            semanas.add(semanaIso(ahora.minusWeeks(i)));
        }
        Map<String, List<Long>> datos = new LinkedHashMap<>();
        for (DisparadorAlerta tipo : TIPOS_TENDENCIA) {
            List<Long> porSemana = new ArrayList<>();
            for (String semana : semanas) {
                porSemana.add(generadas.stream()
                        .filter(g -> g.disparador() == tipo && !DashboardDatos.esAnestro(g.tipo()))
                        .filter(g -> semana.equals(semanaIso(g.tsGeneracion())))
                        .count());
            }
            datos.put(tipo.name(), porSemana);
        }
        return Tendencia.builder()
                .semanas(semanas)
                .tipos(TIPOS_TENDENCIA.stream().map(Enum::name).toList())
                .datos(datos)
                .build();
    }

    /** Semana ISO 8601 ({@code 2026-W45}): de lunes a domingo, la 1 es la que tiene el primer jueves del año. */
    static String semanaIso(LocalDateTime momento) {
        return String.format("%d-W%02d", momento.get(IsoFields.WEEK_BASED_YEAR),
                momento.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    // ── Comunes ──────────────────────────────────────────────────────────────

    private static long contarTipo(List<AlertaResponse> alertas, DisparadorAlerta disparador) {
        return alertas.stream().filter(a -> disparador.name().equals(a.getTipo())).count();
    }
}
