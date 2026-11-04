package dev.vigia.backend.service.dashboard;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.ResumenEjecutivoResponse;
import dev.vigia.backend.dto.ResumenEjecutivoResponse.Equipo;
import dev.vigia.backend.dto.ResumenEjecutivoResponse.FilaRodeo;
import dev.vigia.backend.dto.ResumenEjecutivoResponse.RangoAtencion;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.service.dashboard.DashboardDatos.AlertaGenerada;
import dev.vigia.backend.service.dashboard.DashboardDatos.AnimalDashboard;
import dev.vigia.backend.service.dashboard.ResumenEjecutivoCalculadora.RodeoDashboard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.AHORA;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.HOY;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.UMBRALES;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.alerta;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.conCaravana;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.conReproductivo;
import static dev.vigia.backend.service.dashboard.CentroMandoCalculadoraTest.vaca;
import static org.assertj.core.api.Assertions.assertThat;

/** Mismo "ahora" y mismas vacas que {@link CentroMandoCalculadoraTest}: miércoles 4/11/2026 a las 12. */
@DisplayName("ResumenEjecutivoCalculadora")
class ResumenEjecutivoCalculadoraTest {

    private static final RodeoDashboard RODEO_1 = new RodeoDashboard(1L, "Rodeo 1");
    private static final RodeoDashboard RODEO_2 = new RodeoDashboard(2L, "Rodeo 2");
    private static final RodeoDashboard RODEO_VACIO = new RodeoDashboard(3L, "Rodeo vacío");

    /** Las vacas de los tests son del rodeo 1; esta la pasa al 2. */
    private static AnimalDashboard enRodeo2(AnimalDashboard a) {
        return new AnimalDashboard(a.id(), a.idCaravanaOficial(), a.alias(), 2L, "Rodeo 2", a.fechaNacimiento(),
                a.fechaAlta(), a.tieneCaravana(), a.enCalibracion(), a.ultimaSenial(), a.bateria(),
                a.estadoReproductivo(), a.fechaUltimoParto(), a.fechaUltimaInseminacion(), a.estadoSalud());
    }

    private static AlertaGenerada generada(long animalId, DisparadorAlerta disparador, String tipo, int diasAtras,
            EstadoAlerta estado, Double horasHastaResolver) {
        LocalDateTime ts = AHORA.minusDays(diasAtras);
        return new AlertaGenerada(animalId, disparador, tipo, ts, estado,
                horasHastaResolver == null ? null : ts.plusMinutes(Math.round(horasHastaResolver * 60)));
    }

    private static ResumenEjecutivoResponse calcular(List<AnimalDashboard> animales, List<RodeoDashboard> rodeos,
            List<AlertaResponse> pendientes, List<AlertaGenerada> generadas) {
        return ResumenEjecutivoCalculadora.calcular(animales, rodeos, pendientes, generadas, 30, UMBRALES, AHORA);
    }

    @Test
    @DisplayName("Tabla por rodeo: animales, cobertura, alertas cada 100, ventanas perdidas y vacías de más de 90 días")
    void tablaPorRodeo() {
        List<AnimalDashboard> animales = List.of(
                conReproductivo(vaca(1), TipoReproductivo.VACIA, HOY.minusDays(100), null),
                conReproductivo(vaca(2), TipoReproductivo.VACIA, HOY.minusDays(30), null),
                conCaravana(vaca(3), false, false, null, null),
                conReproductivo(vaca(4), TipoReproductivo.GESTANTE, null, HOY.minusDays(60)),
                enRodeo2(vaca(5)));
        List<AlertaResponse> pendientes = List.of(
                alerta(10, 1, DisparadorAlerta.SALUD, "MEDIA", AHORA.minusHours(2)),
                alerta(11, 3, DisparadorAlerta.CELO, "ALTA", AHORA.minusHours(2)),
                alerta(12, 5, DisparadorAlerta.SALUD, "MEDIA", AHORA.minusHours(2)));
        List<AlertaGenerada> generadas = List.of(
                generada(1, DisparadorAlerta.CELO, "Celo Detectado", 5, EstadoAlerta.ARCHIVADA, null),
                generada(2, DisparadorAlerta.CELO, "Celo Detectado", 6, EstadoAlerta.VENCIDA, null),
                // Fuera del período, atendido o anestro: no son ventanas perdidas.
                generada(2, DisparadorAlerta.CELO, "Celo Detectado", 40, EstadoAlerta.ARCHIVADA, null),
                generada(3, DisparadorAlerta.CELO, "Celo Detectado", 3, EstadoAlerta.CONFIRMADA, 2.0),
                generada(4, DisparadorAlerta.CELO, "Anestro Detectado", 3, EstadoAlerta.ARCHIVADA, null),
                generada(5, DisparadorAlerta.CELO, "Celo Detectado", 2, EstadoAlerta.ARCHIVADA, null));

        List<FilaRodeo> filas = calcular(animales, List.of(RODEO_1, RODEO_2, RODEO_VACIO), pendientes, generadas)
                .getRodeos();

        assertThat(filas).extracting(FilaRodeo::getNombre).containsExactly("Rodeo 1", "Rodeo 2", "Rodeo vacío");
        FilaRodeo r1 = filas.get(0);
        assertThat(r1.getAnimales()).isEqualTo(4);
        assertThat(r1.getCoberturaPorcentaje()).isEqualTo(75);
        assertThat(r1.getAlertasPendientes()).isEqualTo(2);
        assertThat(r1.getAlertasCada100()).isEqualTo(50);
        assertThat(r1.getVentanasPerdidas()).isEqualTo(2);
        assertThat(r1.getVaciasLargasPorcentaje()).isEqualTo(25);
        FilaRodeo r2 = filas.get(1);
        assertThat(r2.getAnimales()).isEqualTo(1);
        assertThat(r2.getAlertasCada100()).isEqualTo(100);
        assertThat(r2.getVentanasPerdidas()).isEqualTo(1);
        FilaRodeo vacio = filas.get(2);
        assertThat(vacio.getAnimales()).isZero();
        assertThat(vacio.getCoberturaPorcentaje()).isZero();
    }

    @Test
    @DisplayName("Promedio de los rodeos con animales, y valores de referencia si no hay ninguno")
    void promedio() {
        List<FilaRodeo> filas = List.of(
                FilaRodeo.builder().animales(4).coberturaPorcentaje(75).alertasCada100(50).ventanasPerdidas(2)
                        .vaciasLargasPorcentaje(25).build(),
                FilaRodeo.builder().animales(1).coberturaPorcentaje(100).alertasCada100(100).ventanasPerdidas(1)
                        .vaciasLargasPorcentaje(0).build(),
                FilaRodeo.builder().animales(0).build());

        ResumenEjecutivoResponse.Promedio p = ResumenEjecutivoCalculadora.promedio(filas);

        assertThat(p.getCoberturaPorcentaje()).isEqualTo(88);
        assertThat(p.getAlertasCada100()).isEqualTo(75);
        assertThat(p.getVentanasPerdidas()).isEqualTo(2);
        assertThat(p.getVaciasLargasPorcentaje()).isEqualTo(13);
        assertThat(ResumenEjecutivoCalculadora.promedio(List.of(FilaRodeo.builder().build())))
                .isEqualTo(ResumenEjecutivoCalculadora.PROMEDIO_SIN_DATOS);
    }

    @Test
    @DisplayName("Equipo: en cola, atendidas y descartadas del período, y tiempo de atención por rango")
    void equipo() {
        List<AlertaResponse> pendientes = List.of(
                alerta(10, 1, DisparadorAlerta.SALUD, "MEDIA", AHORA),
                alerta(11, 2, DisparadorAlerta.SALUD, "MEDIA", AHORA));
        List<AlertaGenerada> generadas = List.of(
                generada(1, DisparadorAlerta.SALUD, "SALUD", 1, EstadoAlerta.CONFIRMADA, 2.0),
                generada(1, DisparadorAlerta.SALUD, "SALUD", 2, EstadoAlerta.CONFIRMADA, 6.0),
                generada(2, DisparadorAlerta.CELO, "Celo Detectado", 3, EstadoAlerta.DESCARTADA, 30.0),
                generada(2, DisparadorAlerta.FUERA_DE_PERIMETRO, "Fuera", 4, EstadoAlerta.CONFIRMADA, 80.0),
                generada(3, DisparadorAlerta.CELO, "Celo Detectado", 5, EstadoAlerta.ARCHIVADA, null),
                generada(3, DisparadorAlerta.SALUD, "SALUD", 1, EstadoAlerta.PENDIENTE, null),
                // Anestro archivado: no es trabajo del equipo. Fuera del período: no cuenta.
                generada(4, DisparadorAlerta.CELO, "Anestro Detectado", 2, EstadoAlerta.ARCHIVADA, null),
                generada(4, DisparadorAlerta.SALUD, "SALUD", 31, EstadoAlerta.CONFIRMADA, 1.0));

        Equipo e = calcular(List.of(), List.of(), pendientes, generadas).getEquipo();

        assertThat(e.getEnCola()).isEqualTo(2);
        // 4 resueltas de 6 generadas; 1 descartada de 4.
        assertThat(e.getPorcentajeAtendidas()).isEqualTo(67);
        assertThat(e.getPorcentajeDescartadas()).isEqualTo(25);
        assertThat(e.getTotalAtendidas()).isEqualTo(4);
        assertThat(e.getTiempoAtencion()).extracting(RangoAtencion::getRango)
                .containsExactly("<6h", "6-24h", "1-3d", ">3d");
        assertThat(e.getTiempoAtencion()).extracting(RangoAtencion::getAlertas).containsExactly(1L, 1L, 1L, 1L);
    }

    @Test
    @DisplayName("La tendencia es la misma que la del Centro de mando, de todo el establecimiento")
    void tendencia() {
        List<AlertaGenerada> generadas = List.of(
                generada(1, DisparadorAlerta.SALUD, "SALUD", 0, EstadoAlerta.PENDIENTE, null),
                generada(2, DisparadorAlerta.SALUD, "SALUD", 0, EstadoAlerta.CONFIRMADA, 1.0));

        ResumenEjecutivoResponse r = calcular(List.of(), List.of(), List.of(), generadas);

        assertThat(r.getTendencia().getDatos().get("SALUD").get(7)).isEqualTo(2L);
        assertThat(r.getPeriodoDias()).isEqualTo(30);
        assertThat(r.getUmbrales()).isEqualTo(UMBRALES);
    }
}
