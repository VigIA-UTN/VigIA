package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@DisplayName("H0042 - Registro y evolución del peso — IT")
class HistorialPesoIT extends GestionRodeoItBase {

    @Test
    @DisplayName("H0042-01 - Peso - Registro y historial")
    void H0042_01_registroEHistorial() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");

        for (Map<String, Object> cuerpo : List.of(peso(350.0, LocalDate.now().minusDays(30)),
                peso(380.0, LocalDate.now()))) {
            MvcResult resultado = registrar("camperoA", animal, cuerpo);
            assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        }

        JsonNode historial = historial(animal);
        assertThat(valores(historial)).containsExactly(380.0, 350.0);
        assertThat(historial.get(0).get("fecha").asText()).isEqualTo(LocalDate.now().toString());
        assertThat(historial.get(0).get("unidad").asText()).isEqualTo("kg");
    }

    @Test
    @DisplayName("H0042-02 - Peso - Corrección y borrado")
    void H0042_02_correccionYBorrado() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");
        Long viejo = leer(registrar("productorA", animal, peso(350.0, LocalDate.now().minusDays(30))))
                .get("id").asLong();
        Long nuevo = leer(registrar("productorA", animal, peso(380.0, LocalDate.now()))).get("id").asLong();

        MvcResult resultado = mockMvc.perform(put(url(animal, nuevo))
                        .header("Authorization", bearer("productorA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(peso(385.0, LocalDate.now()))))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        assertThat(leer(resultado).get("valorKg").asDouble()).isEqualTo(385.0);

        resultado = mockMvc.perform(delete(url(animal, viejo)).header("Authorization", bearer("productorA")))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        assertThat(valores(historial(animal))).containsExactly(385.0);

        resultado = mockMvc.perform(put(url(idAnimal(EST_A, "A-102"), nuevo))
                        .header("Authorization", bearer("productorA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(peso(400.0, LocalDate.now()))))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("El registro de peso no corresponde a este animal.");
        assertThat(valores(historial(animal))).containsExactly(385.0);
    }

    @Test
    @DisplayName("H0042-03 - Peso - Validaciones")
    void H0042_03_validaciones() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");
        Long inactivo = idAnimal(EST_A, "A-106");
        jdbcTemplate.update("UPDATE animales SET estado_actual = 'INACTIVO' WHERE id = ?", inactivo);

        Map<String, Object> sinFecha = peso(380.0, LocalDate.now());
        sinFecha.remove("fecha");
        Map<String, Object> sinUnidad = peso(380.0, LocalDate.now());
        sinUnidad.remove("unidad");

        Map<String, MvcResult> variantes = new LinkedHashMap<>();
        variantes.put("0 kg", registrar("productorA", animal, peso(0.0, LocalDate.now())));
        variantes.put("-5 kg", registrar("productorA", animal, peso(-5.0, LocalDate.now())));
        variantes.put("sin fecha", registrar("productorA", animal, sinFecha));
        variantes.put("sin unidad", registrar("productorA", animal, sinUnidad));
        variantes.put("animal inactivo", registrar("productorA", inactivo, peso(380.0, LocalDate.now())));

        variantes.forEach((variante, resultado) ->
                assertThat(resultado.getResponse().getStatus()).as(variante).isEqualTo(400));
        assertThat(texto(variantes.get("animal inactivo")))
                .isEqualTo("No se pueden registrar pesos en animales inactivos.");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM registros_peso WHERE animal_id IN (?, ?)",
                Integer.class, animal, inactivo)).isZero();
    }

    private static Map<String, Object> peso(double valorKg, LocalDate fecha) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("valorKg", valorKg);
        cuerpo.put("unidad", "kg");
        cuerpo.put("fecha", fecha.toString());
        return cuerpo;
    }

    private static String url(Long animalId, Long pesoId) {
        return "/api/v1/animales/" + animalId + "/historial-pesos/" + pesoId;
    }

    private MvcResult registrar(String clave, Long animalId, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/animales/" + animalId + "/historial-pesos")
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    private JsonNode historial(Long animalId) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/animales/" + animalId + "/historial-pesos")
                        .header("Authorization", bearer("productorA")))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        return leer(resultado);
    }

    private static List<Double> valores(JsonNode historial) {
        List<Double> valores = new ArrayList<>();
        historial.forEach(p -> valores.add(p.get("valorKg").asDouble()));
        return valores;
    }
}
