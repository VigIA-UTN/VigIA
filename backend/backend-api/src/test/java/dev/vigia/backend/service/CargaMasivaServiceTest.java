package dev.vigia.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import dev.vigia.backend.dto.CargaMasivaResult;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.service.impl.CargaMasivaServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("CargaMasivaService - Tests")
class CargaMasivaServiceTest {

        @Mock
        private AnimalRepository animalRepository;
        @Mock
        private EstablecimientoRepository establecimientoRepository;
        @Mock
        private RodeoLoteRepository rodeoLoteRepository;

        @Mock
        private FileParserService fileParserService;

        private CargaMasivaServiceImpl excelReaderService;

        private Establecimiento establecimiento;
        private RodeoLote rodeoLote;

        @BeforeEach
        void setUp() {
                excelReaderService = new CargaMasivaServiceImpl(animalRepository, establecimientoRepository,
                                rodeoLoteRepository, List.of(fileParserService));

                establecimiento = new Establecimiento();
                establecimiento.setId(1L);
                establecimiento.setNombre("Establecimiento Test");

                Usuario usuario = new Usuario();
                usuario.setEmail("test@vigia.dev");

                Empleado empleado = new Empleado();
                empleado.setUsuario(usuario);
                empleado.setActivo(true);
                empleado.setEstablecimiento(establecimiento);

                establecimiento.setEmpleados(new ArrayList<>(List.of(empleado)));

                rodeoLote = new RodeoLote();
                rodeoLote.setId(1L);
                rodeoLote.setNombre("Rodeo Test");
                rodeoLote.setEstablecimiento(establecimiento);
        }

        private MockMultipartFile createMockFile() {
                return new MockMultipartFile(
                                "file", "test.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "dummy content".getBytes());
        }

        @Test
        @DisplayName("Carga Exitosa")
        @SuppressWarnings("null")
        void procesarCargaMasiva_success() throws IOException {
                when(establecimientoRepository.findById(1L))
                                .thenReturn(Optional.of(establecimiento));
                when(rodeoLoteRepository.findByEstablecimientoId(1L))
                                .thenReturn(List.of(rodeoLote));
                when(animalRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

                List<String[]> parsedRows = List.of(
                                new String[] { "CAR-1", "Vaca 1", "2020-01-01", "Angus", "Rodeo Test" },
                                new String[] { "CAR-2", "Vaca 2", "2021-05-10", "Hereford", "Rodeo Test" });

                MultipartFile file = createMockFile();
                when(fileParserService.supports("test.xlsx")).thenReturn(true);
                when(fileParserService.parse(file)).thenReturn(parsedRows);

                CargaMasivaResult resultado = excelReaderService.procesarCargaMasiva(file, 1L, "test@vigia.dev");

                assertEquals(2, resultado.getProcesados());
                assertEquals(0, resultado.getFallidos());
                assertEquals(2, resultado.getAnimalesExitosos().size());
                assertEquals("CAR-1", resultado.getAnimalesExitosos().get(0).getIdCaravanaOficial());
                assertEquals("CAR-2", resultado.getAnimalesExitosos().get(1).getIdCaravanaOficial());
        }

        @Test
        @DisplayName("Archivo Inválido - Extensión Incorrecta")
        @SuppressWarnings("null")
        void procesarCargaMasiva_invalidExtension() {
                when(establecimientoRepository.findById(1L))
                                .thenReturn(Optional.of(establecimiento));

                MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "texto".getBytes());

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        excelReaderService.procesarCargaMasiva(file, 1L, "test@vigia.dev");
                });
                assertEquals("El archivo debe ser un Excel (.xlsx, .xls) o CSV (.csv)", exception.getMessage());
        }

        @Test
        @DisplayName("Archivo Vacío - Sin Filas")
        @SuppressWarnings("null")
        void procesarCargaMasiva_emptyFile() throws IOException {
                when(establecimientoRepository.findById(1L))
                                .thenReturn(Optional.of(establecimiento));
                when(animalRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

                MultipartFile file = createMockFile();
                when(fileParserService.supports("test.xlsx")).thenReturn(true);
                when(fileParserService.parse(file)).thenReturn(List.of());

                CargaMasivaResult resultado = excelReaderService.procesarCargaMasiva(file, 1L, "test@vigia.dev");
                assertEquals(0, resultado.getProcesados());
                assertEquals(0, resultado.getFallidos());
                assertEquals(0, resultado.getAnimalesExitosos().size());
        }

        @Test
        @DisplayName("Columnas Faltantes - Falla al Parsear")
        void procesarCargaMasiva_missingColumns() throws IOException {
                when(establecimientoRepository.findById(1L))
                                .thenReturn(Optional.of(establecimiento));

                List<String[]> parsedRows = Collections.singletonList(
                                new String[] { "", "", "2020-01-01" });

                MultipartFile file = createMockFile();
                when(fileParserService.supports("test.xlsx")).thenReturn(true);
                when(fileParserService.parse(file)).thenReturn(parsedRows);

                CargaMasivaResult resultado = excelReaderService.procesarCargaMasiva(file, 1L, "test@vigia.dev");
                assertEquals(1, resultado.getProcesados());
                assertEquals(1, resultado.getFallidos());
                assertEquals(0, resultado.getAnimalesExitosos().size());
        }

        @Test
        @DisplayName("Identificadores Duplicados - Constraint")
        @SuppressWarnings("null")
        void procesarCargaMasiva_duplicateIdentifiers() throws IOException {
                when(establecimientoRepository.findById(1L))
                                .thenReturn(Optional.of(establecimiento));
                when(rodeoLoteRepository.findByEstablecimientoId(1L))
                                .thenReturn(List.of(rodeoLote));
                when(animalRepository.saveAll(any()))
                                .thenThrow(new DataIntegrityViolationException("Duplicado"));

                List<String[]> parsedRows = List.of(
                                new String[] { "DUP-1", "Vaca 1", "2020-01-01", "Angus", "Rodeo Test" },
                                new String[] { "DUP-1", "Vaca 2", "2021-05-10", "Hereford", "Rodeo Test" });

                MultipartFile file = createMockFile();
                when(fileParserService.supports("test.xlsx")).thenReturn(true);
                when(fileParserService.parse(file)).thenReturn(parsedRows);

                assertThrows(DataIntegrityViolationException.class, () -> {
                        excelReaderService.procesarCargaMasiva(file, 1L, "test@vigia.dev");
                });
        }
}
