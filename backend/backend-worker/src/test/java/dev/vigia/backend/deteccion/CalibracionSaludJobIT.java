package dev.vigia.backend.deteccion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("H0017 - Alerta sanitaria por desvío (período de calibración 7 días) — IT")
class CalibracionSaludJobIT extends SaludItBase {

    @Test
    @DisplayName("H0017-01 - Calibración - Sin alerta sanitaria durante los 7 días de calibración")
    void H0017_01_sinAlertaDuranteCalibracion() throws Exception {
        // Aunque el modelo diría que hay desvío, en calibración no se lo consulta.
        responderPerfil();
        ML.responder(MlSimulado.SALUD_PREDICT, desvio(3, "Posible enfermedad", 0.95));
        // A-102: su caravana transmite hace 24 h.
        Long recien = idAnimal(EST_A, "A-102");
        // A-101: la caravana tiene 8 días de datos, pero se le asignó hace 2: la calibración cuenta desde la asignación.
        Long reasignada = idAnimal(EST_A, "A-101");
        jdbcTemplate.update("UPDATE caravanas SET fecha_asignacion = current_date - 2 WHERE id = ?",
                idCaravana(reasignada));
        marcarUltimaLecturaPendiente(recien, "salud");
        marcarUltimaLecturaPendiente(reasignada, "salud");

        correr(saludJob);

        assertThat(ML.llamadas(MlSimulado.SALUD_PERFIL)).isZero();
        assertThat(ML.llamadas(MlSimulado.SALUD_PREDICT)).isZero();
        for (Long animal : List.of(recien, reasignada)) {
            assertThat(alertas(animal, "SALUD")).isEmpty();
            assertThat(cantidadPerfiles(animal)).isZero();
            assertThat(lecturasSinEvaluar(animal, "salud")).isZero();
        }
    }

    @Test
    @DisplayName("H0017-02 - Calibración - Al cumplir 7 días se calcula y guarda el perfil de referencia")
    void H0017_02_finDeCalibracionCalculaPerfil() throws Exception {
        responderPerfil();
        ML.responder(MlSimulado.SALUD_PREDICT, SIN_DESVIO);
        Long animal = idAnimal(EST_A, "A-101");
        marcarUltimaLecturaPendiente(animal, "salud");
        LocalDateTime antes = LocalDateTime.now();

        correr(saludJob);

        // Perfil: los primeros 7 días de datos, a 5 min.
        assertThat(ML.llamadas(MlSimulado.SALUD_PERFIL)).isEqualTo(1);
        JsonNode calibracion = json(ML.recibidos(MlSimulado.SALUD_PERFIL).get(0)).get("calibracion");
        assertThat(calibracion.size()).isGreaterThanOrEqualTo(7 * 288);

        Map<String, Object> perfil = jdbcTemplate.queryForMap(
                "SELECT perfil_json, fecha_calculo FROM perfiles_referencia WHERE animal_id = ?", animal);
        assertThat(json((String) perfil.get("perfil_json"))).isEqualTo(json(PERFIL));
        assertThat(((java.sql.Timestamp) perfil.get("fecha_calculo")).toLocalDateTime()).isAfterOrEqualTo(antes);

        // Con el perfil recién calculado se evalúa la ventana de las últimas 24 h.
        assertThat(ML.llamadas(MlSimulado.SALUD_PREDICT)).isEqualTo(1);
        JsonNode prediccion = json(ML.recibidos(MlSimulado.SALUD_PREDICT).get(0));
        assertThat(prediccion.get("serie")).hasSize(288);
        assertThat(prediccion.get("perfil")).isEqualTo(json(PERFIL));

        // Sin desvío, no hay alerta.
        assertThat(alertas(animal, "SALUD")).isEmpty();
        assertThat(lecturasSinEvaluar(animal, "salud")).isZero();
    }

    @Test
    @DisplayName("H0017-03 - Calibración - Desvío después de la calibración genera alerta sanitaria")
    void H0017_03_desvioDespuesDeCalibracion() throws Exception {
        responderPerfil();
        ML.responder(MlSimulado.SALUD_PREDICT, desvio(2, "Disminución de actividad", 0.81));
        Long animal = idAnimal(EST_A, "A-101");
        marcarUltimaLecturaPendiente(animal, "salud");

        correr(saludJob);

        assertThat(ML.llamadas(MlSimulado.SALUD_PERFIL)).isEqualTo(1);
        assertThat(ML.llamadas(MlSimulado.SALUD_PREDICT)).isEqualTo(1);
        List<Map<String, Object>> alertas = alertas(animal, "SALUD");
        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).get("tipo")).isEqualTo("SALUD");
        assertThat(alertas.get(0).get("estado")).isEqualTo("PENDIENTE");
        assertThat(((Number) alertas.get(0).get("desvio_detectado")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("H0017-04 - Calibración - Sin alertas duplicadas ni telemetría procesada dos veces")
    void H0017_04_sinDuplicarNiReprocesar() throws Exception {
        ML.responder(MlSimulado.SALUD_PREDICT, desvio(2, "Disminución de actividad", 0.81));
        Long animal = idAnimal(EST_A, "A-101");
        cargarPerfil(animal);
        // A-103 ya tiene una alerta sanitaria de hace 2 h: ni se consulta a ml-salud.
        Long conAlerta = idAnimal(EST_A, "A-103");
        marcarUltimaLecturaPendiente(animal, "salud");
        marcarUltimaLecturaPendiente(conAlerta, "salud");

        correr(saludJob);
        assertThat(ML.llamadas(MlSimulado.SALUD_PREDICT)).isEqualTo(1);
        assertThat(alertas(animal, "SALUD")).hasSize(1);
        assertThat(alertas(conAlerta, "SALUD")).hasSize(1);
        assertThat(lecturasSinEvaluar(conAlerta, "salud")).isZero();

        // Sin telemetría nueva no se vuelve a evaluar la misma ventana.
        correr(saludJob);
        assertThat(ML.llamadas(MlSimulado.SALUD_PREDICT)).isEqualTo(1);

        // Con una lectura nueva, la alerta de hace un momento evita una segunda alerta.
        insertarLecturaNueva(animal);
        correr(saludJob);
        assertThat(ML.llamadas(MlSimulado.SALUD_PREDICT)).isEqualTo(1);
        assertThat(alertas(animal, "SALUD")).hasSize(1);
        assertThat(lecturasSinEvaluar(animal, "salud")).isZero();
    }

    private int cantidadPerfiles(Long animalId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM perfiles_referencia WHERE animal_id = ?",
                Integer.class, animalId);
    }
}
