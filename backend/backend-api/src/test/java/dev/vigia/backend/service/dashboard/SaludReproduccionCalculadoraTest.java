package dev.vigia.backend.service.dashboard;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.AnimalResumenResponse;
import dev.vigia.backend.dto.SaludReproduccionResponse;
import dev.vigia.backend.dto.SaludReproduccionResponse.AnimalEnObservacion;
import dev.vigia.backend.dto.SaludReproduccionResponse.AnimalSinIntervencion;
import dev.vigia.backend.dto.SaludReproduccionResponse.Partos;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.TipoEstadoSalud;
import dev.vigia.backend.model.TipoIntervencion;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.service.dashboard.DashboardDatos.AlertaGenerada;
import dev.vigia.backend.service.dashboard.DashboardDatos.AnimalDashboard;
import dev.vigia.backend.service.dashboard.DashboardDatos.IntervencionDashboard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.AHORA;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.HOY;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.UMBRALES;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.alerta;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.conCaravana;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.conReproductivo;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.conSalud;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.nacida;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.vaca;
import static org.assertj.core.api.Assertions.assertThat;

/** Mismo "ahora" y mismas vacas que {@link CentroMandoCalculadoraTest}: miércoles 4/11/2026 a las 12. */
@DisplayName("SaludReproduccionCalculadora")
class SaludReproduccionCalculadoraTest {

    private static SaludReproduccionResponse calcular(List<AnimalDashboard> animales,
            List<AlertaResponse> pendientes, Set<Long> anestros, List<AlertaGenerada> generadas,
            List<IntervencionDashboard> intervenciones) {
        return SaludReproduccionCalculadora.calcular(animales, pendientes, anestros, Map.of(), generadas,
                intervenciones, 30, UMBRALES, AHORA);
    }

    private static AnimalDashboard enRodeo(AnimalDashboard a, String rodeo) {
        return new AnimalDashboard(a.id(), a.idCaravanaOficial(), a.alias(), a.rodeoId(), rodeo, a.fechaNacimiento(),
                a.fechaAlta(), a.tieneCaravana(), a.enCalibracion(), a.ultimaSenial(), a.bateria(),
                a.estadoReproductivo(), a.fechaUltimoParto(), a.fechaUltimaInseminacion(), a.estadoSalud());
    }

    private static AlertaGenerada generada(long animalId, DisparadorAlerta disparador, String tipo, int diasAtras,
            EstadoAlerta estado) {
        return new AlertaGenerada(animalId, disparador, tipo, AHORA.minusDays(diasAtras), estado, null);
    }

    private static List<Long> ids(List<AnimalResumenResponse> animales) {
        return animales.stream().map(AnimalResumenResponse::getId).toList();
    }

    @Test
    @DisplayName("Embudo: vacías de más de 90 días, inseminadas sin diagnóstico, anestro pendiente y completitud")
    void embudoYExcepciones() {
        List<AnimalDashboard> animales = List.of(
                conReproductivo(vaca(1), TipoReproductivo.VACIA, HOY.minusDays(100), null),
                conReproductivo(vaca(2), TipoReproductivo.VACIA, HOY.minusDays(50), null),
                conReproductivo(vaca(3), TipoReproductivo.INSEMINADA, null, HOY.minusDays(40)),
                conReproductivo(vaca(4), TipoReproductivo.ANESTRO, null, null),
                // Sin estado: baja la completitud.
                vaca(5),
                // Ternera con anestro pendiente: no es reproductiva, no entra en las excepciones.
                nacida(vaca(6), HOY.minusMonths(6)));

        SaludReproduccionResponse.Embudo e = calcular(animales, List.of(), Set.of(4L, 6L), List.of(), List.of())
                .getEmbudo();

        assertThat(e.getTotalReproductivos()).isEqualTo(5);
        assertThat(ids(e.getVaciasLargas())).containsExactly(1L);
        assertThat(ids(e.getInseminadasSinDiagnostico())).containsExactly(3L);
        assertThat(ids(e.getAnestroSinIntervencion())).containsExactly(4L);
        assertThat(e.getCompletitudPorcentaje()).isEqualTo(80);
        assertThat(e.getDistribucion().get("VACIA")).isEqualTo(2L);
    }

    @Test
    @DisplayName("Indicadores: días abiertos, sanitarias sin atender hace más de 24 h y celos con ventana")
    void indicadores() {
        List<AnimalDashboard> animales = List.of(
                conReproductivo(vaca(1), TipoReproductivo.VACIA, HOY.minusDays(100), null),
                conReproductivo(vaca(2), TipoReproductivo.PARIDA, HOY.minusDays(50), null),
                vaca(3),
                // Sin perfil de salud todavía; la sin caravana no calibra.
                conCaravana(vaca(4), true, true, AHORA.minusMinutes(10), 80.0),
                conCaravana(vaca(5), true, true, AHORA.minusMinutes(10), 80.0),
                nacida(conCaravana(vaca(6), false, false, null, null), HOY.minusMonths(6)));
        List<AlertaResponse> pendientes = List.of(
                alerta(10, 3, DisparadorAlerta.SALUD, "MEDIA", AHORA.minusHours(2)),
                alerta(11, 3, DisparadorAlerta.SALUD, "ALTA", AHORA.minusHours(30)),
                alerta(12, 1, DisparadorAlerta.FUERA_DE_PERIMETRO, "ALTA", AHORA.minusHours(40)));

        SaludReproduccionResponse r = calcular(animales, pendientes, Set.of(), List.of(), List.of());

        assertThat(r.getIndicadores().getDiasAbiertosPromedio()).isEqualTo(76);
        assertThat(r.getIndicadores().getVacasAbiertas()).isEqualTo(2);
        assertThat(r.getIndicadores().getSaludSinAtender()).isEqualTo(1);
        assertThat(r.getIndicadores().getCelosSinAtender()).isZero();
        assertThat(r.getIndicadores().getAnimalesEnCalibracion()).isEqualTo(2);
        // Todas las sanitarias pendientes, de la más vieja a la más nueva.
        assertThat(r.getAlertasSaludSinAtender()).extracting(a -> a.getAlerta().getId()).containsExactly(11L, 10L);
        assertThat(r.getAlertasSaludSinAtender().get(0).getAnimal().getId()).isEqualTo(3L);
        assertThat(r.getPeriodoDias()).isEqualTo(30);
    }

    @Test
    @DisplayName("En observación: enfermos primero, con los días desde su última intervención")
    void enObservacion() {
        List<AnimalDashboard> animales = List.of(
                conSalud(vaca(1), TipoEstadoSalud.EN_OBSERVACION),
                conSalud(vaca(2), TipoEstadoSalud.SANO),
                conSalud(vaca(3), TipoEstadoSalud.ENFERMO));
        List<IntervencionDashboard> intervenciones = List.of(
                new IntervencionDashboard(1L, TipoIntervencion.TRATAMIENTO, AHORA.minusDays(3).minusHours(5), "Laura"));

        List<AnimalEnObservacion> r = calcular(animales, List.of(), Set.of(), List.of(), intervenciones)
                .getEnObservacion();

        assertThat(r).extracting(a -> a.getAnimal().getId()).containsExactly(3L, 1L);
        assertThat(r.get(0).getDiasSinIntervencion()).isNull();
        assertThat(r.get(0).getUltimaIntervencionTipo()).isNull();
        assertThat(r.get(1).getDiasSinIntervencion()).isEqualTo(3L);
        assertThat(r.get(1).getUltimaIntervencionTipo()).isEqualTo(TipoIntervencion.TRATAMIENTO);
        assertThat(r.get(1).getAnimal().getRodeoLoteNombre()).isEqualTo("Rodeo 1");
    }

    @Test
    @DisplayName("Partos: a 283 días de la inseminación, por semana desde hoy y por día en la vista anual")
    void partos() {
        List<AnimalDashboard> animales = List.of(
                // Parto en 3 días: semana 0.
                conReproductivo(vaca(1), TipoReproductivo.GESTANTE, null, HOY.minusDays(280)),
                // Parto en 20 días: semana 2 (días 14 a 20).
                conReproductivo(vaca(2), TipoReproductivo.GESTANTE, null, HOY.minusDays(263)),
                // Parto hace 17 días: vencido, fuera de la vista anual (que arranca el lunes 2/11).
                conReproductivo(vaca(3), TipoReproductivo.GESTANTE, null, HOY.minusDays(300)),
                // Parto el lunes de esta semana: vencido, pero dentro de la vista anual.
                conReproductivo(vaca(4), TipoReproductivo.GESTANTE, null, HOY.minusDays(285)),
                conReproductivo(vaca(5), TipoReproductivo.GESTANTE, null, null),
                conReproductivo(vaca(6), TipoReproductivo.VACIA, null, HOY.minusDays(280)));

        Partos p = calcular(animales, List.of(), Set.of(), List.of(), List.of()).getPartos();

        assertThat(p.getSemanas()).hasSize(12);
        assertThat(p.getSemanas().get(0).getDesde()).isEqualTo(HOY);
        assertThat(ids(p.getSemanas().get(0).getAnimales())).containsExactly(1L);
        assertThat(p.getSemanas().get(1).getAnimales()).isEmpty();
        assertThat(ids(p.getSemanas().get(2).getAnimales())).containsExactly(2L);
        assertThat(p.getPorDia()).extracting(SaludReproduccionResponse.DiaPartos::getFecha)
                .containsExactly(LocalDate.of(2026, 11, 2), HOY.plusDays(3), HOY.plusDays(20));
        assertThat(p.getGestantesSinFecha()).isEqualTo(1);
        assertThat(p.getVencidos()).isEqualTo(2);
        assertThat(p.getTotalGestantes()).isEqualTo(5);
    }

    @Test
    @DisplayName("Desempeño: celos del período por resultado e inseminadas del período que hoy están gestantes")
    void desempenyo() {
        List<AnimalDashboard> animales = List.of(
                conReproductivo(vaca(1), TipoReproductivo.GESTANTE, null, HOY.minusDays(10)),
                conReproductivo(vaca(2), TipoReproductivo.INSEMINADA, null, HOY.minusDays(20)),
                conReproductivo(vaca(3), TipoReproductivo.GESTANTE, null, HOY.minusDays(40)),
                vaca(4));
        List<AlertaGenerada> generadas = List.of(
                generada(1, DisparadorAlerta.CELO, "Celo Detectado", 25, EstadoAlerta.CONFIRMADA),
                generada(2, DisparadorAlerta.CELO, "Celo Detectado", 15, EstadoAlerta.ARCHIVADA),
                generada(3, DisparadorAlerta.CELO, "Celo Detectado", 1, EstadoAlerta.PENDIENTE),
                generada(3, DisparadorAlerta.CELO, "Celo Detectado", 5, EstadoAlerta.VENCIDA),
                generada(4, DisparadorAlerta.CELO, "Anestro Detectado", 1, EstadoAlerta.PENDIENTE),
                generada(4, DisparadorAlerta.CELO, "Celo Detectado", 31, EstadoAlerta.ARCHIVADA),
                generada(4, DisparadorAlerta.SALUD, "SALUD", 1, EstadoAlerta.PENDIENTE));

        SaludReproduccionResponse.Desempenyo d = calcular(animales, List.of(), Set.of(), generadas, List.of())
                .getDesempenyo();

        assertThat(d.getReproductivos()).isEqualTo(4);
        assertThat(d.getCelosDetectados()).isEqualTo(4);
        assertThat(d.getCelosAtendidos()).isEqualTo(1);
        assertThat(d.getVentanasPerdidas()).isEqualTo(2);
        assertThat(d.getInseminadas()).isEqualTo(2);
        assertThat(d.getGestantes()).isEqualTo(1);
    }

    @Test
    @DisplayName("Reincidencia: animales con 2 o más alertas y lotes con salidas de perímetro repetidas")
    void reincidencia() {
        List<AnimalDashboard> animales = List.of(vaca(1), enRodeo(vaca(2), "Rodeo 2"), vaca(3));
        EstadoAlerta p = EstadoAlerta.PENDIENTE;
        List<AlertaGenerada> generadas = List.of(
                generada(2, DisparadorAlerta.FUERA_DE_PERIMETRO, "Fuera", 1, p),
                generada(1, DisparadorAlerta.SALUD, "SALUD", 2, p),
                generada(1, DisparadorAlerta.FUERA_DE_PERIMETRO, "Fuera", 3, p),
                generada(1, DisparadorAlerta.FUERA_DE_PERIMETRO, "Fuera", 4, p),
                generada(2, DisparadorAlerta.FUERA_DE_PERIMETRO, "Fuera", 5, p),
                generada(3, DisparadorAlerta.SALUD, "SALUD", 5, p),
                // Fuera del período.
                generada(3, DisparadorAlerta.SALUD, "SALUD", 40, p),
                // Animal que ya no está activo.
                generada(99, DisparadorAlerta.CELO, "Celo Detectado", 6, p),
                generada(99, DisparadorAlerta.CELO, "Celo Detectado", 7, p));

        SaludReproduccionResponse.Reincidencia r = calcular(animales, List.of(), Set.of(), generadas, List.of())
                .getReincidencia();

        assertThat(r.getAnimales()).extracting(a -> a.getAnimal() == null ? null : a.getAnimal().getId())
                .containsExactly(1L, 2L, null);
        assertThat(r.getAnimales()).extracting(SaludReproduccionResponse.AnimalAlertas::getAlertas)
                .containsExactly(3L, 2L, 2L);
        assertThat(r.getLotes()).extracting(SaludReproduccionResponse.LoteSalidas::getNombre)
                .containsExactly("Rodeo 2", "Rodeo 1");
    }

    @Test
    @DisplayName("Última intervención: la más reciente y los que llevan más tiempo sin ninguna")
    void ultimaIntervencion() {
        List<AnimalDashboard> animales = List.of(vaca(1), vaca(2), vaca(3), vaca(4));
        LocalDateTime hace2Dias = AHORA.minusDays(2);
        List<IntervencionDashboard> intervenciones = List.of(
                new IntervencionDashboard(1L, TipoIntervencion.VACUNACION, AHORA.minusDays(10), "Laura"),
                new IntervencionDashboard(2L, TipoIntervencion.REVISION_VETERINARIA, hace2Dias, "Carlos"),
                new IntervencionDashboard(4L, TipoIntervencion.OTRO, AHORA.minusDays(30), "Laura"));

        SaludReproduccionResponse.UltimaIntervencion u = calcular(animales, List.of(), Set.of(), List.of(),
                intervenciones).getUltimaIntervencion();

        assertThat(u.getMasReciente().getAnimal().getId()).isEqualTo(2L);
        assertThat(u.getMasReciente().getTipo()).isEqualTo(TipoIntervencion.REVISION_VETERINARIA);
        assertThat(u.getMasReciente().getInterventor()).isEqualTo("Carlos");
        assertThat(u.getMasReciente().getFechaHora()).isEqualTo(hace2Dias);
        assertThat(u.getSinIntervencionHaceMas()).extracting(s -> s.getAnimal().getId())
                .containsExactly(3L, 4L, 1L, 2L);
        assertThat(u.getSinIntervencionHaceMas()).extracting(AnimalSinIntervencion::getDias)
                .containsExactly(null, 30L, 10L, 2L);
    }

    @Test
    @DisplayName("Sin intervenciones: no hay más reciente")
    void sinIntervenciones() {
        assertThat(calcular(List.of(vaca(1)), List.of(), Set.of(), List.of(), List.of()).getUltimaIntervencion()
                .getMasReciente()).isNull();
    }
}
