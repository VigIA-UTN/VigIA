package dev.vigia.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import dev.vigia.backend.dto.EstadoReproductivoParseResult;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.service.impl.EstadoReproductivoCsvServiceImpl;

class EstadoReproductivoCsvServiceTest {

    private EstadoReproductivoCsvService service;

    @BeforeEach
    void setUp() {
        service = new EstadoReproductivoCsvServiceImpl();
    }

    private MockMultipartFile createCsv(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes());
    }

    @Test
    @DisplayName("Falla si el archivo está vacío")
    void testEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", new byte[0]);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.parseAndValidate(file));
        assertEquals("El archivo está vacío", ex.getMessage());
    }

    @Test
    @DisplayName("Falla si el archivo excede los 5MB")
    void testFileTooLarge() {
        byte[] largeContent = new byte[(5 * 1024 * 1024) + 1]; // 5MB + 1 byte
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", largeContent);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.parseAndValidate(file));
        assertEquals("El archivo excede el tamaño máximo permitido (5MB)", ex.getMessage());
    }

    @Test
    @DisplayName("Falla si la extensión no es válida")
    void testInvalidExtension() {
        MockMultipartFile file = createCsv("test.txt", "header\nvalue");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.parseAndValidate(file));
        assertEquals("El archivo debe ser un CSV (.csv) o Excel (.xlsx, .xls)", ex.getMessage());
    }

    @Test
    @DisplayName("Falla si faltan columnas en el header")
    void testMissingColumnsHeader() {
        MockMultipartFile file = createCsv("test.csv", "IdCaravana\n123");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.parseAndValidate(file));
        assertEquals("El archivo no tiene las columnas requeridas (IdCaravana, Alias, Estado)", ex.getMessage());
    }

    @Test
    @DisplayName("Filas válidas se procesan correctamente")
    void testValidRows() throws IOException {
        String content = "IdCaravana,Alias,Estado,FechaUltimoParto,FechaUltimaInseminacion\n" +
                "123,,GESTANTE,2023-01-01,2023-02-01\n" +
                "124,,VACIA,,\n";
        MockMultipartFile file = createCsv("test.csv", content);

        EstadoReproductivoParseResult result = service.parseAndValidate(file);

        assertEquals(2, result.getProcesados());
        assertEquals(0, result.getInvalidos().size());
        assertEquals(2, result.getValidos().size());

        assertEquals("123", result.getValidos().get(0).getIdCaravana());
        assertEquals(TipoReproductivo.GESTANTE, result.getValidos().get(0).getEstado());
        assertEquals(LocalDate.of(2023, 1, 1), result.getValidos().get(0).getFechaUltimoParto());
        assertEquals(LocalDate.of(2023, 2, 1), result.getValidos().get(0).getFechaUltimaInseminacion());

        assertEquals("124", result.getValidos().get(1).getIdCaravana());
        assertEquals(TipoReproductivo.VACIA, result.getValidos().get(1).getEstado());
    }

    @Test
    @DisplayName("Manejo de fechas futuras y consistencia")
    void testInvalidDates() throws IOException {
        String futureDate = LocalDate.now().plusDays(1).toString();
        String content = "IdCaravana,Alias,Estado,FechaUltimoParto,FechaUltimaInseminacion\n" +
                "1,,GESTANTE," + futureDate + ",\n" + // Parto futuro
                "2,,GESTANTE,," + futureDate + "\n"; // Inseminacion futura

        MockMultipartFile file = createCsv("test.csv", content);
        EstadoReproductivoParseResult result = service.parseAndValidate(file);

        assertEquals(2, result.getProcesados());
        assertEquals(0, result.getValidos().size());
        assertEquals(2, result.getInvalidos().size());

        assertTrue(result.getInvalidos().get(0).getError().contains("no puede ser una fecha futura"));
        assertTrue(result.getInvalidos().get(1).getError().contains("no puede ser una fecha futura"));
    }

    @Test
    @DisplayName("Estados inválidos o campos vacíos se reportan en la fila")
    void testInvalidFields() throws IOException {
        String content = "IdCaravana,Alias,Estado,FechaUltimoParto,FechaUltimaInseminacion\n" +
                ",,GESTANTE,,\n" + // Sin ID ni Alias
                "2,,,\n" + // Sin estado
                "3,,ESTADO_FALSO,,\n"; // Estado falso

        MockMultipartFile file = createCsv("test.csv", content);
        EstadoReproductivoParseResult result = service.parseAndValidate(file);

        assertEquals(3, result.getProcesados());
        assertEquals(0, result.getValidos().size());
        assertEquals(3, result.getInvalidos().size());

        assertTrue(result.getInvalidos().get(0).getError().contains("IdCaravana o Alias son obligatorios"));
        assertTrue(result.getInvalidos().get(1).getError().contains("Estado es obligatorio"));
        assertTrue(result.getInvalidos().get(2).getError().contains("Estado inválido"));
    }
}
