package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("H0003 - Alta de establecimiento (admin) — IT")
class EstablecimientoAltaIT extends SeguridadItBase {

    @Test
    @DisplayName("H0003-01 - Alta de establecimiento - Administrador con datos válidos")
    void H0003_01_adminCreaEstablecimiento() throws Exception {
        // Nombre "Fixture ..." para que limpiar.sql lo borre en la próxima carga.
        String nombre = "Fixture IT " + UUID.randomUUID();

        MvcResult resultado = crear("admin", cuerpo(nombre, -36.7, -61.7));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
        JsonNode creado = leer(resultado);
        assertThat(creado.get("id").asLong()).isPositive();
        assertThat(creado.get("nombre").asText()).isEqualTo(nombre);
        assertThat(creado.get("activo").asBoolean()).isTrue();
        assertThat(idEstablecimiento(nombre)).isEqualTo(creado.get("id").asLong());
    }

    @Test
    @DisplayName("H0003-02 - Alta de establecimiento - Datos obligatorios faltantes")
    void H0003_02_datosInvalidos() throws Exception {
        String nombre = "Fixture IT " + UUID.randomUUID();
        List<Map<String, Object>> invalidos = new ArrayList<>();
        invalidos.add(cuerpo(null, -36.7, -61.7));
        invalidos.add(cuerpo(nombre, null, -61.7));
        invalidos.add(cuerpo(nombre, -36.7, null));

        for (Map<String, Object> invalido : invalidos) {
            assertThat(crear("admin", invalido).getResponse().getStatus()).as("cuerpo %s", invalido).isEqualTo(400);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM establecimientos WHERE nombre = ?",
                Integer.class, nombre)).isZero();
    }

    @ParameterizedTest(name = "H0003-03 - Alta de establecimiento - Rol {0} sin permiso")
    @ValueSource(strings = { "productorA", "veterinarioA", "camperoA" })
    @DisplayName("H0003-03 - Alta de establecimiento - Rol no administrador")
    void H0003_03_noAdminNoPuedeCrear(String clave) throws Exception {
        String nombre = "Fixture IT " + UUID.randomUUID();

        assertThat(crear(clave, cuerpo(nombre, -36.7, -61.7)).getResponse().getStatus()).isEqualTo(403);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM establecimientos WHERE nombre = ?",
                Integer.class, nombre)).isZero();
    }

    @Test
    @DisplayName("H0003-04 - Listado de establecimientos - El productor ve solo los suyos")
    void H0003_04_productorVeSoloLosSuyos() throws Exception {
        assertThat(nombresListados("productorA")).containsExactly(EST_A);
        assertThat(nombresListados("productorB")).containsExactly(EST_B);
        assertThat(nombresListados("productorAmbos")).containsExactlyInAnyOrder(EST_A, EST_B);
        assertThat(nombresListados("admin")).contains(EST_A, EST_B);
    }

    private MvcResult crear(String clave, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/establecimientos")
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    private List<String> nombresListados(String clave) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/establecimientos")
                        .header("Authorization", bearer(clave)))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        List<String> nombres = new ArrayList<>();
        leer(resultado).forEach(e -> nombres.add(e.get("nombre").asText()));
        return nombres;
    }

    private static Map<String, Object> cuerpo(String nombre, Double latitud, Double longitud) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("latitud", latitud);
        cuerpo.put("longitud", longitud);
        return cuerpo;
    }
}
