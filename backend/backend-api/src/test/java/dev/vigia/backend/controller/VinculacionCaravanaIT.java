package dev.vigia.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("H0041 - Vincular y desvincular una caravana a un animal — IT")
class VinculacionCaravanaIT extends GestionRodeoItBase {

    @Test
    @DisplayName("H0041-01 - Vinculación - Caravana libre a animal sin caravana")
    void H0041_01_caravanaLibreAAnimalSinCaravana() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");
        jdbcTemplate.update("UPDATE animales SET caravana_id = NULL WHERE id = ?", animal);
        jdbcTemplate.update("UPDATE caravanas SET estado_sensor = 'DISPONIBLE' WHERE codigo = 'FX-A-01'");

        MvcResult resultado = vincular(animal, "FX-A-L1");

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        assertThat(leer(resultado).get("caravanaCodigo").asText()).isEqualTo("FX-A-L1");
        assertThat(estadoCaravana("FX-A-L1")).isEqualTo("VINCULADO");
        assertThat(codigosDisponibles("productorA", EST_A)).doesNotContain("FX-A-L1");
    }

    @Test
    @DisplayName("H0041-02 - Vinculación - Una caravana no puede estar en dos animales")
    void H0041_02_unaCaravanaNoPuedeEstarEnDosAnimales() throws Exception {
        MvcResult resultado = vincular(idAnimal(EST_A, "A-102"), "FX-A-01");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).startsWith("La caravana ya se encuentra vinculada a otro animal (Calibrada A)");
        assertThat(caravanaDe("A-101")).isEqualTo(idCaravana("FX-A-01"));
        assertThat(caravanaDe("A-102")).isEqualTo(idCaravana("FX-A-02"));
    }

    @Test
    @DisplayName("H0041-03 - Desvinculación - Libera la caravana")
    void H0041_03_desvinculacionLiberaLaCaravana() throws Exception {
        MvcResult resultado = mockMvc.perform(delete("/api/v1/animales/" + idAnimal(EST_A, "A-101")
                        + "/desvincular-caravana")
                        .header("Authorization", bearer("productorA")))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        assertThat(leer(resultado).get("caravanaId").isNull()).isTrue();
        assertThat(estadoCaravana("FX-A-01")).isEqualTo("DISPONIBLE");
        assertThat(codigosDisponibles("productorA", EST_A)).contains("FX-A-01");

        Long nuevo = leer(altaAnimal("productorA", cuerpoAnimal("A-501", EST_A, "Rodeo A1"))).get("id").asLong();
        resultado = vincular(nuevo, "FX-A-01");
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        assertThat(caravanaDe("A-501")).isEqualTo(idCaravana("FX-A-01"));
        assertThat(estadoCaravana("FX-A-01")).isEqualTo("VINCULADO");
    }

    @Test
    @DisplayName("H0041-04 - Vinculación - Restricciones")
    void H0041_04_restricciones() throws Exception {
        MvcResult resultado = vincular(idAnimal(EST_A, "A-101"), "FX-B-L1");
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("La caravana no pertenece al mismo establecimiento que el animal.");

        jdbcTemplate.update("UPDATE caravanas SET estado_sensor = 'INACTIVO' WHERE codigo = 'FX-A-L1'");
        resultado = vincular(idAnimal(EST_A, "A-102"), "FX-A-L1");
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("La caravana se encuentra inactiva y no puede ser vinculada.");

        jdbcTemplate.update("UPDATE animales SET estado_actual = 'INACTIVO' WHERE id = ?", idAnimal(EST_A, "A-106"));
        resultado = vincular(idAnimal(EST_A, "A-106"), "FX-A-L2");
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("No se puede vincular una caravana a un animal inactivo.");

        assertThat(caravanaDe("A-101")).isEqualTo(idCaravana("FX-A-01"));
        assertThat(caravanaDe("A-102")).isEqualTo(idCaravana("FX-A-02"));
        assertThat(estadoCaravana("FX-B-L1")).isEqualTo("DISPONIBLE");
        assertThat(estadoCaravana("FX-A-L2")).isEqualTo("DISPONIBLE");
    }

    private MvcResult vincular(Long animalId, String codigoCaravana) throws Exception {
        return mockMvc.perform(post("/api/v1/animales/" + animalId + "/vincular-caravana")
                        .header("Authorization", bearer("productorA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caravanaId", idCaravana(codigoCaravana)))))
                .andReturn();
    }

    private Long caravanaDe(String identificador) {
        return jdbcTemplate.queryForObject("SELECT caravana_id FROM animales WHERE id = ?", Long.class,
                idAnimal(EST_A, identificador));
    }
}
