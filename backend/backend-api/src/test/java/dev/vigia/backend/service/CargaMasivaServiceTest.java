package dev.vigia.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.CaravanaRepository;
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
        private CaravanaRepository caravanaRepository;

        @Mock
        private FileParserService fileParserService;

        private CargaMasivaServiceImpl excelReaderService;

        private Establecimiento establecimiento;
        private RodeoLote rodeoLote;

        @BeforeEach
        void setUp() {
                excelReaderService = new CargaMasivaServiceImpl(animalRepository, establecimientoRepository,
                                rodeoLoteRepository, caravanaRepository, List.of(fileParserService));

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

        @SuppressWarnings("null")
        private CargaMasivaResult procesarFilas(List<String[]> parsedRows) throws IOException {
                when(establecimientoRepository.findById(1L))
                                .thenReturn(Optional.of(establecimiento));
                when(rodeoLoteRepository.findByEstablecimientoId(1L))
                                .thenReturn(List.of(rodeoLote));
                when(animalRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

                MultipartFile file = createMockFile();
                when(fileParserService.supports("test.xlsx")).thenReturn(true);
                when(fileParserService.parse(file)).thenReturn(parsedRows);

                return excelReaderService.procesarCargaMasiva(file, 1L, "test@vigia.dev");
        }

        private void assertFallaConError(List<String[]> parsedRows, String errorEsperado) throws IOException {
                CargaMasivaResult resultado = procesarFilas(parsedRows);
                assertEquals(1, resultado.getFallidos());
                assertEquals(0, resultado.getAnimalesExitosos().size());
                assertEquals(errorEsperado, resultado.getErrores().get(0));
        }

        @Test
        @DisplayName("Falla si falta el IdCaravana aunque haya Alias")
        void procesarCargaMasiva_sinIdCaravana() throws IOException {
                assertFallaConError(
                                List.<String[]>of(new String[] { "", "Lola", "2020-01-01", "Angus", "Rodeo Test" }),
                                "Fila 2: El IdCaravana es obligatorio.");
        }

        @Test
        @DisplayName("Falla si falta la fecha de nacimiento")
        void procesarCargaMasiva_sinFechaNacimiento() throws IOException {
                assertFallaConError(
                                List.<String[]>of(new String[] { "CAR-1", "Lola", "", "Angus", "Rodeo Test" }),
                                "Fila 2: La fecha de nacimiento es obligatoria.");
        }

        @Test
        @DisplayName("Falla si la fecha de nacimiento es futura")
        void procesarCargaMasiva_fechaFutura() throws IOException {
                String manana = java.time.LocalDate.now().plusDays(1).toString();
                assertFallaConError(
                                List.<String[]>of(new String[] { "CAR-1", "Lola", manana, "Angus", "Rodeo Test" }),
                                "Fila 2: La fecha de nacimiento no puede ser futura.");
        }

        @Test
        @DisplayName("Falla si falta el rodeo/lote")
        void procesarCargaMasiva_sinRodeo() throws IOException {
                assertFallaConError(
                                List.<String[]>of(new String[] { "CAR-1", "Lola", "2020-01-01", "Angus", " " }),
                                "Fila 2: El rodeo/lote es obligatorio.");
        }

        @Test
        @SuppressWarnings("null")
        @DisplayName("Falla si el rodeo no existe (no se crea automáticamente)")
        void procesarCargaMasiva_rodeoInexistente() throws IOException {
                assertFallaConError(
                                List.<String[]>of(new String[] { "CAR-1", "Lola", "2020-01-01", "Angus", "Otro Lote" }),
                                "Fila 2: El rodeo/lote 'Otro Lote' no existe o no está activo en el establecimiento.");
                verify(rodeoLoteRepository, never()).save(any());
        }

        @Test
        @DisplayName("Falla si el rodeo está inactivo")
        void procesarCargaMasiva_rodeoInactivo() throws IOException {
                rodeoLote.setActivo(false);
                assertFallaConError(
                                List.<String[]>of(new String[] { "CAR-1", "Lola", "2020-01-01", "Angus", "Rodeo Test" }),
                                "Fila 2: El rodeo/lote 'Rodeo Test' no existe o no está activo en el establecimiento.");
        }

        @Test
        @DisplayName("Rodeo se matchea sin distinguir mayúsculas ni espacios")
        void procesarCargaMasiva_rodeoCaseInsensitive() throws IOException {
                CargaMasivaResult resultado = procesarFilas(
                                List.<String[]>of(new String[] { "CAR-1", "Lola", "2020-01-01", "Angus", "  rodeo test " }));
                assertEquals(0, resultado.getFallidos());
                assertEquals(rodeoLote, resultado.getAnimalesExitosos().get(0).getRodeoLote());
        }

        @Test
        @DisplayName("Falla si el alias se repite dentro del archivo")
        void procesarCargaMasiva_aliasDuplicadoEnArchivo() throws IOException {
                CargaMasivaResult resultado = procesarFilas(List.of(
                                new String[] { "CAR-1", "Lola", "2020-01-01", "Angus", "Rodeo Test" },
                                new String[] { "CAR-2", "Lola", "2020-01-01", "Angus", "Rodeo Test" }));
                assertEquals(1, resultado.getFallidos());
                assertEquals(0, resultado.getAnimalesExitosos().size());
                assertEquals("Fila 3: Alias duplicado en el archivo: Lola", resultado.getErrores().get(0));
        }

        @Test
        @DisplayName("Falla si el alias ya existe en el establecimiento")
        void procesarCargaMasiva_aliasExistenteEnBD() throws IOException {
                when(animalRepository.existsByAliasAndEstablecimientoId("Lola", 1L)).thenReturn(true);
                assertFallaConError(
                                List.<String[]>of(new String[] { "CAR-1", "Lola", "2020-01-01", "Angus", "Rodeo Test" }),
                                "Fila 2: El Alias Lola ya existe en el establecimiento.");
        }

        @Test
        @DisplayName("Las filas vacías se ignoran")
        void procesarCargaMasiva_filasVaciasIgnoradas() throws IOException {
                CargaMasivaResult resultado = procesarFilas(List.of(
                                new String[] { "CAR-1", "Lola", "2020-01-01", "Angus", "Rodeo Test" },
                                new String[] { "", " ", null, "", "" },
                                new String[] { null, null, null, null, null }));
                assertEquals(1, resultado.getProcesados());
                assertEquals(0, resultado.getFallidos());
                assertEquals(1, resultado.getAnimalesExitosos().size());
        }

        // ── Columna Sensor (opcional) ────────────────────────────────────────────

        private Caravana caravana(long id, String devEui, String codigo, String alias, EstadoSensor estado) {
                Caravana c = new Caravana();
                c.setId(id);
                c.setDevEui(devEui);
                c.setCodigo(codigo);
                c.setAlias(alias);
                c.setEstadoSensor(estado);
                c.setEstablecimiento(establecimiento);
                return c;
        }

        @Test
        @DisplayName("Sin columna Sensor no consulta caravanas y crea los animales sin sensor")
        void procesarCargaMasiva_sinSensor_noConsultaCaravanas() throws IOException {
                CargaMasivaResult resultado = procesarFilas(List.<String[]>of(
                                new String[] { "CAR-1", "Lola", "2020-01-01", "Angus", "Rodeo Test" }));
                assertEquals(1, resultado.getAnimalesExitosos().size());
                assertNull(resultado.getAnimalesExitosos().get(0).getCaravana());
                verify(caravanaRepository, never()).findAllByEstablecimientoId(any());
        }

        @Test
        @DisplayName("Con Sensor vincula la caravana, buscándola por DevEUI, código o alias")
        void procesarCargaMasiva_conSensor_vincula() throws IOException {
                Caravana porDevEui = caravana(1L, "EUI-1", null, null, EstadoSensor.DISPONIBLE);
                Caravana porCodigo = caravana(2L, "EUI-2", "COD-2", null, EstadoSensor.DISPONIBLE);
                Caravana porAlias = caravana(3L, "EUI-3", null, "Sensor Lola", EstadoSensor.DISPONIBLE);
                when(caravanaRepository.findAllByEstablecimientoId(1L)).thenReturn(List.of(porDevEui, porCodigo, porAlias));

                CargaMasivaResult resultado = procesarFilas(List.of(
                                new String[] { "CAR-1", "Vaca 1", "2020-01-01", "Angus", "Rodeo Test", "eui-1" },
                                new String[] { "CAR-2", "Vaca 2", "2020-01-01", "Angus", "Rodeo Test", "COD-2" },
                                new String[] { "CAR-3", "Vaca 3", "2020-01-01", "Angus", "Rodeo Test", " sensor lola " },
                                new String[] { "CAR-4", "Vaca 4", "2020-01-01", "Angus", "Rodeo Test", "" }));

                assertEquals(0, resultado.getFallidos());
                assertSame(porDevEui, resultado.getAnimalesExitosos().get(0).getCaravana());
                assertSame(porCodigo, resultado.getAnimalesExitosos().get(1).getCaravana());
                assertSame(porAlias, resultado.getAnimalesExitosos().get(2).getCaravana());
                assertNull(resultado.getAnimalesExitosos().get(3).getCaravana());
                for (Caravana c : List.of(porDevEui, porCodigo, porAlias)) {
                        assertEquals(EstadoSensor.VINCULADO, c.getEstadoSensor());
                        verify(caravanaRepository).save(c);
                }
        }

        @Test
        @DisplayName("Falla si el sensor no existe, ya está vinculado o está dado de baja")
        void procesarCargaMasiva_sensorInvalido() throws IOException {
                when(caravanaRepository.findAllByEstablecimientoId(1L)).thenReturn(List.of(
                                caravana(1L, "EUI-VINC", null, null, EstadoSensor.VINCULADO),
                                caravana(2L, "EUI-BAJA", null, null, EstadoSensor.INACTIVO)));

                CargaMasivaResult resultado = procesarFilas(List.of(
                                new String[] { "CAR-1", "Vaca 1", "2020-01-01", "Angus", "Rodeo Test", "NO-EXISTE" },
                                new String[] { "CAR-2", "Vaca 2", "2020-01-01", "Angus", "Rodeo Test", "EUI-VINC" },
                                new String[] { "CAR-3", "Vaca 3", "2020-01-01", "Angus", "Rodeo Test", "EUI-BAJA" }));

                assertEquals(3, resultado.getFallidos());
                assertEquals(0, resultado.getAnimalesExitosos().size());
                assertEquals(List.of(
                                "Fila 2: El sensor 'NO-EXISTE' no existe en el establecimiento.",
                                "Fila 3: El sensor 'EUI-VINC' ya está vinculado a otro animal.",
                                "Fila 4: El sensor 'EUI-BAJA' está dado de baja."), resultado.getErrores());
                verify(caravanaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Falla si el mismo sensor aparece dos veces en el archivo, y no vincula nada")
        void procesarCargaMasiva_sensorDuplicado() throws IOException {
                Caravana libre = caravana(1L, "EUI-1", null, null, EstadoSensor.DISPONIBLE);
                when(caravanaRepository.findAllByEstablecimientoId(1L)).thenReturn(List.of(libre));

                CargaMasivaResult resultado = procesarFilas(List.of(
                                new String[] { "CAR-1", "Vaca 1", "2020-01-01", "Angus", "Rodeo Test", "EUI-1" },
                                new String[] { "CAR-2", "Vaca 2", "2020-01-01", "Angus", "Rodeo Test", "EUI-1" }));

                assertEquals(List.of("Fila 3: Sensor duplicado en el archivo: EUI-1"), resultado.getErrores());
                assertEquals(0, resultado.getAnimalesExitosos().size());
                assertEquals(EstadoSensor.DISPONIBLE, libre.getEstadoSensor());
        }

        @Test
        @DisplayName("Falla si el alias del sensor coincide con varias caravanas")
        void procesarCargaMasiva_sensorAmbiguo() throws IOException {
                when(caravanaRepository.findAllByEstablecimientoId(1L)).thenReturn(List.of(
                                caravana(1L, "EUI-1", null, "Repetido", EstadoSensor.DISPONIBLE),
                                caravana(2L, "EUI-2", null, "Repetido", EstadoSensor.DISPONIBLE)));

                assertFallaConError(
                                List.<String[]>of(new String[] { "CAR-1", "Vaca 1", "2020-01-01", "Angus", "Rodeo Test", "Repetido" }),
                                "Fila 2: El sensor 'Repetido' coincide con varias caravanas. Indicá su DevEUI.");
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
