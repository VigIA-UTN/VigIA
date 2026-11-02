package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * GET /api/v1/animales/{id}/cohesion sobre los registros que guarda el job geo (lo que persiste el worker desde ml-geo
 * lo cubren H0026-01, H0026-03 y H0026-04 en backend-worker). El índice se guarda de 0 a 1 y se expone de 0 a 100.
 */
@DisplayName("H0026 - Índice de cohesión social: consulta por animal — IT")
class CohesionIT extends DeteccionItBase {

    @Test
    @DisplayName("H0026-02 - Cohesión - Índice actual, promedio del rodeo y tendencia de los últimos 7 días")
    void H0026_02_consultaDelIndice() throws Exception {
        Long a101 = idAnimal(EST_A, "A-101");
        Long a102 = idAnimal(EST_A, "A-102");
        LocalDateTime ahora = LocalDateTime.now();
        // Dos análisis de hoy: en el primero A-101 y A-102 (Rodeo A1), en el segundo solo A-101.
        registrar(a101, ahora.minusSeconds(120), 0.8);
        registrar(a102, ahora.minusSeconds(120), 0.6);
        registrar(a101, ahora.minusSeconds(60), 0.9);
        // Uno del período anterior (antes de los 7 días).
        registrar(a101, LocalDate.now().minusDays(8).atTime(12, 0), 0.5);

        JsonNode cohesion = leer(ok(getAnimal("veterinarioA", a101, "/cohesion")));

        assertThat(cohesion.get("actual").asDouble()).isEqualTo(90.0);
        // Promedio del rodeo en cada análisis (70 y 90), promediado en el período.
        assertThat(cohesion.get("promedioRodeo").asDouble()).isEqualTo(80.0);
        assertThat(cohesion.get("promedioPeriodoAnterior").asDouble()).isEqualTo(50.0);
        // Un punto por día, de hoy - 6 a hoy; solo hoy tiene análisis.
        JsonNode serie = cohesion.get("serie");
        assertThat(serie).hasSize(7);
        for (int i = 0; i < 6; i++) {
            assertThat(serie.get(i).get("valor").isNull()).as("día %s", i).isTrue();
        }
        assertThat(serie.get(6).get("valor").asDouble()).isEqualTo(85.0);
        assertThat(OffsetDateTime.parse(serie.get(6).get("ts").asText()).toLocalDate()).isEqualTo(LocalDate.now());

        // Rango de 24 h: un punto por hora.
        MvcResult porHora = mockMvc.perform(get("/api/v1/animales/" + a101 + "/cohesion")
                        .param("desde", OffsetDateTime.now().minusHours(24).toString())
                        .param("hasta", OffsetDateTime.now().toString())
                        .header("Authorization", bearer("camperoA")))
                .andReturn();
        assertThat(leer(ok(porHora)).get("serie")).hasSize(25);

        // Otro establecimiento no la ve; sin análisis, no hay índice actual.
        assertThat(estado(getAnimal("productorB", a101, "/cohesion"))).isEqualTo(403);
        assertThat(leer(ok(getAnimal("productorA", idAnimal(EST_A, "A-105"), "/cohesion"))).get("actual").isNull())
                .isTrue();
    }

    private void registrar(Long animalId, LocalDateTime fecha, double indice) {
        jdbcTemplate.update("INSERT INTO registros_cohesion (animal_id, establecimiento_id, fecha, indice_cohesion, "
                + "tiempo_fuera_grupo_s, distancia_centroide_m) SELECT id, establecimiento_id, ?, ?, 0, 20 "
                + "FROM animales WHERE id = ?", Timestamp.valueOf(fecha), indice, animalId);
    }
}
