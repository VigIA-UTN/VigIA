package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * H0006-04 (filtros, orden y paginación) no tiene test acá: GET /animales no los recibe, se resuelven en el frontend
 * y se cubren en la regresión E2E.
 */
@DisplayName("H0006 - Listado del rodeo (filtros, orden) — IT")
class ListadoRodeoIT extends GestionRodeoItBase {

    @Test
    @DisplayName("H0006-01 - Listado - Animales activos del establecimiento")
    void H0006_01_animalesActivosDelEstablecimiento() throws Exception {
        jdbcTemplate.update("UPDATE animales SET estado_actual = 'INACTIVO' WHERE id = ?", idAnimal(EST_A, "A-106"));

        MvcResult resultado = listar("productorA", EST_A);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode animales = leer(resultado);
        assertThat(identificadores(animales)).containsExactlyInAnyOrder("A-101", "A-102", "A-103", "A-104", "A-105");
        JsonNode calibrada = buscar(animales, "A-101");
        assertThat(calibrada.get("alias").asText()).isEqualTo("Calibrada A");
        assertThat(calibrada.get("rodeoLoteNombre").asText()).isEqualTo("Rodeo A1");
        assertThat(calibrada.get("caravanaCodigo").asText()).isEqualTo("FX-A-01");
        assertThat(calibrada.get("estadoActual").asText()).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("H0006-02 - Listado - Aislamiento entre establecimientos")
    void H0006_02_aislamientoEntreEstablecimientos() throws Exception {
        assertThat(listar("productorA", EST_B).getResponse().getStatus()).isEqualTo(403);

        assertThat(identificadores(leer(listar("productorAmbos", EST_A))))
                .containsExactlyInAnyOrder("A-101", "A-102", "A-103", "A-104", "A-105", "A-106");
        assertThat(identificadores(leer(listar("productorAmbos", EST_B))))
                .containsExactlyInAnyOrder("B-101", "B-102", "B-103", "B-104", "B-105", "B-106");
    }

    @ParameterizedTest(name = "H0006-03 - Listado - Acceso por rol: {0}")
    @ValueSource(strings = { "veterinarioA", "camperoA" })
    @DisplayName("H0006-03 - Listado - Acceso por rol")
    void H0006_03_accesoPorRol(String clave) throws Exception {
        MvcResult resultado = listar(clave, EST_A);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(identificadores(leer(resultado)))
                .containsExactlyInAnyOrder("A-101", "A-102", "A-103", "A-104", "A-105", "A-106");
    }

    @Test
    @DisplayName("H0006-03 - Listado - Acceso por rol: el administrador ve los animales de todos los establecimientos")
    void H0006_03_administradorVeTodos() throws Exception {
        // El administrador no filtra por establecimiento: lo hace el frontend con el establecimiento seleccionado.
        MvcResult resultado = listar("admin", EST_A);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(identificadores(leer(resultado))).contains(
                "A-101", "A-102", "A-103", "A-104", "A-105", "A-106",
                "B-101", "B-102", "B-103", "B-104", "B-105", "B-106");
    }

    private MvcResult listar(String clave, String establecimiento) throws Exception {
        return mockMvc.perform(get("/api/v1/animales")
                        .param("establecimientoId", idEstablecimiento(establecimiento).toString())
                        .header("Authorization", bearer(clave)))
                .andReturn();
    }

    private static List<String> identificadores(JsonNode animales) {
        List<String> identificadores = new ArrayList<>();
        animales.forEach(a -> identificadores.add(a.get("idCaravanaOficial").asText()));
        return identificadores;
    }

    private static JsonNode buscar(JsonNode animales, String identificador) {
        for (JsonNode animal : animales) {
            if (animal.get("idCaravanaOficial").asText().equals(identificador)) {
                return animal;
            }
        }
        throw new AssertionError("No está el animal " + identificador);
    }
}
