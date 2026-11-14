package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Diagnóstico veterinario sobre la alerta sanitaria de A-103 (POST /api/v1/alertas/{id}/diagnosticos), que primero
 * confirma veterinario.a. La inmutabilidad se verifica sobre la base real: los triggers de V12 y V16.
 */
@DisplayName("H0031 - Diagnóstico veterinario trazable e inmutable — IT")
class DiagnosticoInmutableIT extends DeteccionItBase {

    @Test
    @DisplayName("H0031-01 - Diagnóstico - Registro sobre la alerta sanitaria confirmada")
    void H0031_01_registroDiagnostico() throws Exception {
        Long alerta = alertaSanitariaConfirmada();
        Long animal = idAnimal(EST_A, "A-103");

        MvcResult resultado = diagnosticar("veterinarioA", alerta, "Neumonía leve", "Oxitetraciclina por 5 días");

        assertThat(estado(resultado)).as(texto(resultado)).isEqualTo(200);
        JsonNode diagnostico = leer(resultado);
        assertThat(diagnostico.get("alertaId").asLong()).isEqualTo(alerta);
        assertThat(diagnostico.get("animalId").asLong()).isEqualTo(animal);
        assertThat(diagnostico.get("animalCaravanaOficial").asText()).isEqualTo("A-103");
        assertThat(diagnostico.get("diagnostico").asText()).isEqualTo("Neumonía leve");
        assertThat(diagnostico.get("tratamiento").asText()).isEqualTo("Oxitetraciclina por 5 días");
        assertThat(diagnostico.get("veterinarioNombre").asText()).isEqualTo("Veterinario A");
        assertThat(diagnostico.get("fechaRegistro").isNull()).isFalse();
        // Vinculado al dato sensorial que originó la alerta.
        assertThat(diagnostico.get("disparador").asText()).isEqualTo("SALUD");
        assertThat(diagnostico.get("datoOrigen").asText()).contains("Caída de actividad respecto de su perfil");

        // En la base, ligado a la alerta, al animal y al veterinario; la alerta no cambia de estado.
        Map<String, Object> fila = jdbcTemplate.queryForMap("SELECT * FROM diagnosticos WHERE id = ?",
                diagnostico.get("id").asLong());
        assertThat(((Number) fila.get("alerta_id")).longValue()).isEqualTo(alerta);
        assertThat(((Number) fila.get("animal_id")).longValue()).isEqualTo(animal);
        assertThat(((Number) fila.get("veterinario_id")).longValue()).isEqualTo(idUsuario(email("veterinarioA")));
        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM alertas WHERE id = ?", String.class, alerta))
                .isEqualTo("CONFIRMADA");

        // Consultable por animal, también por el productor.
        JsonNode historial = leer(ok(diagnosticos("productorA", animal)));
        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).get("id").asLong()).isEqualTo(diagnostico.get("id").asLong());
    }

    @Test
    @DisplayName("H0031-02 - Diagnóstico - Validaciones y permisos")
    void H0031_02_validacionesYPermisos() throws Exception {
        // a) Sobre una alerta sanitaria todavía pendiente.
        Long pendiente = idAlerta(EST_A, "A-103", "SALUD");
        assertThat(texto(diagnosticar("veterinarioA", pendiente, "Neumonía", ""))).contains("debe estar confirmada");

        Long alerta = alertaSanitariaConfirmada();
        // b) Texto vacío, de más de 200 caracteres, o tratamiento de más de 500.
        assertThat(estado(diagnosticar("veterinarioA", alerta, "  ", ""))).isEqualTo(400);
        assertThat(estado(diagnosticar("veterinarioA", alerta, "x".repeat(201), ""))).isEqualTo(400);
        assertThat(estado(diagnosticar("veterinarioA", alerta, "Neumonía", "x".repeat(501)))).isEqualTo(400);

        // c) Sobre una alerta que no es sanitaria (el celo de A-102, confirmado).
        Long celo = idAlerta(EST_A, "A-102", "CELO");
        jdbcTemplate.update("UPDATE alertas SET estado = 'CONFIRMADA', ts_confirmacion = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), celo);
        assertThat(texto(diagnosticar("veterinarioA", celo, "Celo confirmado", ""))).contains("alertas sanitarias");

        // d) Solo el veterinario del establecimiento.
        assertThat(estado(diagnosticar("productorA", alerta, "Neumonía", ""))).isEqualTo(403);
        assertThat(estado(diagnosticar("camperoA", alerta, "Neumonía", ""))).isEqualTo(403);
        assertThat(estado(diagnosticar("veterinarioB", alerta, "Neumonía", ""))).isEqualTo(403);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM diagnosticos d JOIN animales a "
                + "ON a.id = d.animal_id JOIN establecimientos e ON e.id = a.establecimiento_id WHERE e.nombre = ?",
                Integer.class, EST_A)).isZero();
    }

    @Test
    @DisplayName("H0031-03 - Diagnóstico - No se puede modificar ni eliminar en la base")
    void H0031_03_inmutableEnLaBase() throws Exception {
        Long alerta = alertaSanitariaConfirmada();
        long id = leer(diagnosticar("veterinarioA", alerta, "Neumonía leve", "Reposo")).get("id").asLong();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE diagnosticos SET diagnostico = 'Diagnóstico alterado' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Acción prohibida")
                .hasMessageContaining("diagnosticos");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM diagnosticos WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Acción prohibida");
        // La alerta confirmada tampoco puede volver atrás ni cambiar su dato de origen.
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE alertas SET estado = 'PENDIENTE' WHERE id = ?", alerta))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Acción prohibida");
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE alertas SET confianza_modelo = 0.1 WHERE id = ?", alerta))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Acción prohibida");

        Map<String, Object> fila = jdbcTemplate.queryForMap("SELECT * FROM diagnosticos WHERE id = ?", id);
        assertThat(fila.get("diagnostico")).isEqualTo("Neumonía leve");
        assertThat(fila.get("indicaciones")).isEqualTo("Reposo");
    }

    @Test
    @DisplayName("H0031-04 - Diagnóstico - Historial trazable: solo se agrega información")
    void H0031_04_historialSoloAgrega() throws Exception {
        Long alerta = alertaSanitariaConfirmada();
        Long animal = idAnimal(EST_A, "A-103");
        long primero = leer(diagnosticar("veterinarioA", alerta, "Neumonía leve", "Reposo")).get("id").asLong();

        // La API no ofrece editar la resolución ni el diagnóstico: un cambio de criterio se registra como otro.
        MvcResult reabrir = mockMvc.perform(put("/api/v1/alertas/estado/" + alerta)
                        .param("estado", "DESCARTADA")
                        .header("Authorization", bearer("veterinarioA")))
                .andReturn();
        assertThat(estado(reabrir)).isEqualTo(409);
        MvcResult editar = mockMvc.perform(put("/api/v1/alertas/" + alerta + "/diagnosticos")
                        .header("Authorization", bearer("veterinarioA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("diagnostico", "Otro diagnóstico"))))
                .andReturn();
        assertThat(estado(editar)).isGreaterThanOrEqualTo(400);
        long segundo = leer(diagnosticar("veterinarioA", alerta, "Neumonía moderada", "Antibiótico")).get("id")
                .asLong();

        // Los dos quedan, del más nuevo al más viejo, cada uno con su fecha y su veterinario.
        JsonNode historial = leer(ok(diagnosticos("veterinarioA", animal)));
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).get("id").asLong()).isEqualTo(segundo);
        assertThat(historial.get(1).get("id").asLong()).isEqualTo(primero);
        assertThat(historial.get(1).get("diagnostico").asText()).isEqualTo("Neumonía leve");
        historial.forEach(d -> {
            assertThat(d.get("veterinarioNombre").asText()).isEqualTo("Veterinario A");
            assertThat(d.get("fechaRegistro").isNull()).isFalse();
            assertThat(d.get("alertaId").asLong()).isEqualTo(alerta);
        });
        // Otro establecimiento no accede al historial del animal.
        assertThat(estado(diagnosticos("productorB", animal))).isEqualTo(403);
    }

    private Long alertaSanitariaConfirmada() throws Exception {
        Long alerta = idAlerta(EST_A, "A-103", "SALUD");
        MvcResult confirmacion = mockMvc.perform(put("/api/v1/alertas/estado/" + alerta)
                        .param("estado", "CONFIRMADA")
                        .header("Authorization", bearer("veterinarioA")))
                .andReturn();
        assertThat(estado(confirmacion)).as(texto(confirmacion)).isEqualTo(200);
        return alerta;
    }

    private MvcResult diagnosticar(String clave, Long alertaId, String diagnostico, String tratamiento)
            throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("diagnostico", diagnostico);
        cuerpo.put("tratamiento", tratamiento);
        return mockMvc.perform(post("/api/v1/alertas/" + alertaId + "/diagnosticos")
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    private MvcResult diagnosticos(String clave, Long animalId) throws Exception {
        return mockMvc.perform(get("/api/v1/alertas/diagnosticos")
                        .param("animalId", animalId.toString())
                        .header("Authorization", bearer(clave)))
                .andReturn();
    }
}
