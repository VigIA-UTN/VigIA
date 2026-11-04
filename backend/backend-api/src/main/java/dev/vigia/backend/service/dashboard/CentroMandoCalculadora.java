package dev.vigia.backend.service.dashboard;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.CentroMandoResponse;
import dev.vigia.backend.dto.CentroMandoResponse.AlertasPendientes;
import dev.vigia.backend.dto.CentroMandoResponse.AnimalResumen;
import dev.vigia.backend.dto.CentroMandoResponse.BajoObservacion;
import dev.vigia.backend.dto.CentroMandoResponse.Cobertura;
import dev.vigia.backend.dto.CentroMandoResponse.Embudo;
import dev.vigia.backend.dto.CentroMandoResponse.ItemCola;
import dev.vigia.backend.dto.CentroMandoResponse.ItemVentana;
import dev.vigia.backend.dto.CentroMandoResponse.Tendencia;
import dev.vigia.backend.dto.CentroMandoResponse.Umbrales;
import dev.vigia.backend.dto.CentroMandoResponse.VentanasAbiertas;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.TipoEstadoSalud;
import dev.vigia.backend.model.TipoReproductivo;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
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

    /** Estados en los que la vaca sigue abierta (sin preñez confirmada) desde el último parto. */
    private static final Set<TipoReproductivo> ESTADOS_ABIERTOS = EnumSet.of(TipoReproductivo.VACIA,
            TipoReproductivo.CELO, TipoReproductivo.POSIBLE_CELO, TipoReproductivo.INSEMINADA,
            TipoReproductivo.PARIDA, TipoReproductivo.ANESTRO);

    /** Orden de los segmentos del embudo. */
    private static final List<String> ORDEN_EMBUDO = List.of("VACIA", "CELO", "POSIBLE_CELO", "INSEMINADA",
            "GESTANTE", "PARIDA", "ANESTRO", "SIN_ESTADO");

    static final String TIPO_CARAVANA = "CARAVANA";

    private static final double MS_POR_HORA = 3_600_000.0;

    private CentroMandoCalculadora() {
    }

    /** Lo que los cálculos leen de cada animal activo. */
    public record AnimalDashboard(Long id, String idCaravanaOficial, String alias, Long rodeoId,
            LocalDate fechaNacimiento, LocalDate fechaAlta, boolean tieneCaravana, boolean enCalibracion,
            LocalDateTime ultimaSenial, Double bateria, TipoReproductivo estadoReproductivo,
            LocalDate fechaUltimoParto, LocalDate fechaUltimaInseminacion, TipoEstadoSalud estadoSalud) {
    }

    /** Una alerta generada en el período de la tendencia, en cualquier estado. */
    public record AlertaGenerada(Long animalId, DisparadorAlerta disparador, String tipo, LocalDateTime tsGeneracion,
            EstadoAlerta estado) {
    }

    public static CentroMandoResponse calcular(List<AnimalDashboard> animales, List<AlertaResponse> pendientes,
            Map<Long, VentanaInseminacionResponse> ventanasPorAlerta, List<AlertaGenerada> generadas,
            Umbrales umbrales, LocalDateTime ahora) {
        Map<Long, AnimalDashboard> porId = animales.stream()
                .collect(Collectors.toMap(AnimalDashboard::id, Function.identity()));

        List<ItemVentana> ventanas = ventanas(pendientes, ventanasPorAlerta, porId);
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
                .ventanasAbiertas(VentanasAbiertas.builder()
                        .total(ventanas.size())
                        .porVencer(ventanas.stream()
                                .filter(v -> "ACTIVA".equals(v.getVentana().getEstado())
                                        && v.getVentana().getTiempoRestanteSegundos()
                                                <= umbrales.getHorasVentanaPorVencer() * 3600L)
                                .count())
                        .perdidasUltimos30Dias(ventanasPerdidas(generadas, ahora))
                        .build())
                .bajoObservacion(BajoObservacion.builder()
                        .total(aRevisar.size())
                        .enfermos(aRevisar.stream().filter(a -> a.estadoSalud() == TipoEstadoSalud.ENFERMO).count())
                        .build())
                .cobertura(cobertura(animales, umbrales, ahora))
                .cola(cola(animales, pendientes, ventanasPorAlerta, porId, umbrales, ahora))
                .ventanas(ventanas)
                .embudo(embudo(animales, umbrales, ahora))
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
            Umbrales umbrales, LocalDateTime ahora) {
        List<ItemCola> items = new ArrayList<>();
        for (AlertaResponse alerta : pendientes) {
            items.add(ItemCola.builder()
                    .alerta(alerta)
                    .animal(resumen(porId.get(alerta.getAnimalId())))
                    .ventana(ventanasPorAlerta.get(alerta.getId()))
                    .build());
        }

        Set<Long> conAlerta = pendientes.stream().map(AlertaResponse::getAnimalId).collect(Collectors.toSet());
        for (AnimalDashboard animal : animales) {
            boolean silenciosa = !animal.tieneCaravana() || (animal.ultimaSenial() != null
                    && horasDesde(animal.ultimaSenial(), ahora) > umbrales.getHorasSinSenial());
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
                        .animal(resumen(animal))
                        .build());
            }
        }

        // Estable: a igual urgencia queda el orden de llegada (las pendientes, por severidad y antigüedad).
        return items.stream()
                .sorted(Comparator.comparingDouble(item -> urgencia(item, ahora)))
                .limit(umbrales.getColaMax())
                .toList();
    }

    /**
     * Menor es más urgente: primero los celos con la ventana abierta, por horas restantes; después las salidas de
     * perímetro, por antigüedad; después el resto, por severidad y antigüedad.
     */
    static double urgencia(ItemCola item, LocalDateTime ahora) {
        AlertaResponse alerta = item.getAlerta();
        VentanaInseminacionResponse ventana = item.getVentana();
        if (DisparadorAlerta.CELO.name().equals(alerta.getTipo()) && ventana != null
                && "ACTIVA".equals(ventana.getEstado())) {
            return ventana.getTiempoRestanteSegundos() / 3600.0;
        }
        double antiguedad = alerta.getTsGeneracion() != null ? horasDesde(alerta.getTsGeneracion(), ahora) : 0;
        if (DisparadorAlerta.FUERA_DE_PERIMETRO.name().equals(alerta.getTipo())) {
            return 24 + antiguedad;
        }
        int severidad = "ALTA".equals(alerta.getSeveridad()) ? 0 : "MEDIA".equals(alerta.getSeveridad()) ? 1 : 2;
        return 100 + severidad * 10 + antiguedad;
    }

    // ── Ventanas de inseminación ─────────────────────────────────────────────

    static List<ItemVentana> ventanas(List<AlertaResponse> pendientes,
            Map<Long, VentanaInseminacionResponse> ventanasPorAlerta, Map<Long, AnimalDashboard> porId) {
        return pendientes.stream()
                .filter(a -> DisparadorAlerta.CELO.name().equals(a.getTipo()))
                .filter(a -> ventanasPorAlerta.containsKey(a.getId()))
                .filter(a -> {
                    String estado = ventanasPorAlerta.get(a.getId()).getEstado();
                    return "ACTIVA".equals(estado) || "FUTURA".equals(estado);
                })
                .map(a -> ItemVentana.builder()
                        .alerta(a)
                        .animal(resumen(porId.get(a.getAnimalId())))
                        .ventana(ventanasPorAlerta.get(a.getId()))
                        .build())
                .sorted(Comparator.comparing((ItemVentana v) -> "FUTURA".equals(v.getVentana().getEstado()))
                        .thenComparingLong(v -> v.getVentana().getTiempoRestanteSegundos()))
                .toList();
    }

    /** Celos de los últimos 30 días archivados sin atender (los anestros también se archivan, pero sin ventana). */
    static long ventanasPerdidas(List<AlertaGenerada> generadas, LocalDateTime ahora) {
        LocalDateTime desde = ahora.minusDays(DIAS_VENTANAS_PERDIDAS);
        return generadas.stream()
                .filter(g -> g.disparador() == DisparadorAlerta.CELO && !esAnestro(g.tipo()))
                .filter(g -> g.estado() == EstadoAlerta.ARCHIVADA && !g.tsGeneracion().isBefore(desde))
                .count();
    }

    /** Celo pendiente con ventana de inseminación: el anestro también llega como CELO, pero no la tiene. */
    public static boolean esCeloConVentana(AlertaResponse alerta) {
        return DisparadorAlerta.CELO.name().equals(alerta.getTipo()) && !esAnestro(alerta.getSubtipo());
    }

    private static boolean esAnestro(String tipo) {
        return tipo != null && tipo.toLowerCase().contains("anestro");
    }

    // ── Cobertura ────────────────────────────────────────────────────────────

    static Cobertura cobertura(List<AnimalDashboard> animales, Umbrales umbrales, LocalDateTime ahora) {
        long ok = 0, calibrando = 0, sinSenial = 0, bateriaBaja = 0, sinCaravana = 0;
        for (AnimalDashboard a : animales) {
            if (!a.tieneCaravana()) {
                sinCaravana++;
            } else if (a.enCalibracion()) {
                calibrando++;
            } else if (a.ultimaSenial() == null
                    || horasDesde(a.ultimaSenial(), ahora) > umbrales.getHorasSinSenial()) {
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

    // ── Embudo reproductivo ──────────────────────────────────────────────────

    static Embudo embudo(List<AnimalDashboard> animales, Umbrales umbrales, LocalDateTime ahora) {
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
            if (a.estadoReproductivo() == TipoReproductivo.INSEMINADA && a.fechaUltimaInseminacion() != null
                    && diasDesde(a.fechaUltimaInseminacion(), ahora) > umbrales.getDiasDiagnosticoInseminada()) {
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

    /** De 1 año o más. */
    static boolean esReproductiva(AnimalDashboard animal, LocalDateTime ahora) {
        return animal.fechaNacimiento() != null && !animal.fechaNacimiento().plusYears(1).isAfter(ahora.toLocalDate());
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
                        .filter(g -> g.disparador() == tipo && !esAnestro(g.tipo()))
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

    private static AnimalResumen resumen(AnimalDashboard animal) {
        if (animal == null) {
            return null;
        }
        return AnimalResumen.builder()
                .id(animal.id())
                .idCaravanaOficial(animal.idCaravanaOficial())
                .alias(animal.alias())
                .build();
    }

    private static double horasDesde(LocalDateTime momento, LocalDateTime ahora) {
        return Duration.between(momento, ahora).toMillis() / MS_POR_HORA;
    }

    private static double diasDesde(LocalDate fecha, LocalDateTime ahora) {
        return horasDesde(fecha.atStartOfDay(), ahora) / 24;
    }
}
