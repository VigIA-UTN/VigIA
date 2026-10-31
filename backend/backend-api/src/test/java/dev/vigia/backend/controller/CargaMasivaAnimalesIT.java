package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@DisplayName("H0005 - Carga masiva de animales por CSV — IT")
class CargaMasivaAnimalesIT extends GestionRodeoItBase {

    private static final String ENCABEZADO = "IdCaravana,Alias,FechaNacimiento,Raza,RodeoLote,Sensor\n";

    @Test
    @DisplayName("H0005-01 - Carga masiva - CSV válido")
    void H0005_01_csvValido() throws Exception {
        MvcResult resultado = cargar("animales.csv", ENCABEZADO
                + "A-301,Masiva 1,2024-01-10,Angus,Rodeo A1,FX-A-L1\n"
                + "A-302,Masiva 2,2024-02-10,Hereford,Rodeo A1,\n"
                + "A-303,Masiva 3,2024-03-10,Angus,Rodeo A2,\n");

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        JsonNode respuesta = leer(resultado);
        assertThat(respuesta.get("procesados").asInt()).isEqualTo(3);
        assertThat(respuesta.get("exitosos").asInt()).isEqualTo(3);
        assertThat(respuesta.get("fallidos").asInt()).isZero();
        assertThat(respuesta.get("errores")).isEmpty();

        assertThat(cantidadAnimales(EST_A)).isEqualTo(9);
        assertThat(estadoCaravana("FX-A-L1")).isEqualTo("VINCULADO");
        assertThat(jdbcTemplate.queryForObject("SELECT caravana_id FROM animales WHERE id = ?", Long.class,
                idAnimal(EST_A, "A-301"))).isEqualTo(idCaravana("FX-A-L1"));
    }

    @Test
    @DisplayName("H0005-02 - Carga masiva - Filas inválidas con detalle de error")
    void H0005_02_filasInvalidasConDetalle() throws Exception {
        MvcResult resultado = cargar("animales.csv", ENCABEZADO
                + "A-301,Masiva 1,2024-01-10,Angus,Rodeo A1,\n"
                + "A-302,Masiva 2,2999-01-01,Angus,Rodeo A1,\n"
                + "A-303,Masiva 3,2024-03-10,Angus,Rodeo Inexistente,\n");

        JsonNode respuesta = leer(resultado);
        assertThat(respuesta.get("procesados").asInt()).isEqualTo(3);
        assertThat(respuesta.get("fallidos").asInt()).isEqualTo(2);
        assertThat(respuesta.get("exitosos").asInt()).isZero();
        assertThat(errores(respuesta)).containsExactly(
                "Fila 3: La fecha de nacimiento no puede ser futura.",
                "Fila 4: El rodeo/lote 'Rodeo Inexistente' no existe o no está activo en el establecimiento.");
        // Todo o nada: tampoco se carga la fila válida.
        assertThat(cantidadAnimales(EST_A)).isEqualTo(6);
    }

    @Test
    @DisplayName("H0005-03 - Carga masiva - Duplicados en el archivo y contra la base")
    void H0005_03_duplicados() throws Exception {
        MvcResult resultado = cargar("animales.csv", ENCABEZADO
                + "A-301,Uno,2024-01-10,Angus,Rodeo A1,FX-A-L1\n"
                + "A-301,Dos,2024-01-10,Angus,Rodeo A1,\n"
                + "A-101,Tres,2024-01-10,Angus,Rodeo A1,\n"
                + "A-302,Uno,2024-01-10,Angus,Rodeo A1,\n"
                + "A-303,Cuatro,2024-01-10,Angus,Rodeo A1,FX-A-L1\n");

        JsonNode respuesta = leer(resultado);
        assertThat(respuesta.get("fallidos").asInt()).isEqualTo(4);
        assertThat(respuesta.get("exitosos").asInt()).isZero();
        assertThat(errores(respuesta)).containsExactly(
                "Fila 3: ID Caravana duplicado en el archivo: A-301",
                "Fila 4: El ID Caravana A-101 ya existe en el establecimiento.",
                "Fila 5: Alias duplicado en el archivo: Uno",
                "Fila 6: Sensor duplicado en el archivo: FX-A-L1");
        assertThat(cantidadAnimales(EST_A)).isEqualTo(6);
        assertThat(estadoCaravana("FX-A-L1")).isEqualTo("DISPONIBLE");
    }

    @Test
    @DisplayName("H0005-04 - Carga masiva - Archivo con formato incorrecto")
    void H0005_04_formatoIncorrecto() throws Exception {
        MvcResult resultado = cargar("animales.txt", ENCABEZADO + "A-301,Uno,2024-01-10,Angus,Rodeo A1,\n");
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("El archivo debe ser un Excel (.xlsx, .xls) o CSV (.csv)");

        resultado = cargar("animales.csv", "");
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("El archivo está vacío");

        resultado = cargar("animales.csv", "IdCaravana,Alias,FechaNacimiento\nA-301,Uno,2024-01-10\n");
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("El archivo CSV debe tener al menos 5 columnas.");

        assertThat(cantidadAnimales(EST_A)).isEqualTo(6);
    }

    private MvcResult cargar(String nombreArchivo, String contenido) throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("file", nombreArchivo, "text/csv",
                contenido.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/v1/animales/csv")
                        .file(archivo)
                        .param("establecimientoId", idEstablecimiento(EST_A).toString())
                        .header("Authorization", bearer("productorA")))
                .andReturn();
    }

    private static List<String> errores(JsonNode respuesta) {
        List<String> errores = new ArrayList<>();
        respuesta.get("errores").forEach(e -> errores.add(e.asText()));
        return errores;
    }
}
