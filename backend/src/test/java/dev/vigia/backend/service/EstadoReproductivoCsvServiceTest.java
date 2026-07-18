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
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.parseAndValidate(file));
        assertEquals("El archivo está vacío", ex.getMessage());
    }

    @Test
    @DisplayName("Falla si la extensión no es válida")
    void testInvalidExtension() {
        MockMultipartFile file = createCsv("test.txt", "header\nvalue");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.parseAndValidate(file));
        assertEquals("El archivo debe ser un CSV (.csv) o Excel (.xlsx, .xls)", ex.getMessage());
    }

    @Test
    @DisplayName("Falla si faltan columnas en el header")
    void testMissingColumnsHeader() {
        MockMultipartFile file = createCsv("test.csv", "IdCaravana\nX-123");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.parseAndValidate(file));
        assertEquals("El archivo no tiene las columnas requeridas (IdCaravana, Estado)", ex.getMessage());
    }

    @Test
    @DisplayName("Filas válidas se procesan correctamente")
    void testValidRows() throws IOException {
        String content = "IdCaravana,Estado,FechaUltimoParto,FechaUltimaInseminacion\n" +
                "X-123,GESTANTE,2023-01-01,2023-02-01\n" +
                "X-124,VACIA,,\n";
        MockMultipartFile file = createCsv("test.csv", content);

        EstadoReproductivoParseResult result = service.parseAndValidate(file);

        assertEquals(2, result.getProcesados());
        assertEquals(0, result.getInvalidos().size());
        assertEquals(2, result.getValidos().size());

        assertEquals("X-123", result.getValidos().get(0).getIdCaravanaOficial());
        assertEquals(TipoReproductivo.GESTANTE, result.getValidos().get(0).getEstado());
        assertEquals(LocalDate.of(2023, 1, 1), result.getValidos().get(0).getFechaUltimoParto());
        assertEquals(LocalDate.of(2023, 2, 1), result.getValidos().get(0).getFechaUltimaInseminacion());

        assertEquals("X-124", result.getValidos().get(1).getIdCaravanaOficial());
        assertEquals(TipoReproductivo.VACIA, result.getValidos().get(1).getEstado());
    }

    @Test
    @DisplayName("Manejo de fechas futuras y consistencia")
    void testInvalidDates() throws IOException {
        String futureDate = LocalDate.now().plusDays(1).toString();
        String content = "IdCaravana,Estado,FechaUltimoParto,FechaUltimaInseminacion\n" +
                "X-1,GESTANTE," + futureDate + ",\n" + // Parto futuro
                "X-2,GESTANTE,," + futureDate + "\n" + // Inseminacion futura
                "X-3,GESTANTE,2023-02-01,2023-01-01\n"; // Inseminacion antes que parto

        MockMultipartFile file = createCsv("test.csv", content);
        EstadoReproductivoParseResult result = service.parseAndValidate(file);

        assertEquals(3, result.getProcesados());
        assertEquals(0, result.getValidos().size());
        assertEquals(3, result.getInvalidos().size());

        assertTrue(result.getInvalidos().get(0).getError().contains("no puede ser una fecha futura"));
        assertTrue(result.getInvalidos().get(1).getError().contains("no puede ser una fecha futura"));
        assertTrue(result.getInvalidos().get(2).getError().contains("no puede ser anterior a FechaUltimoParto"));
    }

    @Test
    @DisplayName("Estados inválidos o campos vacíos se reportan en la fila")
    void testInvalidFields() throws IOException {
        String content = "IdCaravana,Estado,FechaUltimoParto,FechaUltimaInseminacion\n" +
                ",GESTANTE,,\n" + // Sin ID
                "X-2,,,\n" + // Sin estado
                "X-3,ESTADO_FALSO,,\n"; // Estado falso

        MockMultipartFile file = createCsv("test.csv", content);
        EstadoReproductivoParseResult result = service.parseAndValidate(file);

        assertEquals(3, result.getProcesados());
        assertEquals(0, result.getValidos().size());
        assertEquals(3, result.getInvalidos().size());

        assertTrue(result.getInvalidos().get(0).getError().contains("IdCaravana es obligatorio"));
        assertTrue(result.getInvalidos().get(1).getError().contains("Estado es obligatorio"));
        assertTrue(result.getInvalidos().get(2).getError().contains("Estado inválido"));
    }
}
