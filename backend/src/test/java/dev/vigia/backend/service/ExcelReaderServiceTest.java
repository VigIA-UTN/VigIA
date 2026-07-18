package dev.vigia.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.service.impl.ExcelReaderServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExcelReaderService - Tests")
class ExcelReaderServiceTest {

    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private EstablecimientoRepository establecimientoRepository;
    @Mock
    private RodeoLoteRepository rodeoLoteRepository;

    @InjectMocks
    private ExcelReaderServiceImpl excelReaderService;

    private Establecimiento establecimiento;
    private RodeoLote rodeoLote;

    @BeforeEach
    void setUp() {
        establecimiento = new Establecimiento();
        establecimiento.setId(1L);
        establecimiento.setNombre("Establecimiento Test");

        rodeoLote = new RodeoLote();
        rodeoLote.setId(1L);
        rodeoLote.setNombre("Rodeo Test");
        rodeoLote.setEstablecimiento(establecimiento);
    }

    private MockMultipartFile generateExcel(String[] headers, String[][] rows) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Animales");

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < rows[r].length; c++) {
                if (rows[r][c] != null) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        workbook.close();

        return new MockMultipartFile(
                "file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bos.toByteArray());
    }

    @Test
    @DisplayName("Carga Exitosa")
    @SuppressWarnings("null")
    void leerYGuardarAnimalesExcel_success() throws IOException {
        when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail("test@vigia.dev"))
                .thenReturn(List.of(establecimiento));
        when(rodeoLoteRepository.findByEstablecimientoId(1L))
                .thenReturn(List.of(rodeoLote));
        when(animalRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String[] headers = { "IdCaravana", "Alias", "FechaNacimiento", "Raza", "Establecimiento", "Rodeo" };
        String[][] rows = {
                { "CAR-1", "Vaca 1", "2020-01-01", "Angus", "Establecimiento Test", "Rodeo Test" },
                { "CAR-2", "Vaca 2", "2021-05-10", "Hereford", "Establecimiento Test", "Rodeo Test" }
        };

        MultipartFile file = generateExcel(headers, rows);

        List<Animal> animales = excelReaderService.leerYGuardarAnimalesExcel(file, "test@vigia.dev");

        assertEquals(2, animales.size());
        assertEquals("CAR-1", animales.get(0).getIdCaravanaOficial());
        assertEquals("CAR-2", animales.get(1).getIdCaravanaOficial());
    }

    @Test
    @DisplayName("Archivo Inválido - Extensión Incorrecta")
    @SuppressWarnings("null")
    void leerYGuardarAnimalesExcel_invalidExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "texto".getBytes());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            excelReaderService.leerYGuardarAnimalesExcel(file, "test@vigia.dev");
        });
        assertEquals("El archivo debe ser un Excel (.xlsx o .xls)", exception.getMessage());
    }

    @Test
    @DisplayName("Archivo Vacío - Sin Filas")
    @SuppressWarnings("null")
    void leerYGuardarAnimalesExcel_emptyFile() throws IOException {
        when(animalRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String[] headers = { "IdCaravana", "Alias", "FechaNacimiento", "Raza", "Establecimiento", "Rodeo" };
        String[][] rows = {};

        MultipartFile file = generateExcel(headers, rows);

        List<Animal> animales = excelReaderService.leerYGuardarAnimalesExcel(file, "test@vigia.dev");
        assertEquals(0, animales.size());
    }

    @Test
    @DisplayName("Columnas Faltantes - Falla al Parsear")
    void leerYGuardarAnimalesExcel_missingColumns() throws IOException {
        String[] headers = { "IdCaravana", "Alias", "FechaNacimiento", "Raza" };
        String[][] rows = {
                { "CAR-1", "Vaca 1", "2020-01-01", "Angus" }
        };

        MultipartFile file = generateExcel(headers, rows);

        assertThrows(IllegalArgumentException.class, () -> {
            excelReaderService.leerYGuardarAnimalesExcel(file, "test@vigia.dev");
        });
    }

    @Test
    @DisplayName("Identificadores Duplicados - Constraint")
    @SuppressWarnings("null")
    void leerYGuardarAnimalesExcel_duplicateIdentifiers() throws IOException {
        when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail("test@vigia.dev"))
                .thenReturn(List.of(establecimiento));
        when(rodeoLoteRepository.findByEstablecimientoId(1L))
                .thenReturn(List.of(rodeoLote));
        when(animalRepository.saveAll(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicado"));

        String[] headers = { "IdCaravana", "Alias", "FechaNacimiento", "Raza", "Establecimiento", "Rodeo" };
        String[][] rows = {
                { "DUP-1", "Vaca 1", "2020-01-01", "Angus", "Establecimiento Test", "Rodeo Test" },
                { "DUP-1", "Vaca 2", "2021-05-10", "Hereford", "Establecimiento Test", "Rodeo Test" }
        };

        MultipartFile file = generateExcel(headers, rows);

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> {
            excelReaderService.leerYGuardarAnimalesExcel(file, "test@vigia.dev");
        });
    }
}
