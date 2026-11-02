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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@DisplayName("H0003 - Alta y edición de establecimiento — IT")
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

    @Test
    @DisplayName("H0003-05 - Edición de establecimiento - Administrador edita nombre y ubicación")
    void H0003_05_adminEditaEstablecimiento() throws Exception {
        Long id = idEstablecimiento(EST_A);

        MvcResult resultado = editar("admin", id, cuerpo("  Fixture A editado  ", -35.25, -60.75));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode editado = leer(resultado);
        assertThat(editado.get("id").asLong()).isEqualTo(id);
        assertThat(editado.get("nombre").asText()).isEqualTo("Fixture A editado");
        assertThat(editado.get("latitud").asDouble()).isEqualTo(-35.25);
        assertThat(editado.get("longitud").asDouble()).isEqualTo(-60.75);
        assertThat(guardado(id)).containsExactly("Fixture A editado", -35.25, -60.75);
    }

    @Test
    @DisplayName("H0003-06 - Edición de establecimiento - Productor edita el suyo")
    void H0003_06_productorEditaElSuyo() throws Exception {
        Long id = idEstablecimiento(EST_A);

        MvcResult resultado = editar("productorA", id, cuerpo("Fixture A editado", -35.25, -60.75));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(guardado(id)).containsExactly("Fixture A editado", -35.25, -60.75);
    }

    @ParameterizedTest(name = "H0003-07 - Edición de establecimiento - {0} sin permiso")
    @ValueSource(strings = { "productorB", "veterinarioA", "camperoA" })
    @DisplayName("H0003-07 - Edición de establecimiento - Productor ajeno y empleados sin permiso")
    void H0003_07_sinPermisoNoPuedeEditar(String clave) throws Exception {
        Long id = idEstablecimiento(EST_A);
        List<Object> antes = guardado(id);

        assertThat(editar(clave, id, cuerpo("Fixture A editado", -35.25, -60.75)).getResponse().getStatus())
                .isEqualTo(403);
        assertThat(guardado(id)).isEqualTo(antes);
    }

    @Test
    @DisplayName("H0003-08 - Edición de establecimiento - Datos inválidos")
    void H0003_08_edicionConDatosInvalidos() throws Exception {
        Long id = idEstablecimiento(EST_A);
        List<Object> antes = guardado(id);
        List<Map<String, Object>> invalidos = new ArrayList<>();
        invalidos.add(cuerpo(null, -35.25, -60.75));
        invalidos.add(cuerpo("   ", -35.25, -60.75));
        invalidos.add(cuerpo("AB", -35.25, -60.75));
        invalidos.add(cuerpo("Fixture A editado", 90.5, -60.75));
        invalidos.add(cuerpo("Fixture A editado", -35.25, -180.5));
        invalidos.add(cuerpo("Fixture A editado", null, -60.75));

        for (Map<String, Object> invalido : invalidos) {
            assertThat(editar("admin", id, invalido).getResponse().getStatus()).as("cuerpo %s", invalido)
                    .isEqualTo(400);
        }
        assertThat(guardado(id)).isEqualTo(antes);
    }

    private MvcResult editar(String clave, Long id, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(put("/api/v1/establecimientos/" + id)
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    /** Nombre, latitud y longitud tal como quedaron en la base. */
    private List<Object> guardado(Long id) {
        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT nombre, latitud, longitud FROM establecimientos WHERE id = ?", id);
        return List.of(fila.get("nombre"), fila.get("latitud"), fila.get("longitud"));
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
