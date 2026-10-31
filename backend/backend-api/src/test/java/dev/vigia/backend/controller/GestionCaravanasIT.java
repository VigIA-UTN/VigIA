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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("H0040 - Gestión de caravanas (alta, baja y reactivación) — IT")
class GestionCaravanasIT extends GestionRodeoItBase {

    @Test
    @DisplayName("H0040-01 - Caravanas - Alta por administrador")
    void H0040_01_altaPorAdministrador() throws Exception {
        MvcResult resultado = crear(cuerpo("FX-A-N1", "FX-A-N1-EUI"));

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        JsonNode creada = leer(resultado);
        assertThat(creada.get("codigo").asText()).isEqualTo("FX-A-N1");
        assertThat(creada.get("estadoSensor").asText()).isEqualTo("DISPONIBLE");
        assertThat(creada.get("establecimientoId").asLong()).isEqualTo(idEstablecimiento(EST_A));
        assertThat(listar("admin", EST_A)).hasSize(9);
    }

    @Test
    @DisplayName("H0040-02 - Caravanas - Alta duplicada")
    void H0040_02_altaDuplicada() throws Exception {
        assertThat(crear(cuerpo("FX-A-N1", "FX-A-01")).getResponse().getStatus()).isEqualTo(409);

        MvcResult resultado = crear(cuerpo("FX-A-01", "FX-A-N1-EUI"));
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("Ya existe una caravana con este codigo");

        assertThat(listar("admin", EST_A)).hasSize(8);
    }

    @Test
    @DisplayName("H0040-03 - Caravanas - Listado y disponibles")
    void H0040_03_listadoYDisponibles() throws Exception {
        JsonNode caravanas = listar("productorA", EST_A);
        List<String> estados = new ArrayList<>();
        caravanas.forEach(c -> estados.add(c.get("estadoSensor").asText()));
        assertThat(estados).hasSize(8);
        assertThat(estados).filteredOn("VINCULADO"::equals).hasSize(6);
        assertThat(estados).filteredOn("DISPONIBLE"::equals).hasSize(2);

        assertThat(codigosDisponibles("productorA", EST_A)).containsExactlyInAnyOrder("FX-A-L1", "FX-A-L2");

        String establecimiento = idEstablecimiento(EST_A).toString();
        assertThat(mockMvc.perform(get("/api/v1/caravana").param("establecimientoId", establecimiento)
                        .header("Authorization", bearer("veterinarioA"))).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(get("/api/v1/caravana/disponibles").param("establecimientoId", establecimiento)
                        .header("Authorization", bearer("veterinarioA"))).andReturn().getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("H0040-04 - Caravanas - Baja")
    void H0040_04_baja() throws Exception {
        MvcResult resultado = accion("baja", idCaravana("FX-A-01"));
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        assertThat(leer(resultado).get("estadoSensor").asText()).isEqualTo("INACTIVO");

        assertThat(jdbcTemplate.queryForObject("SELECT caravana_id FROM animales WHERE id = ?", Long.class,
                idAnimal(EST_A, "A-101"))).isNull();

        resultado = accion("baja", idCaravana("FX-A-01"));
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("La caravana ya se encuentra dada de baja.");
    }

    @Test
    @DisplayName("H0040-05 - Caravanas - Reactivación")
    void H0040_05_reactivacion() throws Exception {
        jdbcTemplate.update("UPDATE caravanas SET estado_sensor = 'INACTIVO' WHERE codigo = 'FX-A-L1'");

        MvcResult resultado = accion("reactivar", idCaravana("FX-A-L1"));
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        assertThat(leer(resultado).get("estadoSensor").asText()).isEqualTo("DISPONIBLE");
        assertThat(codigosDisponibles("productorA", EST_A)).contains("FX-A-L1");

        resultado = accion("reactivar", idCaravana("FX-A-L2"));
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("Solo se puede reactivar una caravana que esté dada de baja.");
    }

    private Map<String, Object> cuerpo(String codigo, String devEui) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idCaravanaOficial", codigo);
        cuerpo.put("alias", "Sensor " + codigo);
        cuerpo.put("establecimientoId", idEstablecimiento(EST_A));
        cuerpo.put("dev_eui", devEui);
        return cuerpo;
    }

    private MvcResult crear(Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/caravana")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    private JsonNode listar(String clave, String establecimiento) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/caravana")
                        .param("establecimientoId", idEstablecimiento(establecimiento).toString())
                        .header("Authorization", bearer(clave)))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        return leer(resultado);
    }

    private MvcResult accion(String accion, Long caravanaId) throws Exception {
        return mockMvc.perform(patch("/api/v1/caravana/" + caravanaId + "/" + accion)
                        .header("Authorization", bearer("admin")))
                .andReturn();
    }
}
