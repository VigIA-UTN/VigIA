package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Sobre la telemetría del escenario en TimescaleDB: A-101 tiene 8 días cada 5 min hasta la carga y A-102 las últimas
 * 24 h. El día 0 de la respuesta es hoy - 6 y el 6 es hoy.
 */
@DisplayName("H0015 - Historial de actividad reciente por animal (7 días) — IT")
class ActividadRecienteIT extends GestionRodeoItBase {

    @Test
    @DisplayName("H0015-01 - Actividad 7 días - Telemetría completa")
    void H0015_01_telemetriaCompleta() throws Exception {
        MvcResult resultado = metricas("productorA", idAnimal(EST_A, "A-101"));

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        JsonNode dias = leer(resultado);
        assertThat(dias).hasSize(7);
        for (int i = 0; i < 7; i++) {
            JsonNode dia = dias.get(i);
            assertThat(dia.get("fecha").asText()).isEqualTo(LocalDate.now().minusDays(6 - i).toString());
            assertThat(dia.get("sinDatos").asBoolean()).as("día %s", i).isFalse();
            assertThat(dia.get("actividadMotora").isNumber()).as("día %s", i).isTrue();
            assertThat(dia.get("latitud").isNumber()).as("día %s", i).isTrue();
            assertThat(dia.get("longitud").isNumber()).as("día %s", i).isTrue();
        }
    }

    @Test
    @DisplayName("H0015-02 - Actividad 7 días - Días sin datos")
    void H0015_02_diasSinDatos() throws Exception {
        MvcResult resultado = metricas("veterinarioA", idAnimal(EST_A, "A-102"));

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        JsonNode dias = leer(resultado);
        assertThat(dias).hasSize(7);
        // Las últimas 24 h caen en hoy y, salvo que se corra justo a medianoche, también en ayer (día 5).
        for (int i = 0; i < 5; i++) {
            assertThat(dias.get(i).get("sinDatos").asBoolean()).as("día %s", i).isTrue();
        }
        assertThat(dias.get(6).get("sinDatos").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("H0015-03 - Actividad 7 días - Animal sin caravana y acceso")
    void H0015_03_sinCaravanaYAcceso() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");

        // b) Otro establecimiento.
        assertThat(metricas("productorB", animal).getResponse().getStatus()).isEqualTo(403);

        // Caravana asignada hace 2 días: lo anterior de esa caravana puede ser de otro animal y no cuenta.
        jdbcTemplate.update("UPDATE caravanas SET fecha_asignacion = current_date - 2 WHERE codigo = 'FX-A-01'");
        JsonNode dias = leer(metricas("productorA", animal));
        for (int i = 0; i < 7; i++) {
            assertThat(dias.get(i).get("sinDatos").asBoolean()).as("día %s", i).isEqualTo(i < 4);
        }

        // Cambio de caravana: se borra el perfil de salud, vuelve a calibración y no se usa la telemetría de la
        // caravana anterior (FX-A-L1 todavía no transmitió).
        jdbcTemplate.update("INSERT INTO perfiles_referencia (animal_id, perfil_json, fecha_calculo) "
                + "VALUES (?, '{}', now())", animal);
        MvcResult resultado = mockMvc.perform(post("/api/v1/animales/" + animal + "/vincular-caravana")
                        .header("Authorization", bearer("productorA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caravanaId", idCaravana("FX-A-L1")))))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        assertThat(leer(resultado).get("enCalibracion").asBoolean()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM perfiles_referencia WHERE animal_id = ?",
                Integer.class, animal)).isZero();
        leer(metricas("productorA", animal))
                .forEach(dia -> assertThat(dia.get("sinDatos").asBoolean()).isTrue());

        // a) Sin caravana.
        jdbcTemplate.update("UPDATE animales SET caravana_id = NULL WHERE id = ?", animal);
        dias = leer(metricas("productorA", animal));
        assertThat(dias).hasSize(7);
        dias.forEach(dia -> assertThat(dia.get("sinDatos").asBoolean()).isTrue());
    }

    private MvcResult metricas(String clave, Long animalId) throws Exception {
        return mockMvc.perform(get("/api/v1/animales/" + animalId + "/metricas")
                        .header("Authorization", bearer(clave)))
                .andReturn();
    }
}
