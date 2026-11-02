package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** POST y GET /api/v1/animales/{id}/estado-salud. En el escenario ningún animal tiene estados de salud. */
@DisplayName("H0019 - Registro manual de estado de salud — IT")
class RegistroSaludIT extends DeteccionItBase {

    @Test
    @DisplayName("H0019-01 - Estado de salud - Registro manual válido")
    void H0019_01_registroValido() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");

        MvcResult resultado = registrar("veterinarioA", animal, cuerpo("EN_OBSERVACION",
                "Leve renquera en miembro posterior", "Antiinflamatorio por 3 días"));

        assertThat(estado(resultado)).as(texto(resultado)).isEqualTo(201);
        JsonNode registrado = leer(resultado);
        assertThat(registrado.get("estado").asText()).isEqualTo("EN_OBSERVACION");
        assertThat(registrado.get("diagnosticoNotas").asText()).isEqualTo("Leve renquera en miembro posterior");
        assertThat(registrado.get("tratamientoIndicado").asText()).isEqualTo("Antiinflamatorio por 3 días");
        assertThat(registrado.get("origen").asText()).isEqualTo("MANUAL");
        assertThat(registrado.get("registradoPor").asText()).isEqualTo("Veterinario A");
        // Sin fecha de revisión, la de hoy.
        assertThat(registrado.get("fechaRevision").asText()).isEqualTo(LocalDate.now().toString());

        // El productor también puede registrarlo; el tratamiento es opcional.
        MvcResult delProductor = registrar("productorA", animal, cuerpo("SANO", null, null));
        assertThat(estado(delProductor)).as(texto(delProductor)).isEqualTo(201);

        JsonNode historial = historial("camperoA", animal);
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).get("estado").asText()).isEqualTo("SANO");
        assertThat(historial.get(0).get("registradoPor").asText()).isEqualTo("Productor A");
        assertThat(historial.get(1).get("id").asLong()).isEqualTo(registrado.get("id").asLong());
    }

    @Test
    @DisplayName("H0019-02 - Estado de salud - Validaciones")
    void H0019_02_validaciones() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");

        // a) Sin estado; b) estado que no existe.
        assertThat(estado(registrar("veterinarioA", animal, cuerpo(null, "Sin estado", null)))).isEqualTo(400);
        assertThat(estado(registrar("veterinarioA", animal, cuerpo("GRAVE", null, null)))).isEqualTo(400);

        // c) Animal dado de baja.
        Long baja = idAnimal(EST_A, "A-106");
        jdbcTemplate.update("UPDATE animales SET estado_actual = 'INACTIVO' WHERE id = ?", baja);
        MvcResult inactivo = registrar("veterinarioA", baja, cuerpo("SANO", null, null));
        assertThat(estado(inactivo)).isEqualTo(400);
        assertThat(texto(inactivo)).contains("animales inactivos");
        assertThat(cantidadEstados(animal) + cantidadEstados(baja)).isZero();

        // d) Con fecha de revisión, se guarda esa.
        Map<String, Object> conFecha = cuerpo("ENFERMO", "Fiebre", null);
        conFecha.put("fecha", LocalDate.now().minusDays(2).toString());
        JsonNode registrado = leer(registrar("veterinarioA", animal, conFecha));
        assertThat(registrado.get("fechaRevision").asText()).isEqualTo(LocalDate.now().minusDays(2).toString());
    }

    @Test
    @DisplayName("H0019-03 - Estado de salud - Solo veterinario y productor del establecimiento")
    void H0019_03_permisos() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");

        for (String clave : new String[] {"camperoA", "productorB", "veterinarioB", "admin"}) {
            assertThat(estado(registrar(clave, animal, cuerpo("SANO", null, null)))).as(clave).isEqualTo(403);
        }
        assertThat(cantidadEstados(animal)).isZero();
        // De otro establecimiento tampoco se puede consultar el historial.
        assertThat(estado(getAnimal("productorB", animal, "/estado-salud"))).isEqualTo(403);
    }

    @Test
    @DisplayName("H0019-04 - Estado de salud - Revisión veterinaria e historial que no se modifica")
    void H0019_04_revisionVeterinariaEHistorialInmutable() throws Exception {
        Long animal = idAnimal(EST_A, "A-103");
        JsonNode manual = leer(registrar("veterinarioA", animal, cuerpo("EN_OBSERVACION", "Decaída", null)));

        // Una intervención de revisión veterinaria sobre la alerta sanitaria deja el estado resultante.
        Long alerta = idAlerta(EST_A, "A-103", "SALUD");
        assertThat(estado(mockMvc.perform(put("/api/v1/alertas/estado/" + alerta)
                        .param("estado", "CONFIRMADA")
                        .header("Authorization", bearer("veterinarioA")))
                .andReturn())).isEqualTo(200);
        MvcResult revision = mockMvc.perform(post("/api/v1/alertas/intervenir/" + alerta)
                        .header("Authorization", bearer("veterinarioA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tipo", "REVISION_VETERINARIA", "estadoSalud", "ENFERMO",
                                "descripcion", "Neumonía", "fechaHora",
                                LocalDateTime.now().plusMinutes(1).withNano(0).toString()))))
                .andReturn();
        assertThat(estado(revision)).as(texto(revision)).isEqualTo(200);

        JsonNode historial = historial("productorA", animal);
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).get("estado").asText()).isEqualTo("ENFERMO");
        assertThat(historial.get(0).get("diagnosticoNotas").asText()).isEqualTo("Neumonía");
        assertThat(historial.get(0).get("registradoPor").asText()).isEqualTo("Veterinario A");

        // El historial no se puede editar ni borrar: los triggers de la base lo impiden.
        long id = manual.get("id").asLong();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE estados_salud SET estado = 'SANO' WHERE estado_salud_id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Acción prohibida");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM estados_salud WHERE estado_salud_id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Acción prohibida");
        assertThat(historial("productorA", animal).get(1).get("estado").asText()).isEqualTo("EN_OBSERVACION");
    }

    private static Map<String, Object> cuerpo(String estado, String notas, String tratamiento) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("estado", estado);
        cuerpo.put("diagnosticoNotas", notas);
        cuerpo.put("tratamientoIndicado", tratamiento);
        return cuerpo;
    }

    private MvcResult registrar(String clave, Long animalId, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-salud")
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    private JsonNode historial(String clave, Long animalId) throws Exception {
        return leer(ok(getAnimal(clave, animalId, "/estado-salud")));
    }

    private int cantidadEstados(Long animalId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM estados_salud WHERE animal_id = ?", Integer.class,
                animalId);
    }
}
