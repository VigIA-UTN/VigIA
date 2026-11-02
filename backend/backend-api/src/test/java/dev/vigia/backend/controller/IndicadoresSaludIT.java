package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /api/v1/animales/{id}/indicadores-salud: semáforo de movimiento (última lectura contra el perfil, VERDE dentro
 * de 1 desvío, AMARILLO hasta 2, ROJO más allá), del estado reproductivo y del último estado de salud. Cada caso le da
 * al animal un perfil con media 4 y desvío 1 y una lectura de este momento con el moveState que prueba.
 */
@DisplayName("H0020 - Indicadores de salud (semáforo normal/borde/crítico) — IT")
class IndicadoresSaludIT extends DeteccionItBase {

    private static final String MOVIMIENTO = "Estado de movimiento";
    private static final String REPRODUCTIVO = "Estado reproductivo";
    private static final String SALUD = "Estado de salud";

    @Test
    @DisplayName("H0020-01 - Indicadores - Valores normales en verde")
    void H0020_01_normal() throws Exception {
        // A-101 está GESTANTE en el escenario.
        Long animal = idAnimal(EST_A, "A-101");
        prepararMovimiento(animal, 4);
        registrarSalud(animal, "SANO");

        JsonNode respuesta = indicadores("veterinarioA", animal);

        assertThat(respuesta.get("animalId").asLong()).isEqualTo(animal);
        assertThat(respuesta.get("caravana").asText()).isEqualTo("FX-A-01");
        assertThat(respuesta.get("indicadores")).hasSize(3);
        JsonNode movimiento = indicador(respuesta, MOVIMIENTO);
        assertThat(movimiento.get("estado").asText()).isEqualTo("VERDE");
        assertThat(movimiento.get("valor").asText()).isEqualTo("4");
        assertThat(movimiento.get("descripcion").asText()).isEqualTo("Actividad dentro del rango normal para este animal.");
        assertThat(indicador(respuesta, REPRODUCTIVO).get("estado").asText()).isEqualTo("VERDE");
        assertThat(indicador(respuesta, REPRODUCTIVO).get("valor").asText()).isEqualTo("GESTANTE");
        assertThat(indicador(respuesta, SALUD).get("estado").asText()).isEqualTo("VERDE");
        assertThat(indicador(respuesta, SALUD).get("valor").asText()).isEqualTo("SANO");
    }

    @Test
    @DisplayName("H0020-02 - Indicadores - Valores de borde en amarillo")
    void H0020_02_borde() throws Exception {
        // A-102 tiene el POSIBLE_CELO de su alerta como último estado reproductivo.
        Long animal = idAnimal(EST_A, "A-102");
        // Justo en media + 1 desvío ya no es normal.
        prepararMovimiento(animal, 5);
        registrarSalud(animal, "EN_OBSERVACION");

        JsonNode respuesta = indicadores("productorA", animal);

        assertThat(indicador(respuesta, MOVIMIENTO).get("estado").asText()).isEqualTo("AMARILLO");
        assertThat(indicador(respuesta, REPRODUCTIVO).get("estado").asText()).isEqualTo("AMARILLO");
        assertThat(indicador(respuesta, REPRODUCTIVO).get("valor").asText()).isEqualTo("POSIBLE_CELO");
        assertThat(indicador(respuesta, SALUD).get("estado").asText()).isEqualTo("AMARILLO");
        // Del otro lado del rango, también.
        prepararMovimiento(animal, 3);
        assertThat(indicador(indicadores("productorA", animal), MOVIMIENTO).get("estado").asText())
                .isEqualTo("AMARILLO");
    }

    @Test
    @DisplayName("H0020-03 - Indicadores - Valores críticos en rojo")
    void H0020_03_critico() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");
        // Justo en media + 2 desvíos ya es crítico.
        prepararMovimiento(animal, 6);
        jdbcTemplate.update("INSERT INTO estados_reproductivos (animal_id, estado, origen, fecha_registro) "
                + "VALUES (?, 'ANESTRO', 'MANUAL', ?)", animal, Timestamp.valueOf(LocalDateTime.now()));
        registrarSalud(animal, "ENFERMO");

        JsonNode respuesta = indicadores("veterinarioA", animal);

        assertThat(indicador(respuesta, MOVIMIENTO).get("estado").asText()).isEqualTo("ROJO");
        assertThat(indicador(respuesta, MOVIMIENTO).get("descripcion").asText())
                .isEqualTo("Actividad muy alejada del rango normal para este animal.");
        assertThat(indicador(respuesta, REPRODUCTIVO).get("estado").asText()).isEqualTo("ROJO");
        assertThat(indicador(respuesta, SALUD).get("estado").asText()).isEqualTo("ROJO");
        insertarLectura(animal, 1);
        assertThat(indicador(indicadores("veterinarioA", animal), MOVIMIENTO).get("estado").asText())
                .isEqualTo("ROJO");
    }

    @Test
    @DisplayName("H0020-04 - Indicadores - Sin datos: sin perfil, caravana muda más de 6 h o sin registros")
    void H0020_04_sinDatos() throws Exception {
        // a) Sin perfil de referencia (en calibración) y sin estados de salud.
        Long sinPerfil = idAnimal(EST_A, "A-103");
        JsonNode respuesta = indicadores("veterinarioA", sinPerfil);
        assertThat(indicador(respuesta, MOVIMIENTO).get("estado").asText()).isEqualTo("SIN DATOS");
        assertThat(indicador(respuesta, SALUD).get("estado").asText()).isEqualTo("SIN DATOS");

        // b) Con perfil, pero la caravana no transmite hace 7 h: no se muestra el último valor calculado.
        Long muda = idAnimal(EST_A, "A-101");
        prepararMovimiento(muda, 4);
        jdbcTemplate.update("DELETE FROM telemetria WHERE caravana_id = ? AND fecha_hora > ?", idCaravana("FX-A-01"),
                Timestamp.valueOf(LocalDateTime.now().minusHours(7)));
        JsonNode movimiento = indicador(indicadores("veterinarioA", muda), MOVIMIENTO);
        assertThat(movimiento.get("estado").asText()).isEqualTo("SIN DATOS");
        assertThat(movimiento.get("descripcion").asText()).contains("últimas 6 horas");

        // c) Animal recién dado de alta: sin caravana ni registros.
        MvcResult alta = altaAnimal("productorA", cuerpoAnimal("A-191", EST_A, "Rodeo A1"));
        assertThat(estado(alta)).as(texto(alta)).isEqualTo(201);
        JsonNode nuevo = indicadores("productorA", leer(alta).get("id").asLong());
        assertThat(nuevo.get("caravana").isNull()).isTrue();
        nuevo.get("indicadores").forEach(i -> assertThat(i.get("estado").asText()).as(i.get("nombre").asText())
                .isEqualTo("SIN DATOS"));
    }

    @Test
    @DisplayName("H0020-05 - Indicadores - Acceso por rol y establecimiento")
    void H0020_05_acceso() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");

        assertThat(estado(getAnimal("productorA", animal, "/indicadores-salud"))).isEqualTo(200);
        assertThat(estado(getAnimal("veterinarioA", animal, "/indicadores-salud"))).isEqualTo(200);
        assertThat(estado(getAnimal("admin", animal, "/indicadores-salud"))).isEqualTo(200);
        assertThat(estado(getAnimal("camperoA", animal, "/indicadores-salud"))).isEqualTo(403);
        assertThat(estado(getAnimal("productorB", animal, "/indicadores-salud"))).isEqualTo(403);
    }

    /** Perfil con media 4 y desvío 1, y una lectura de este momento con ese moveState. */
    private void prepararMovimiento(Long animalId, int moveState) {
        jdbcTemplate.update("DELETE FROM perfiles_referencia WHERE animal_id = ?", animalId);
        jdbcTemplate.update("INSERT INTO perfiles_referencia (animal_id, perfil_json, fecha_calculo) VALUES (?, ?, ?)",
                animalId, "{\"mean_moveState_mean\": 4.0, \"mean_moveState_std\": 1.0}",
                Timestamp.valueOf(LocalDateTime.now().minusDays(1)));
        insertarLectura(animalId, moveState);
    }

    private void insertarLectura(Long animalId, int moveState) {
        jdbcTemplate.update("INSERT INTO telemetria (fecha_hora, caravana_id, latitud, longitud, move_state, bateria, "
                + "estado_inferencia_celo, estado_inferencia_salud, estado_inferencia_geo) "
                + "SELECT ?, caravana_id, -36.5, -61.5, ?, 80, 'COMPLETADO', 'COMPLETADO', 'COMPLETADO' "
                + "FROM animales WHERE id = ?", Timestamp.valueOf(LocalDateTime.now()), moveState, animalId);
    }

    private void registrarSalud(Long animalId, String estado) {
        jdbcTemplate.update("INSERT INTO estados_salud (animal_id, estado, origen, fecha_registro) "
                + "VALUES (?, ?, 'MANUAL', ?)", animalId, estado, Timestamp.valueOf(LocalDateTime.now()));
    }

    private JsonNode indicadores(String clave, Long animalId) throws Exception {
        return leer(ok(getAnimal(clave, animalId, "/indicadores-salud")));
    }

    private static JsonNode indicador(JsonNode respuesta, String nombre) {
        for (JsonNode indicador : respuesta.get("indicadores")) {
            if (nombre.equals(indicador.get("nombre").asText())) {
                return indicador;
            }
        }
        throw new AssertionError("No está el indicador " + nombre + " en " + respuesta);
    }
}
