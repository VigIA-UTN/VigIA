package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@DisplayName("H0039 - Edición y baja de animales — IT")
class EdicionBajaAnimalIT extends GestionRodeoItBase {

    @Test
    @DisplayName("H0039-01 - Edición de animal - Datos válidos")
    void H0039_01_edicionValida() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");
        Map<String, Object> cuerpo = cuerpoAnimal("A-101", EST_A, "Rodeo A2");
        cuerpo.put("alias", "Editada");
        cuerpo.put("raza", "Hereford");

        MvcResult resultado = editar(animal, cuerpo);

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        JsonNode guardado = leer(mockMvc.perform(get("/api/v1/animales/" + animal)
                        .header("Authorization", bearer("productorA")))
                .andReturn());
        assertThat(guardado.get("alias").asText()).isEqualTo("Editada");
        assertThat(guardado.get("raza").asText()).isEqualTo("Hereford");
        assertThat(guardado.get("rodeoLoteNombre").asText()).isEqualTo("Rodeo A2");
        assertThat(guardado.get("caravanaCodigo").asText()).isEqualTo("FX-A-01");
    }

    @Test
    @DisplayName("H0039-02 - Edición de animal - Identificador y alias repetidos")
    void H0039_02_identificadorYAliasRepetidos() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");

        MvcResult resultado = editar(animal, cuerpoAnimal("A-102", EST_A, "Rodeo A1"));
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado))
                .isEqualTo("El identificador ya se encuentra registrado en el establecimiento destino.");

        Map<String, Object> aliasRepetido = cuerpoAnimal("A-101", EST_A, "Rodeo A1");
        aliasRepetido.put("alias", "Celo A");
        resultado = editar(animal, aliasRepetido);
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado))
                .isEqualTo("El nombre/alias ya se encuentra registrado en el establecimiento destino.");

        Map<String, Object> enBase = jdbcTemplate.queryForMap(
                "SELECT id_caravana_oficial, alias FROM animales WHERE id = ?", animal);
        assertThat(enBase.get("id_caravana_oficial")).isEqualTo("A-101");
        assertThat(enBase.get("alias")).isEqualTo("Calibrada A");
    }

    @Test
    @DisplayName("H0039-03 - Baja de animal - Baja lógica")
    void H0039_03_bajaLogica() throws Exception {
        Long animal = idAnimal(EST_A, "A-103");

        MvcResult resultado = baja(animal);
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("SELECT estado_actual FROM animales WHERE id = ?", String.class,
                animal)).isEqualTo("INACTIVO");

        JsonNode listado = leer(mockMvc.perform(get("/api/v1/animales")
                        .param("establecimientoId", idEstablecimiento(EST_A).toString())
                        .header("Authorization", bearer("productorA")))
                .andReturn());
        listado.forEach(a -> assertThat(a.get("idCaravanaOficial").asText()).isNotEqualTo("A-103"));

        resultado = editar(animal, cuerpoAnimal("A-103", EST_A, "Rodeo A2"));
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("No se puede editar un animal inactivo.");

        resultado = baja(animal);
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("El animal no está activo.");
    }

    @Test
    @DisplayName("H0039-04 - Baja de animal - Libera la caravana")
    void H0039_04_bajaLiberaLaCaravana() throws Exception {
        assertThat(baja(idAnimal(EST_A, "A-103")).getResponse().getStatus()).isEqualTo(200);

        assertThat(estadoCaravana("FX-A-03")).isEqualTo("DISPONIBLE");
        assertThat(jdbcTemplate.queryForObject("SELECT caravana_id FROM animales WHERE id = ?", Long.class,
                idAnimal(EST_A, "A-103"))).isNull();
        assertThat(codigosDisponibles("productorA", EST_A))
                .containsExactlyInAnyOrder("FX-A-03", "FX-A-L1", "FX-A-L2");
    }

    private MvcResult editar(Long animalId, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(put("/api/v1/animales/" + animalId)
                        .header("Authorization", bearer("productorA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    private MvcResult baja(Long animalId) throws Exception {
        return mockMvc.perform(delete("/api/v1/animales/" + animalId)
                        .header("Authorization", bearer("productorA")))
                .andReturn();
    }
}
