package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * El escenario ya trae un estado por animal (GESTANTE en A-101, A-103, A-104 y A-105; PARIDA en A-102 y A-106, más un
 * POSIBLE_CELO automático), así que los historiales se comparan contra lo que había antes de cada caso.
 */
@DisplayName("H0007 - Registro manual de estado reproductivo — IT")
class EstadoReproductivoIT extends GestionRodeoItBase {

    private static final String ENCABEZADO_CSV = "IdCaravana,Alias,Estado,FechaUltimoParto,FechaUltimaInseminacion\n";

    @Test
    @DisplayName("H0007-01 - Estado reproductivo - Registro manual válido")
    void H0007_01_registroManualValido() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");

        MvcResult resultado = registrar("productorA", animal, estado("VACIA"));

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        JsonNode registrado = leer(resultado);
        assertThat(registrado.get("estado").asText()).isEqualTo("VACIA");
        assertThat(registrado.get("origen").asText()).isEqualTo("MANUAL");
        assertThat(registrado.get("registradoPor").asText()).isEqualTo("Productor A");

        JsonNode historial = historial("productorA", animal);
        assertThat(historial.get(0).get("id").asLong()).isEqualTo(registrado.get("id").asLong());
        assertThat(historial).hasSize(2);
    }

    @Test
    @DisplayName("H0007-02 - Estado reproductivo - Registro de CELO genera alerta")
    void H0007_02_celoGeneraAlerta() throws Exception {
        Long animal = idAnimal(EST_A, "A-103");
        LocalDateTime antes = LocalDateTime.now();

        MvcResult resultado = registrar("veterinarioA", animal, estado("CELO"));

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        List<Map<String, Object>> alertas = jdbcTemplate.queryForList("SELECT severidad, estado, "
                + "ventana_inicio_ins, ventana_fin_ins FROM alertas WHERE animal_id = ? AND disparador = 'CELO'",
                animal);
        assertThat(alertas).hasSize(1);
        Map<String, Object> alerta = alertas.get(0);
        assertThat(alerta.get("severidad")).isEqualTo("ALTA");
        assertThat(alerta.get("estado")).isEqualTo("PENDIENTE");
        LocalDateTime inicio = ((Timestamp) alerta.get("ventana_inicio_ins")).toLocalDateTime();
        LocalDateTime fin = ((Timestamp) alerta.get("ventana_fin_ins")).toLocalDateTime();
        assertThat(inicio).isBetween(antes.plusHours(6), LocalDateTime.now().plusHours(6));
        assertThat(fin).isBetween(antes.plusHours(24), LocalDateTime.now().plusHours(24));
    }

    @Test
    @DisplayName("H0007-03 - Estado reproductivo - Transiciones del ciclo")
    void H0007_03_transicionesDelCiclo() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");
        LocalDate inseminacion = LocalDate.now().minusDays(30);
        LocalDate parto = LocalDate.now();

        Map<String, Object> inseminada = estado("INSEMINADA");
        inseminada.put("fechaUltimaInseminacion", inseminacion.toString());
        Map<String, Object> parida = estado("PARIDA");
        parida.put("fechaUltimoParto", parto.toString());
        for (Map<String, Object> cuerpo : List.of(estado("VACIA"), inseminada, estado("GESTANTE"), parida)) {
            MvcResult resultado = registrar("productorA", animal, cuerpo);
            assertThat(resultado.getResponse().getStatus()).as("%s: %s", cuerpo, texto(resultado)).isEqualTo(201);
        }

        // Del más nuevo al más viejo; el último es el GESTANTE del escenario.
        JsonNode historial = historial("productorA", animal);
        List<String> estados = new ArrayList<>();
        historial.forEach(e -> estados.add(e.get("estado").asText()));
        assertThat(estados).containsExactly("PARIDA", "GESTANTE", "INSEMINADA", "VACIA", "GESTANTE");
        assertThat(historial.get(0).get("fechaUltimoParto").asText()).isEqualTo(parto.toString());
        assertThat(historial.get(2).get("fechaUltimaInseminacion").asText()).isEqualTo(inseminacion.toString());
        for (int i = 0; i < 4; i++) {
            assertThat(historial.get(i).get("origen").asText()).isEqualTo("MANUAL");
            assertThat(historial.get(i).get("registradoPor").asText()).isEqualTo("Productor A");
        }
    }

    @Test
    @DisplayName("H0007-04 - Estado reproductivo - Validaciones")
    void H0007_04_validaciones() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");
        Long inactivo = idAnimal(EST_A, "A-106");
        jdbcTemplate.update("UPDATE animales SET estado_actual = 'INACTIVO' WHERE id = ?", inactivo);

        Map<String, Object> partoFuturo = estado("PARIDA");
        partoFuturo.put("fechaUltimoParto", LocalDate.now().plusDays(1).toString());
        Map<String, Object> inseminacionFutura = estado("INSEMINADA");
        inseminacionFutura.put("fechaUltimaInseminacion", LocalDate.now().plusDays(1).toString());

        Map<String, MvcResult> variantes = new LinkedHashMap<>();
        variantes.put("sin estado", registrar("productorA", animal, new HashMap<>()));
        variantes.put("parto futuro", registrar("productorA", animal, partoFuturo));
        variantes.put("inseminación futura", registrar("productorA", animal, inseminacionFutura));
        variantes.put("animal inactivo", registrar("productorA", inactivo, estado("VACIA")));

        variantes.forEach((variante, resultado) ->
                assertThat(resultado.getResponse().getStatus()).as(variante).isEqualTo(400));
        assertThat(cantidadEstados(animal)).isEqualTo(1);
        assertThat(cantidadEstados(inactivo)).isEqualTo(2);
    }

    @Test
    @DisplayName("H0007-05 - Estado reproductivo - Edición")
    void H0007_05_edicion() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");
        Long otro = idAnimal(EST_A, "A-102");
        Long estadoId = leer(registrar("productorA", animal, estado("VACIA"))).get("id").asLong();

        MvcResult resultado = editar(animal, estadoId, estado("GESTANTE"));
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        assertThat(leer(resultado).get("estado").asText()).isEqualTo("GESTANTE");

        resultado = editar(otro, estadoId, estado("CELO"));
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("El estado reproductivo no pertenece a este animal.");
        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM estados_reproductivos WHERE id = ?",
                String.class, estadoId)).isEqualTo("GESTANTE");
    }

    @Test
    @DisplayName("H0007-06 - Estado reproductivo - Carga por CSV")
    void H0007_06_cargaPorCsv() throws Exception {
        Long a101 = idAnimal(EST_A, "A-101");
        Long a102 = idAnimal(EST_A, "A-102");

        // b) Primero el archivo con errores, para ver que no deja nada registrado.
        MvcResult resultado = cargarCsv(ENCABEZADO_CSV
                + "A-101,,ESTADO_FALSO,,\n"
                + "NO-EXISTE,,VACIA,,\n"
                + "A-103,,POSIBLE_CELO,,\n"
                + "A-102,,GESTANTE,,\n");
        JsonNode respuesta = leer(resultado);
        assertThat(respuesta.get("exitosos").asInt()).isZero();
        assertThat(respuesta.get("fallidos").asInt()).isEqualTo(3);
        List<String> errores = new ArrayList<>();
        respuesta.get("errores").forEach(e -> errores.add(e.get("fila").asInt() + ": " + e.get("error").asText()));
        String permitidos = "Estado inválido. Valores permitidos: VACIA, GESTANTE, CELO, ANESTRO, INSEMINADA, PARIDA";
        assertThat(errores).containsExactly(
                "2: " + permitidos,
                "3: NO-EXISTE: Animal no encontrado con los identificadores proporcionados en este establecimiento.",
                "4: " + permitidos);
        assertThat(cantidadEstados(a102)).isEqualTo(2);

        // a) Archivo válido.
        resultado = cargarCsv(ENCABEZADO_CSV
                + "A-101,,VACIA,,\n"
                + "A-102,,GESTANTE,," + LocalDate.now().minusDays(20) + "\n");
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        respuesta = leer(resultado);
        assertThat(respuesta.get("exitosos").asInt()).isEqualTo(2);
        assertThat(respuesta.get("fallidos").asInt()).isZero();

        JsonNode ultimo = historial("productorA", a101).get(0);
        assertThat(ultimo.get("estado").asText()).isEqualTo("VACIA");
        assertThat(ultimo.get("origen").asText()).isEqualTo("CARGA_MASIVA");
        assertThat(historial("productorA", a102).get(0).get("estado").asText()).isEqualTo("GESTANTE");
    }

    private static Map<String, Object> estado(String estado) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("estado", estado);
        return cuerpo;
    }

    private MvcResult registrar(String clave, Long animalId, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-reproductivo")
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    private MvcResult editar(Long animalId, Long estadoId, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(put("/api/v1/animales/" + animalId + "/estado-reproductivo/" + estadoId)
                        .header("Authorization", bearer("productorA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    private JsonNode historial(String clave, Long animalId) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/animales/" + animalId + "/estado-reproductivo")
                        .header("Authorization", bearer(clave)))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        return leer(resultado);
    }

    private MvcResult cargarCsv(String contenido) throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("file", "estados.csv", "text/csv",
                contenido.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/v1/animales/estado-reproductivo/csv")
                        .file(archivo)
                        .param("establecimientoId", idEstablecimiento(EST_A).toString())
                        .header("Authorization", bearer("productorA")))
                .andReturn();
    }

    private int cantidadEstados(Long animalId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM estados_reproductivos WHERE animal_id = ?",
                Integer.class, animalId);
    }
}
