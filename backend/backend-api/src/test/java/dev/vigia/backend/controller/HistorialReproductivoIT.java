package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * GET /api/v1/animales/{id}/estado-reproductivo. A-102 llega del escenario con PARIDA (manual, veterinario.a, hace 30
 * días) y el POSIBLE_CELO automático de su alerta de celo (hace 4 h).
 */
@DisplayName("H0023 - Historial reproductivo del animal — IT")
class HistorialReproductivoIT extends DeteccionItBase {

    @Test
    @DisplayName("H0023-01 - Historial reproductivo - Celo detectado, celo confirmado e inseminación, ordenados")
    void H0023_01_historialOrdenadoYCompleto() throws Exception {
        Long animal = idAnimal(EST_A, "A-102");
        Long alerta = idAlerta(EST_A, "A-102", "CELO");

        // El veterinario confirma el celo y registra la inseminación como intervención de la alerta.
        MvcResult confirmacion = mockMvc.perform(put("/api/v1/alertas/estado/" + alerta)
                        .param("estado", "CONFIRMADA")
                        .header("Authorization", bearer("veterinarioA")))
                .andReturn();
        assertThat(estado(confirmacion)).as(texto(confirmacion)).isEqualTo(200);
        MvcResult intervencion = mockMvc.perform(post("/api/v1/alertas/intervenir/" + alerta)
                        .header("Authorization", bearer("veterinarioA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tipo", "INSEMINACION", "descripcion", "IA con semen Angus",
                                "fechaHora", LocalDateTime.now().plusMinutes(1).withNano(0).toString()))))
                .andReturn();
        assertThat(estado(intervencion)).as(texto(intervencion)).isEqualTo(200);

        JsonNode historial = leer(ok(getAnimal("veterinarioA", animal, "/estado-reproductivo")));

        // Del más nuevo al más viejo, con fecha, origen y quién lo registró si fue manual.
        assertThat(estados(historial)).containsExactly("INSEMINADA", "CELO", "POSIBLE_CELO", "PARIDA");
        JsonNode inseminada = historial.get(0);
        assertThat(inseminada.get("origen").asText()).isEqualTo("MANUAL");
        assertThat(inseminada.get("registradoPor").asText()).isEqualTo("Veterinario A");
        assertThat(inseminada.get("fechaUltimaInseminacion").asText()).isEqualTo(LocalDate.now().toString());
        JsonNode confirmado = historial.get(1);
        assertThat(confirmado.get("origen").asText()).isEqualTo("MANUAL");
        assertThat(confirmado.get("registradoPor").asText()).isEqualTo("Veterinario A");
        JsonNode detectado = historial.get(2);
        assertThat(detectado.get("origen").asText()).isEqualTo("AUTOMATICO");
        assertThat(detectado.get("registradoPor").isNull()).isTrue();
        // El PARIDA del escenario conserva la fecha del último parto.
        assertThat(historial.get(3).get("fechaUltimoParto").asText())
                .isEqualTo(LocalDate.now().minusDays(60).toString());
        for (JsonNode registro : historial) {
            assertThat(registro.get("fechaRegistro").isNull()).isFalse();
        }
    }

    @Test
    @DisplayName("H0023-02 - Historial reproductivo - Registros de carga masiva con fechas de parto e inseminación")
    void H0023_02_cargaMasivaConFechas() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");
        LocalDate parto = LocalDate.now().minusDays(90);
        LocalDate inseminacion = LocalDate.now().minusDays(20);
        MockMultipartFile archivo = new MockMultipartFile("file", "estados.csv", "text/csv",
                ("IdCaravana,Alias,Estado,FechaUltimoParto,FechaUltimaInseminacion\n"
                        + "A-101,,INSEMINADA," + parto + "," + inseminacion + "\n").getBytes(StandardCharsets.UTF_8));
        MvcResult carga = mockMvc.perform(multipart("/api/v1/animales/estado-reproductivo/csv")
                        .file(archivo)
                        .param("establecimientoId", idEstablecimiento(EST_A).toString())
                        .header("Authorization", bearer("productorA")))
                .andReturn();
        assertThat(estado(carga)).as(texto(carga)).isEqualTo(201);

        JsonNode historial = leer(ok(getAnimal("productorA", animal, "/estado-reproductivo")));

        // El GESTANTE del escenario queda debajo del registro nuevo.
        assertThat(estados(historial)).containsExactly("INSEMINADA", "GESTANTE");
        JsonNode cargado = historial.get(0);
        assertThat(cargado.get("origen").asText()).isEqualTo("CARGA_MASIVA");
        assertThat(cargado.get("fechaUltimoParto").asText()).isEqualTo(parto.toString());
        assertThat(cargado.get("fechaUltimaInseminacion").asText()).isEqualTo(inseminacion.toString());
        assertThat(historial.get(1).get("fechaUltimaInseminacion").asText())
                .isEqualTo(LocalDate.now().minusDays(60).toString());
    }

    @Test
    @DisplayName("H0023-03 - Historial reproductivo - Acceso y animal sin registros")
    void H0023_03_accesoYSinRegistros() throws Exception {
        Long animal = idAnimal(EST_A, "A-102");

        assertThat(estado(getAnimal("camperoA", animal, "/estado-reproductivo"))).isEqualTo(200);
        assertThat(estado(getAnimal("admin", animal, "/estado-reproductivo"))).isEqualTo(200);
        assertThat(estado(getAnimal("productorB", animal, "/estado-reproductivo"))).isEqualTo(403);

        // Un animal recién dado de alta todavía no tiene historial.
        MvcResult alta = altaAnimal("productorA", cuerpoAnimal("A-190", EST_A, "Rodeo A1"));
        assertThat(estado(alta)).as(texto(alta)).isEqualTo(201);
        assertThat(leer(ok(getAnimal("productorA", leer(alta).get("id").asLong(), "/estado-reproductivo"))))
                .isEmpty();

        Long inexistente = jdbcTemplate.queryForObject("SELECT coalesce(max(id), 0) + 1000 FROM animales", Long.class);
        assertThat(estado(getAnimal("productorA", inexistente, "/estado-reproductivo"))).isEqualTo(400);
    }

    private static List<String> estados(JsonNode historial) {
        List<String> estados = new ArrayList<>();
        historial.forEach(e -> estados.add(e.get("estado").asText()));
        return estados;
    }
}
