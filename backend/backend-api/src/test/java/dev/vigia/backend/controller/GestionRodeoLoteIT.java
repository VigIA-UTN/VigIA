package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("H0038 - Gestión de rodeos/lotes — IT")
class GestionRodeoLoteIT extends GestionRodeoItBase {

    @Test
    @DisplayName("H0038-01 - Rodeos/lotes - Alta y listado")
    void H0038_01_altaYListado() throws Exception {
        MvcResult resultado = crear("productorA", cuerpo("Rodeo A3", EST_A));

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        JsonNode creado = leer(resultado);
        assertThat(creado.get("nombre").asText()).isEqualTo("Rodeo A3");
        assertThat(creado.get("activo").asBoolean()).isTrue();
        assertThat(creado.get("establecimientoId").asLong()).isEqualTo(idEstablecimiento(EST_A));

        assertThat(nombresListados("productorA", EST_A)).containsExactlyInAnyOrder("Rodeo A1", "Rodeo A2", "Rodeo A3");
    }

    @Test
    @DisplayName("H0038-02 - Rodeos/lotes - Baja")
    void H0038_02_baja() throws Exception {
        Long rodeo = idRodeo(EST_A, "Rodeo A2");

        MvcResult resultado = mockMvc.perform(delete("/api/v1/rodeo-lotes/" + rodeo)
                        .header("Authorization", bearer("productorA")))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);

        resultado = mockMvc.perform(get("/api/v1/rodeo-lotes/" + rodeo)
                        .header("Authorization", bearer("productorA")))
                .andReturn();
        assertThat(leer(resultado).get("activo").asBoolean()).isFalse();

        resultado = altaAnimal("productorA", cuerpoAnimal("A-401", EST_A, "Rodeo A2"));
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("El rodeo/lote no está activo.");
    }

    @Test
    @DisplayName("H0038-03 - Rodeos/lotes - Validaciones y acceso")
    void H0038_03_validacionesYAcceso() throws Exception {
        assertThat(crear("productorA", cuerpo(null, EST_A)).getResponse().getStatus()).isEqualTo(400);
        assertThat(crear("productorA", cuerpo("   ", EST_A)).getResponse().getStatus()).isEqualTo(400);
        assertThat(nombresListados("productorA", EST_A)).containsExactlyInAnyOrder("Rodeo A1", "Rodeo A2");

        Long rodeo = idRodeo(EST_A, "Rodeo A1");
        MvcResult resultado = mockMvc.perform(delete("/api/v1/rodeo-lotes/" + rodeo)
                        .header("Authorization", bearer("productorB")))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(jdbcTemplate.queryForObject("SELECT activo FROM rodeo_lote WHERE id = ?", Integer.class, rodeo))
                .isEqualTo(1);
    }

    private MvcResult crear(String clave, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/rodeo-lotes")
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    private Map<String, Object> cuerpo(String nombre, String establecimiento) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("establecimientoId", idEstablecimiento(establecimiento));
        return cuerpo;
    }

    private List<String> nombresListados(String clave, String establecimiento) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/rodeo-lotes")
                        .param("establecimientoId", idEstablecimiento(establecimiento).toString())
                        .header("Authorization", bearer(clave)))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        List<String> nombres = new ArrayList<>();
        leer(resultado).forEach(r -> nombres.add(r.get("nombre").asText()));
        return nombres;
    }
}
