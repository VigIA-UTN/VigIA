package dev.vigia.backend.service.dashboard;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.CentroMandoResponse;
import dev.vigia.backend.dto.CentroMandoResponse.ItemCola;
import dev.vigia.backend.dto.UmbralesDashboardResponse;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.TipoEstadoSalud;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.service.dashboard.DashboardDatos.AlertaGenerada;
import dev.vigia.backend.service.dashboard.DashboardDatos.AnimalDashboard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CentroMandoCalculadora")
class CentroMandoCalculadoraTest {

    /** Miércoles 4 de noviembre de 2026 a las 12:00 (semana ISO 2026-W45). */
    static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 4, 12, 0);
    static final LocalDate HOY = AHORA.toLocalDate();

    static final UmbralesDashboardResponse UMBRALES = UmbralesDashboardResponse.builder()
            .horasSinSenial(6)
            .bateriaBajaPorcentaje(20)
            .horasVentanaPorVencer(3)
            .diasDiagnosticoInseminada(35)
            .colaMax(8)
            .horasSaludSinAtender(24)
            .diasVaciaLarga(90)
            .diasGestacion(283)
            .build();

    // ── Armado de datos ──────────────────────────────────────────────────────

    /** Vaca adulta monitoreada: caravana con señal hace 10 min, batería 80 %, sin estados. */
    static AnimalDashboard vaca(long id) {
        return new AnimalDashboard(id, "A-" + id, "Vaca " + id, 1L, "Rodeo 1", HOY.minusYears(3), HOY.minusYears(1), true,
                false, AHORA.minusMinutes(10), 80.0, null, null, null, null);
    }

    static AnimalDashboard conCaravana(AnimalDashboard a, boolean tiene, boolean calibrando,
            LocalDateTime ultimaSenial, Double bateria) {
        return new AnimalDashboard(a.id(), a.idCaravanaOficial(), a.alias(), a.rodeoId(), a.rodeoNombre(), a.fechaNacimiento(),
                a.fechaAlta(), tiene, calibrando, ultimaSenial, bateria, a.estadoReproductivo(), a.fechaUltimoParto(),
                a.fechaUltimaInseminacion(), a.estadoSalud());
    }

    static AnimalDashboard conReproductivo(AnimalDashboard a, TipoReproductivo estado, LocalDate parto,
            LocalDate inseminacion) {
        return new AnimalDashboard(a.id(), a.idCaravanaOficial(), a.alias(), a.rodeoId(), a.rodeoNombre(), a.fechaNacimiento(),
                a.fechaAlta(), a.tieneCaravana(), a.enCalibracion(), a.ultimaSenial(), a.bateria(), estado, parto,
                inseminacion, a.estadoSalud());
    }

    static AnimalDashboard conSalud(AnimalDashboard a, TipoEstadoSalud salud) {
        return new AnimalDashboard(a.id(), a.idCaravanaOficial(), a.alias(), a.rodeoId(), a.rodeoNombre(), a.fechaNacimiento(),
                a.fechaAlta(), a.tieneCaravana(), a.enCalibracion(), a.ultimaSenial(), a.bateria(),
                a.estadoReproductivo(), a.fechaUltimoParto(), a.fechaUltimaInseminacion(), salud);
    }

    static AnimalDashboard nacida(AnimalDashboard a, LocalDate nacimiento) {
        return new AnimalDashboard(a.id(), a.idCaravanaOficial(), a.alias(), a.rodeoId(), a.rodeoNombre(), nacimiento, a.fechaAlta(),
                a.tieneCaravana(), a.enCalibracion(), a.ultimaSenial(), a.bateria(), a.estadoReproductivo(),
                a.fechaUltimoParto(), a.fechaUltimaInseminacion(), a.estadoSalud());
    }

    static AlertaResponse alerta(long id, long animalId, DisparadorAlerta tipo, String severidad,
            LocalDateTime generada) {
        return AlertaResponse.builder()
                .id(id)
                .animalId(animalId)
                .tipo(tipo.name())
                .subtipo(tipo.name())
                .severidad(severidad)
                .estado(EstadoAlerta.PENDIENTE)
                .tsGeneracion(generada)
                .build();
    }

    static VentanaInseminacionResponse ventana(String estado, long segundosRestantes) {
        return VentanaInseminacionResponse.builder()
                .estado(estado)
                .tiempoRestanteSegundos(segundosRestantes)
                .porcentajeTranscurrido(estado.equals("ACTIVA") ? 50.0 : 0.0)
                .proximaAVencer(false)
                .build();
    }

    private static CentroMandoResponse calcular(List<AnimalDashboard> animales, List<AlertaResponse> pendientes,
            Map<Long, VentanaInseminacionResponse> ventanas, List<AlertaGenerada> generadas) {
        return CentroMandoCalculadora.calcular(animales, pendientes, ventanas, generadas, UMBRALES, AHORA);
    }

    // ── Indicadores ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Alertas pendientes por tipo, ventanas abiertas y animales en observación")
    void indicadores() {
        List<AnimalDashboard> animales = List.of(
                vaca(1), vaca(2), conSalud(vaca(3), TipoEstadoSalud.ENFERMO),
                conSalud(vaca(4), TipoEstadoSalud.EN_OBSERVACION), conSalud(vaca(5), TipoEstadoSalud.SANO));
        List<AlertaResponse> pendientes = List.of(
                alerta(10, 1, DisparadorAlerta.CELO, "ALTA", AHORA.minusHours(8)),
                alerta(11, 2, DisparadorAlerta.CELO, "ALTA", AHORA.minusHours(1)),
                alerta(12, 3, DisparadorAlerta.SALUD, "MEDIA", AHORA.minusHours(2)),
                alerta(13, 4, DisparadorAlerta.FUERA_DE_PERIMETRO, "ALTA", AHORA.minusMinutes(30)),
                alerta(14, 5, DisparadorAlerta.AISLAMIENTO_SOCIAL, "ALTA", AHORA.minusHours(2)));
        Map<Long, VentanaInseminacionResponse> ventanas = Map.of(
                10L, ventana("ACTIVA", 2 * 3600),
                11L, ventana("FUTURA", 5 * 3600));

        CentroMandoResponse r = calcular(animales, pendientes, ventanas, List.of());

        assertThat(r.getAlertasPendientes().getTotal()).isEqualTo(5);
        assertThat(r.getAlertasPendientes().getCelo()).isEqualTo(2);
        assertThat(r.getAlertasPendientes().getSalud()).isEqualTo(1);
        assertThat(r.getAlertasPendientes().getPerimetro()).isEqualTo(1);
        assertThat(r.getVentanasAbiertas().getTotal()).isEqualTo(2);
        assertThat(r.getVentanasAbiertas().getPorVencer()).isEqualTo(1);
        assertThat(r.getBajoObservacion().getTotal()).isEqualTo(2);
        assertThat(r.getBajoObservacion().getEnfermos()).isEqualTo(1);
        assertThat(r.getUmbrales()).isEqualTo(UMBRALES);
    }

    // ── Cola de atención ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cola de atención")
    class Cola {

        @Test
        @DisplayName("Celos por tiempo restante de su ventana, después perímetro, después el resto por severidad")
        void ordenPorUrgencia() {
            List<AnimalDashboard> animales = List.of(vaca(1), vaca(2), vaca(3), vaca(4), vaca(5), vaca(6));
            List<AlertaResponse> pendientes = List.of(
                    alerta(10, 1, DisparadorAlerta.SALUD, "BAJA", AHORA.minusHours(30)),
                    alerta(11, 2, DisparadorAlerta.SALUD, "ALTA", AHORA.minusHours(3)),
                    alerta(12, 3, DisparadorAlerta.FUERA_DE_PERIMETRO, "ALTA", AHORA.minusHours(2)),
                    alerta(13, 4, DisparadorAlerta.CELO, "ALTA", AHORA.minusHours(12)),
                    alerta(14, 5, DisparadorAlerta.CELO, "ALTA", AHORA.minusHours(20)),
                    // Celo con la ventana por abrir: va con el resto, por severidad.
                    alerta(15, 6, DisparadorAlerta.CELO, "ALTA", AHORA.minusHours(1)));
            Map<Long, VentanaInseminacionResponse> ventanas = Map.of(
                    13L, ventana("ACTIVA", 12 * 3600),
                    14L, ventana("ACTIVA", 4 * 3600),
                    15L, ventana("FUTURA", 5 * 3600));

            List<ItemCola> cola = calcular(animales, pendientes, ventanas, List.of()).getCola();

            assertThat(cola).extracting(i -> i.getAlerta().getId()).containsExactly(14L, 13L, 12L, 15L, 11L, 10L);
            assertThat(cola.get(0).getVentana().getTiempoRestanteSegundos()).isEqualTo(4 * 3600);
            assertThat(cola.get(0).getAnimal().getIdCaravanaOficial()).isEqualTo("A-5");
            assertThat(cola.get(2).getVentana()).isNull();
        }

        @Test
        @DisplayName("Caravanas sin señal o animales sin caravana entran como CARAVANA si no tienen otra alerta")
        void caravanasSilenciosas() {
            List<AnimalDashboard> animales = List.of(
                    vaca(1),
                    conCaravana(vaca(2), false, false, null, null),
                    conCaravana(vaca(3), true, false, AHORA.minusHours(7), 80.0),
                    // Sin señal pero ya tiene una alerta: no se duplica.
                    conCaravana(vaca(4), true, false, AHORA.minusHours(9), 80.0),
                    // Caravana que nunca transmitió: no entra (no hay desde cuándo contar).
                    conCaravana(vaca(5), true, true, null, null));
            List<AlertaResponse> pendientes = List.of(alerta(10, 4, DisparadorAlerta.SALUD, "MEDIA", AHORA));

            List<ItemCola> cola = calcular(animales, pendientes, Map.of(), List.of()).getCola();

            assertThat(cola).extracting(i -> i.getAlerta().getId()).containsExactlyInAnyOrder(10L, -2L, -3L);
            ItemCola sinSenial = cola.stream().filter(i -> i.getAlerta().getId() == -3L).findFirst().orElseThrow();
            assertThat(sinSenial.getAlerta().getTipo()).isEqualTo("CARAVANA");
            assertThat(sinSenial.getAlerta().getAnimalId()).isEqualTo(3L);
            assertThat(sinSenial.getAlerta().getTsGeneracion()).isEqualTo(AHORA.minusHours(7));
            ItemCola sinCaravana = cola.stream().filter(i -> i.getAlerta().getId() == -2L).findFirst().orElseThrow();
            assertThat(sinCaravana.getAlerta().getTsGeneracion()).isEqualTo(HOY.minusYears(1).atStartOfDay());
        }

        @Test
        @DisplayName("Hasta 8 ítems, y la alerta de un animal que no está en la lista va sin animal")
        void limiteYAnimalDesconocido() {
            List<AlertaResponse> pendientes = new java.util.ArrayList<>();
            for (long i = 1; i <= 10; i++) {
                pendientes.add(alerta(i, 99, DisparadorAlerta.SALUD, "MEDIA", AHORA.minusHours(i)));
            }

            List<ItemCola> cola = calcular(List.of(), pendientes, Map.of(), List.of()).getCola();

            assertThat(cola).hasSize(8);
            assertThat(cola.get(0).getAlerta().getId()).isEqualTo(1L);
            assertThat(cola.get(0).getAnimal()).isNull();
        }
    }

    // ── Ventanas ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ventanas: primero las abiertas y después las por abrir, cada grupo por tiempo restante")
    void ordenDeVentanas() {
        List<AnimalDashboard> animales = List.of(vaca(1), vaca(2), vaca(3), vaca(4));
        List<AlertaResponse> pendientes = List.of(
                alerta(10, 1, DisparadorAlerta.CELO, "ALTA", AHORA),
                alerta(11, 2, DisparadorAlerta.CELO, "ALTA", AHORA),
                alerta(12, 3, DisparadorAlerta.CELO, "ALTA", AHORA),
                alerta(13, 4, DisparadorAlerta.CELO, "ALTA", AHORA));
        Map<Long, VentanaInseminacionResponse> ventanas = Map.of(
                10L, ventana("FUTURA", 3600),
                11L, ventana("ACTIVA", 10 * 3600),
                12L, ventana("ACTIVA", 3600),
                13L, ventana("VENCIDA", 0));

        CentroMandoResponse r = calcular(animales, pendientes, ventanas, List.of());

        assertThat(r.getVentanas()).extracting(v -> v.getAlerta().getId()).containsExactly(12L, 11L, 10L);
        assertThat(r.getVentanasAbiertas().getTotal()).isEqualTo(3);
    }

    @Test
    @DisplayName("Un anestro llega como CELO pero no tiene ventana")
    void anestroSinVentana() {
        AlertaResponse anestro = alerta(10, 1, DisparadorAlerta.CELO, "MEDIA", AHORA);
        anestro.setSubtipo("Anestro Detectado");

        assertThat(DashboardDatos.esCeloConVentana(anestro)).isFalse();
        assertThat(DashboardDatos.esCeloConVentana(alerta(11, 1, DisparadorAlerta.CELO, "ALTA", AHORA)))
                .isTrue();
        assertThat(DashboardDatos.esCeloConVentana(alerta(12, 1, DisparadorAlerta.SALUD, "ALTA", AHORA)))
                .isFalse();
    }

    // ── Cobertura ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cobertura: cada animal en un solo grupo, en orden de prioridad")
    void cobertura() {
        List<AnimalDashboard> animales = List.of(
                vaca(1),
                vaca(2),
                conCaravana(vaca(3), false, false, null, null),
                // Calibrando aunque no tenga señal: gana calibración.
                conCaravana(vaca(4), true, true, AHORA.minusHours(10), 10.0),
                conCaravana(vaca(5), true, false, AHORA.minusHours(7), 80.0),
                conCaravana(vaca(6), true, false, null, null),
                // Sin señal y con batería baja: gana sin señal.
                conCaravana(vaca(7), true, false, AHORA.minusHours(8), 5.0),
                conCaravana(vaca(8), true, false, AHORA.minusMinutes(5), 20.0),
                // Batería desconocida: no cuenta como baja.
                conCaravana(vaca(9), true, false, AHORA.minusMinutes(5), null));

        CentroMandoResponse.Cobertura c = calcular(animales, List.of(), Map.of(), List.of()).getCobertura();

        assertThat(c.getTotal()).isEqualTo(9);
        assertThat(c.getSinCaravana()).isEqualTo(1);
        assertThat(c.getCalibrando()).isEqualTo(1);
        assertThat(c.getSinSenial()).isEqualTo(3);
        assertThat(c.getBateriaBaja()).isEqualTo(1);
        assertThat(c.getOk()).isEqualTo(3);
        assertThat(c.getPorcentaje()).isEqualTo(33);
    }

    @Test
    @DisplayName("Cobertura sin animales: 0 %")
    void coberturaVacia() {
        assertThat(calcular(List.of(), List.of(), Map.of(), List.of()).getCobertura().getPorcentaje()).isZero();
    }

    // ── Embudo ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Embudo: distribución de las de 1 año o más, días abiertos e inseminadas sin diagnóstico")
    void embudo() {
        List<AnimalDashboard> animales = List.of(
                conReproductivo(vaca(1), TipoReproductivo.VACIA, HOY.minusDays(100), null),
                conReproductivo(vaca(2), TipoReproductivo.PARIDA, HOY.minusDays(50), null),
                // Gestante: no está abierta aunque tenga parto.
                conReproductivo(vaca(3), TipoReproductivo.GESTANTE, HOY.minusDays(300), HOY.minusDays(60)),
                conReproductivo(vaca(4), TipoReproductivo.INSEMINADA, null, HOY.minusDays(40)),
                conReproductivo(vaca(5), TipoReproductivo.INSEMINADA, null, HOY.minusDays(20)),
                vaca(6),
                // Ternera de 6 meses: no entra en el embudo.
                nacida(conReproductivo(vaca(7), TipoReproductivo.VACIA, null, null), HOY.minusMonths(6)),
                // Cumple 1 año hoy: entra.
                nacida(vaca(8), HOY.minusYears(1)));

        CentroMandoResponse.Embudo e = calcular(animales, List.of(), Map.of(), List.of()).getEmbudo();

        assertThat(e.getTotalReproductivos()).isEqualTo(7);
        assertThat(e.getDistribucion()).containsExactly(
                Map.entry("VACIA", 1L), Map.entry("CELO", 0L), Map.entry("POSIBLE_CELO", 0L),
                Map.entry("INSEMINADA", 2L), Map.entry("GESTANTE", 1L), Map.entry("PARIDA", 1L),
                Map.entry("ANESTRO", 0L), Map.entry("SIN_ESTADO", 2L));
        // (100,5 + 50,5) / 2: el día de hoy va hasta las 12.
        assertThat(e.getVacasAbiertas()).isEqualTo(2);
        assertThat(e.getDiasAbiertosPromedio()).isEqualTo(76);
        assertThat(e.getInseminadasSinDiagnostico()).isEqualTo(1);
    }

    // ── Tendencia ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Tendencia: 8 semanas ISO hasta la actual, por tipo y sin anestros")
    void tendencia() {
        EstadoAlerta p = EstadoAlerta.PENDIENTE;
        List<AlertaGenerada> generadas = List.of(
                new AlertaGenerada(1L, DisparadorAlerta.CELO, "Celo Detectado", AHORA.minusHours(1), p),
                new AlertaGenerada(1L, DisparadorAlerta.CELO, "Celo Detectado", LocalDateTime.of(2026, 11, 2, 0, 0),
                        EstadoAlerta.ARCHIVADA),
                new AlertaGenerada(1L, DisparadorAlerta.CELO, "Anestro Detectado", AHORA.minusHours(2), p),
                // Domingo: semana anterior.
                new AlertaGenerada(2L, DisparadorAlerta.SALUD, "SALUD", LocalDateTime.of(2026, 11, 1, 23, 59), p),
                new AlertaGenerada(3L, DisparadorAlerta.FUERA_DE_PERIMETRO, "Fuera de Perímetro",
                        LocalDateTime.of(2026, 9, 14, 8, 0), p),
                // Antes de la primera semana: no entra.
                new AlertaGenerada(3L, DisparadorAlerta.FUERA_DE_PERIMETRO, "Fuera de Perímetro",
                        LocalDateTime.of(2026, 9, 13, 23, 0), p));

        CentroMandoResponse.Tendencia t = calcular(List.of(), List.of(), Map.of(), generadas).getTendencia();

        assertThat(t.getSemanas()).containsExactly("2026-W38", "2026-W39", "2026-W40", "2026-W41", "2026-W42",
                "2026-W43", "2026-W44", "2026-W45");
        assertThat(t.getTipos()).containsExactly("CELO", "SALUD", "FUERA_DE_PERIMETRO");
        assertThat(t.getDatos().get("CELO")).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 0L, 2L);
        assertThat(t.getDatos().get("SALUD")).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 1L, 0L);
        assertThat(t.getDatos().get("FUERA_DE_PERIMETRO")).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        assertThat(CentroMandoCalculadora.inicioTendencia(AHORA)).isEqualTo(LocalDateTime.of(2026, 9, 14, 0, 0));
    }

    @Test
    @DisplayName("Ventanas perdidas: celos archivados o vencidos de los últimos 30 días, sin anestros")
    void ventanasPerdidas() {
        List<AlertaGenerada> generadas = List.of(
                new AlertaGenerada(1L, DisparadorAlerta.CELO, "Celo Detectado", AHORA.minusDays(29),
                        EstadoAlerta.ARCHIVADA),
                new AlertaGenerada(2L, DisparadorAlerta.CELO, "Celo Detectado", AHORA.minusDays(1),
                        EstadoAlerta.ARCHIVADA),
                // Fuera del período, atendido o anestro: no cuentan.
                new AlertaGenerada(3L, DisparadorAlerta.CELO, "Celo Detectado", AHORA.minusDays(31),
                        EstadoAlerta.ARCHIVADA),
                new AlertaGenerada(4L, DisparadorAlerta.CELO, "Celo Detectado", AHORA.minusDays(2),
                        EstadoAlerta.CONFIRMADA),
                new AlertaGenerada(6L, DisparadorAlerta.CELO, "Celo Detectado", AHORA.minusDays(3),
                        EstadoAlerta.VENCIDA),
                new AlertaGenerada(5L, DisparadorAlerta.CELO, "Anestro Detectado", AHORA.minusDays(2),
                        EstadoAlerta.ARCHIVADA));

        assertThat(calcular(List.of(), List.of(), Map.of(), generadas).getVentanasAbiertas()
                .getPerdidasUltimos30Dias()).isEqualTo(3);
    }

    @Test
    @DisplayName("Semana ISO en el cambio de año")
    void semanaIsoCambioDeAnio() {
        assertThat(CentroMandoCalculadora.semanaIso(LocalDateTime.of(2027, 1, 1, 10, 0))).isEqualTo("2026-W53");
        assertThat(CentroMandoCalculadora.semanaIso(LocalDateTime.of(2027, 1, 4, 10, 0))).isEqualTo("2027-W01");
    }
}
