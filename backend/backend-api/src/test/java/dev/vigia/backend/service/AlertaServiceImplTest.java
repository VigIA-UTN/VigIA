package dev.vigia.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.vigia.backend.dto.AlertaDetalleResponse;
import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.BloqueComparativoResponse;
import dev.vigia.backend.dto.IntervencionRequest;
import dev.vigia.backend.dto.SerieComparativaSnapshot;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.EstadoSalud;
import dev.vigia.backend.model.OrigenEstado;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.TipoEstadoSalud;
import dev.vigia.backend.model.TipoIntervencion;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.dto.LineaTiempoEventoResponse;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.EstadoSaludRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.service.impl.AlertaServiceImpl;
import dev.vigia.backend.model.Intervencion;

@ExtendWith(MockitoExtension.class)
public class AlertaServiceImplTest {

        @Mock
        private AlertaRepository alertaRepository;

        @Mock
        private UsuarioRepository usuarioRepository;

        @Mock
        private EstablecimientoRepository establecimientoRepository;

        @Mock
        private IntervencionRepository intervencionRepository;

        @Mock
        private EstadoReproductivoRepository estadoReproductivoRepository;

        @Mock
        private EstadoSaludRepository estadoSaludRepository;

        @Mock
        private RabbitTemplate rabbitTemplate;

        @Mock
        private AnimalRepository animalRepository;

        @Spy
        private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        @org.mockito.Spy
        private CeloDatoDesactualizadoEvaluator datoDesactualizadoEvaluator = new CeloDatoDesactualizadoEvaluator();

        @InjectMocks
        private AlertaServiceImpl alertaService;

        private Usuario mockUsuario;
        private Establecimiento mockEstablecimiento;
        private Animal mockAnimal;
        private Alerta mockAlerta;

        private final String EMAIL = "test@test.com";
        private final Long ESTABLECIMIENTO_ID = 1L;
        private final Long ALERTA_ID = 100L;

        @BeforeEach
        void setUp() {
                mockUsuario = new Usuario();
                mockUsuario.setEmail(EMAIL);

                mockEstablecimiento = new Establecimiento();
                mockEstablecimiento.setId(ESTABLECIMIENTO_ID);

                mockAnimal = new Animal();
                mockAnimal.setId(10L);
                mockAnimal.setEstablecimiento(mockEstablecimiento);
                mockAnimal.setIdCaravanaOficial("CAR-123");
                mockAnimal.setAlias("Lola");

                mockAlerta = new Alerta();
                mockAlerta.setId(ALERTA_ID);
                mockAlerta.setAnimal(mockAnimal);
                mockAlerta.setTsGeneracion(LocalDateTime.now());
                mockAlerta.setEstado(EstadoAlerta.PENDIENTE);
                mockAlerta.setDisparador(DisparadorAlerta.CELO);
                mockAlerta.setValorDetectado(0.85);
                mockAlerta.setAccionRecomendada("Planificar inseminación artificial dentro de la ventana óptima.");
                mockAlerta.setVentanaInicioIns(LocalDateTime.now().plusHours(2));
                mockAlerta.setVentanaFinIns(LocalDateTime.now().plusHours(14));
        }

        // --- Tests para listarAlertasCelo ---

        @Test
        void listarAlertasCelo_Success() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findCeloByEstablecimiento(ESTABLECIMIENTO_ID))
                                .thenReturn(List.of(mockAlerta));

                // Act
                List<AlertaResponse> result = alertaService.listarAlertasCelo(EMAIL, ESTABLECIMIENTO_ID, null);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals(ALERTA_ID, result.get(0).getId());
                assertEquals("Planificar inseminación artificial dentro de la ventana óptima.",
                                result.get(0).getAccionRecomendada());
                verify(alertaRepository, times(1)).findCeloByEstablecimiento(ESTABLECIMIENTO_ID);
        }

        @Test
        void listarAlertasCelo_Anestro_ExponeSubtipoYMantieneTipoCelo() {
                mockAlerta.setTipo("Anestro Detectado");
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findCeloByEstablecimiento(ESTABLECIMIENTO_ID))
                                .thenReturn(List.of(mockAlerta));

                AlertaResponse result = alertaService.listarAlertasCelo(EMAIL, ESTABLECIMIENTO_ID, null).get(0);

                assertEquals("CELO", result.getTipo());
                assertEquals("Anestro Detectado", result.getSubtipo());
        }

        @Test
        void listarAlertasCelo_ThrowsIllegalArgumentException_WhenUsuarioNotFound() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

                // Act & Assert
                assertThrows(IllegalArgumentException.class,
                                () -> alertaService.listarAlertasCelo(EMAIL, ESTABLECIMIENTO_ID, null));
        }

        @Test
        void listarAlertasCelo_ThrowsAccessDeniedException_WhenUserNotBelongsToEstablecimiento() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(Collections.emptyList()); // Usuario no está en ningún establecimiento

                // Act & Assert
                assertThrows(AccessDeniedException.class,
                                () -> alertaService.listarAlertasCelo(EMAIL, ESTABLECIMIENTO_ID, null));
        }

        // --- Tests para listarAlertasSalud ---

        @Test
        void listarAlertasSalud_Success() {
                // Arrange
                mockAlerta.setDisparador(DisparadorAlerta.SALUD);
                mockAlerta.setAccionRecomendada("Revisar al animal y realizar seguimiento clínico.");
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findSaludByEstablecimiento(ESTABLECIMIENTO_ID))
                                .thenReturn(List.of(mockAlerta));

                // Act
                List<AlertaResponse> result = alertaService.listarAlertasSalud(EMAIL, ESTABLECIMIENTO_ID, null);

                // Assert
                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals(ALERTA_ID, result.get(0).getId());
                assertEquals("Revisar al animal y realizar seguimiento clínico.",
                                result.get(0).getAccionRecomendada());
                verify(alertaRepository, times(1)).findSaludByEstablecimiento(ESTABLECIMIENTO_ID);
        }

        @Test
        void listarAlertasSalud_ThrowsIllegalArgumentException_WhenUsuarioNotFound() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

                // Act & Assert
                assertThrows(IllegalArgumentException.class,
                                () -> alertaService.listarAlertasSalud(EMAIL, ESTABLECIMIENTO_ID, null));
        }

        @Test
        void listarAlertasSalud_ThrowsAccessDeniedException_WhenUserNotBelongsToEstablecimiento() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(Collections.emptyList()); // Usuario no está en ningún establecimiento

                // Act & Assert
                assertThrows(AccessDeniedException.class,
                                () -> alertaService.listarAlertasSalud(EMAIL, ESTABLECIMIENTO_ID, null));
        }

        // --- Tests para obtenerDetalleAlerta ---

        @Test
        @SuppressWarnings("null")
        void obtenerDetalleAlerta_Success() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));

                // Act
                AlertaDetalleResponse result = alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                // Assert
                assertNotNull(result);
                assertEquals(ALERTA_ID, result.getId());
                assertEquals("CAR-123", result.getAnimalCaravanaOficial());
                assertEquals("12 horas", result.getVentanaOptimaHoras());
                assertEquals("Planificar inseminación artificial dentro de la ventana óptima.",
                                result.getAccionRecomendada());
        }

        @Test
        @SuppressWarnings("null")
        void obtenerDetalleAlerta_FueraDePerimetro_ExponePosicionYHoraDeSalida() {
                // Arrange
                LocalDateTime horaSalida = LocalDateTime.now().minusMinutes(30);
                mockAlerta.setDisparador(DisparadorAlerta.FUERA_DE_PERIMETRO);
                mockAlerta.setTsGeneracion(horaSalida);
                mockAlerta.setLatitud(-34.6037);
                mockAlerta.setLongitud(-58.3816);

                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));

                // Act
                AlertaDetalleResponse result = alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                // Assert
                assertEquals(-34.6037, result.getLatitud());
                assertEquals(-58.3816, result.getLongitud());
                assertEquals(horaSalida, result.getTsGeneracion());
                assertNotNull(result.getDetalleGeoespacial());
                assertEquals(-34.6037, result.getDetalleGeoespacial().getLatitud());
                assertEquals(-58.3816, result.getDetalleGeoespacial().getLongitud());
        }

        @Test
        @SuppressWarnings("null")
        void obtenerDetalleAlerta_FueraDePerimetro_Pendiente_CalculaTiempoFueraHastaAhora() {
                // Arrange
                mockAlerta.setDisparador(DisparadorAlerta.FUERA_DE_PERIMETRO);
                mockAlerta.setTsGeneracion(LocalDateTime.now().minusHours(2).minusMinutes(15));

                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));

                // Act
                AlertaDetalleResponse result = alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                // Assert: 2h 15m = 8100 s (margen por el tiempo de ejecución del test)
                long segundosFuera = result.getDetalleGeoespacial().getSegundosFuera();
                assertTrue(segundosFuera >= 8100 && segundosFuera <= 8105,
                                "segundosFuera fuera de rango: " + segundosFuera);
        }

        @Test
        @SuppressWarnings("null")
        void obtenerDetalleAlerta_FueraDePerimetro_Resuelta_FijaTiempoFueraAlMomentoDeResolucion() {
                // Arrange
                LocalDateTime horaSalida = LocalDateTime.now().minusDays(1);
                mockAlerta.setDisparador(DisparadorAlerta.FUERA_DE_PERIMETRO);
                mockAlerta.setTsGeneracion(horaSalida);
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);
                mockAlerta.setTsConfirmacion(horaSalida.plusMinutes(45));

                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));

                // Act
                AlertaDetalleResponse result = alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                // Assert
                assertEquals(45 * 60L, result.getDetalleGeoespacial().getSegundosFuera());
        }

        @Test
        void listarAlertas_FueraDePerimetro_ExponePosicion() {
                // Arrange
                mockAlerta.setDisparador(DisparadorAlerta.FUERA_DE_PERIMETRO);
                mockAlerta.setLatitud(-34.6037);
                mockAlerta.setLongitud(-58.3816);
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findByFiltros(ESTABLECIMIENTO_ID, null, null, null))
                                .thenReturn(List.of(mockAlerta));

                // Act
                List<AlertaResponse> result = alertaService.listarAlertas(EMAIL, ESTABLECIMIENTO_ID, null, null, null);

                // Assert
                assertEquals(1, result.size());
                assertEquals(-34.6037, result.get(0).getLatitud());
                assertEquals(-58.3816, result.get(0).getLongitud());
                assertEquals(mockAlerta.getTsGeneracion(), result.get(0).getTsGeneracion());
        }

        @Test
        @SuppressWarnings("null")
        void obtenerDetalleAlerta_ThrowsAccessDeniedException_WhenAlertNotBelongsToEstablecimiento() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));

                Establecimiento otroEstablecimiento = new Establecimiento();
                otroEstablecimiento.setId(99L);
                mockAlerta.getAnimal().setEstablecimiento(otroEstablecimiento);

                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));

                // Act & Assert
                AccessDeniedException exception = assertThrows(AccessDeniedException.class,
                                () -> alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID));
                assertEquals("La alerta no pertenece al establecimiento indicado.", exception.getMessage());
        }

        @Test
        @SuppressWarnings("null")
        void obtenerDetalleAlerta_ThrowsIllegalArgumentException_WhenAlertNotFound() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.empty());

                // Act & Assert
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID));
                assertEquals("Alerta no encontrada.", exception.getMessage());
        }

        // --- Tests para el detalle sanitario (snapshot de serie comparativa) ---

        @SuppressWarnings("null")
        private void prepararAlertaSanitaria(String serieComparativaJson) {
                mockAlerta.setDisparador(DisparadorAlerta.SALUD);
                mockAlerta.setSeveridad(SeveridadAlerta.MEDIA);
                mockAlerta.setDesvioDetectado(true);
                mockAlerta.setPosibleCausa("Enfermedad moderada / Cojera clínica");
                mockAlerta.setConfianzaModelo(0.82f);
                mockAlerta.setValorUmbral(0.5);
                mockAlerta.setValorDetectado(0.82);
                mockAlerta.setTipo("SALUD");
                mockAlerta.setSerieComparativaJson(serieComparativaJson);

                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
        }

        private String snapshotJson() throws Exception {
                SerieComparativaSnapshot snap = SerieComparativaSnapshot.builder()
                                .descripcionDesvio(
                                                "Actividad promedio 66% por debajo del perfil de referencia del animal "
                                                                + "en el bloque 0-6h. Severidad media. Posible causa: Enfermedad moderada / Cojera clínica.")
                                .ventanaDesde(LocalDateTime.of(2026, 1, 15, 0, 0))
                                .ventanaHasta(LocalDateTime.of(2026, 1, 15, 23, 55))
                                .perfilCalculadoEn(LocalDateTime.of(2026, 1, 10, 8, 0))
                                .serieActividad(List.of(
                                                BloqueComparativoResponse.builder().bloque("0-6h").actual(1.0)
                                                                .referenciaMedia(3.0).referenciaDesvio(0.5).build(),
                                                BloqueComparativoResponse.builder().bloque("6-12h").actual(5.0)
                                                                .referenciaMedia(2.0).referenciaDesvio(0.4).build(),
                                                BloqueComparativoResponse.builder().bloque("12-18h").actual(5.0)
                                                                .referenciaMedia(4.0).referenciaDesvio(0.6).build(),
                                                BloqueComparativoResponse.builder().bloque("18-24h").actual(5.0)
                                                                .referenciaMedia(3.5).referenciaDesvio(0.5).build()))
                                .serieDesplazamiento(List.of(
                                                BloqueComparativoResponse.builder().bloque("0-6h").actual(9.8)
                                                                .referenciaMedia(10.0).referenciaDesvio(2.0).build(),
                                                BloqueComparativoResponse.builder().bloque("6-12h").actual(10.0)
                                                                .referenciaMedia(8.0).referenciaDesvio(1.5).build(),
                                                BloqueComparativoResponse.builder().bloque("12-18h").actual(10.0)
                                                                .referenciaMedia(12.0).referenciaDesvio(2.5).build(),
                                                BloqueComparativoResponse.builder().bloque("18-24h").actual(10.0)
                                                                .referenciaMedia(9.0).referenciaDesvio(2.0).build()))
                                .build();
                return objectMapper.writeValueAsString(snap);
        }

        @Test
        @SuppressWarnings("null")
        void obtenerDetalleAlerta_SALUD_conSnapshot_deserializaSerieComparativa() throws Exception {
                prepararAlertaSanitaria(snapshotJson());

                AlertaDetalleResponse result = alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                assertNotNull(result.getDetalleSanitario());
                assertTrue(result.getDetalleSanitario().getDesvioDetectado());
                assertEquals("MEDIA", result.getDetalleSanitario().getSeveridad());
                assertEquals("Enfermedad moderada / Cojera clínica", result.getDetalleSanitario().getPosibleCausa());
                assertEquals(0.82, result.getDetalleSanitario().getConfianzaModelo(), 0.001);
                assertNotNull(result.getDetalleSanitario().getPerfilCalculadoEn());
                assertNotNull(result.getDetalleSanitario().getVentanaDesde());
                assertNotNull(result.getDetalleSanitario().getVentanaHasta());

                List<BloqueComparativoResponse> actividad = result.getDetalleSanitario().getSerieActividad();
                assertEquals(4, actividad.size());
                assertEquals("0-6h", actividad.get(0).getBloque());
                assertEquals("18-24h", actividad.get(3).getBloque());
                assertEquals(1.0, actividad.get(0).getActual(), 0.001);
                assertEquals(3.0, actividad.get(0).getReferenciaMedia(), 0.001);
                assertEquals(0.5, actividad.get(0).getReferenciaDesvio(), 0.001);

                assertEquals(4, result.getDetalleSanitario().getSerieDesplazamiento().size());
                assertEquals(10.0, result.getDetalleSanitario().getSerieDesplazamiento().get(0).getReferenciaMedia(),
                                0.001);

                // la descripción viene del snapshot, no se regenera
                assertTrue(result.getDetalleSanitario().getDescripcionDesvio().contains("0-6h"));

                // el detalle top-level expone el mismo dato puntual para cualquier tipo de alerta
                assertEquals(0.82, result.getValorDetectado(), 0.001);
                assertEquals(0.5, result.getValorUmbral(), 0.001);
                assertEquals(result.getDetalleSanitario().getDescripcionDesvio(), result.getDescripcionDatoOrigen());
        }

        @Test
        @SuppressWarnings("null")
        void obtenerDetalleAlerta_SALUD_sinSnapshot_seriesNullDescripcionGenerica() {
                prepararAlertaSanitaria(null);

                AlertaDetalleResponse result = alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                assertNotNull(result.getDetalleSanitario());
                assertEquals("MEDIA", result.getDetalleSanitario().getSeveridad());
                assertEquals("Enfermedad moderada / Cojera clínica", result.getDetalleSanitario().getPosibleCausa());
                assertNull(result.getDetalleSanitario().getSerieActividad());
                assertNull(result.getDetalleSanitario().getSerieDesplazamiento());
                assertNull(result.getDetalleSanitario().getVentanaDesde());
                assertNotNull(result.getDetalleSanitario().getDescripcionDesvio());
                assertFalse(result.getDetalleSanitario().getDescripcionDesvio().isBlank());
                assertEquals(result.getDetalleSanitario().getDescripcionDesvio(), result.getDescripcionDatoOrigen());
        }

        @Test
        @SuppressWarnings("null")
        void obtenerDetalleAlerta_CELO_exponeDescripcionNarrativa() {
                // mockAlerta (setUp) ya es CELO con valorDetectado=0.85 (confianza del modelo)
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));

                AlertaDetalleResponse result = alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                assertNull(result.getDetalleSanitario());
                assertEquals(DisparadorAlerta.CELO, result.getDatoDisparador());
                // el dato puntual se sigue exponiendo por trazabilidad...
                assertEquals(0.85, result.getValorDetectado(), 0.001);
                assertNull(result.getValorUmbral());
                // ...pero la descripción es en lenguaje natural: menciona el celo y la
                // confianza como porcentaje, sin decimales crudos ni umbrales.
                assertNotNull(result.getDescripcionDatoOrigen());
                assertTrue(result.getDescripcionDatoOrigen().toLowerCase().contains("celo"));
                assertTrue(result.getDescripcionDatoOrigen().contains("85%"));
        }

        /** Convierte mockAlerta (CELO) en una alerta de anestro como la genera AnestroDetectorService. */
        private void prepararAlertaAnestro() {
                mockAlerta.setTipo(Alerta.TIPO_ANESTRO);
                mockAlerta.setValorDetectado(60.0);
                mockAlerta.setValorUmbral(45.0);
                mockAlerta.setVentanaInicioIns(null);
                mockAlerta.setVentanaFinIns(null);
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
        }

        @Test
        void obtenerDetalleAlerta_Anestro_DescribeDiasSinCelo() {
                prepararAlertaAnestro();

                AlertaDetalleResponse result = alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                assertEquals("El animal no registra celo hace 60 días (umbral: 45 días).",
                                result.getDescripcionDatoOrigen());
        }

        @Test
        void obtenerEstadoVentanaInseminacion_Anestro_Rechaza() {
                prepararAlertaAnestro();

                assertThrows(IllegalArgumentException.class,
                                () -> alertaService.obtenerEstadoVentanaInseminacion(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID));
        }

        @Test
        void obtenerLineaTiempoAlertas_Anestro_ExponeSubtipoYMantieneTipoCelo() {
                mockAlerta.setTipo(Alerta.TIPO_ANESTRO);
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(animalRepository.findById(10L)).thenReturn(Optional.of(mockAnimal));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findByAnimalConFiltros(10L, DisparadorAlerta.CELO, null, null))
                                .thenReturn(List.of(mockAlerta));

                LineaTiempoEventoResponse evento = alertaService
                                .obtenerLineaTiempoAlertas(EMAIL, 10L, "celo", null, null).get(0);

                assertEquals("CELO", evento.getTipo());
                assertEquals(Alerta.TIPO_ANESTRO, evento.getSubtipo());
        }

        @Test
        void obtenerLineaTiempoAlertas_Anestro_ExponeDiasSinCicloYEstadoArchivada() {
                mockAlerta.setTipo(Alerta.TIPO_ANESTRO);
                mockAlerta.setEstado(EstadoAlerta.ARCHIVADA);
                mockAlerta.setAccionRecomendada(
                                "Se requiere revisión veterinaria inmediata. El animal lleva 60 días sin ciclo registrado.");
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(animalRepository.findById(10L)).thenReturn(Optional.of(mockAnimal));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findByAnimalConFiltros(10L, DisparadorAlerta.CELO, null, null))
                                .thenReturn(List.of(mockAlerta));

                LineaTiempoEventoResponse evento = alertaService
                                .obtenerLineaTiempoAlertas(EMAIL, 10L, "celo", null, null).get(0);

                assertEquals("Se requiere revisión veterinaria inmediata. El animal lleva 60 días sin ciclo registrado.",
                                evento.getDescripcion());
                assertEquals("ARCHIVADA", evento.getEstadoResolucion());
        }

        @Test
        void archivarAlertaPorVencimiento_Anestro_RechazaYNoGuarda() {
                prepararAlertaAnestro();

                assertThrows(IllegalArgumentException.class,
                                () -> alertaService.archivarAlertaPorVencimiento(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID));
                verify(alertaRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void obtenerDetalleAlerta_SALUD_snapshotCorrupto_noRompe() {
                prepararAlertaSanitaria("{ esto no es json");

                AlertaDetalleResponse result = alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                assertNotNull(result.getDetalleSanitario());
                assertNull(result.getDetalleSanitario().getSerieActividad());
                assertEquals("MEDIA", result.getDetalleSanitario().getSeveridad());
                assertNotNull(result.getDetalleSanitario().getDescripcionDesvio());
        }

        @Test
        @SuppressWarnings("null")
        void obtenerDetalleAlerta_CELO_noTraeDetalleSanitario() {
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));

                AlertaDetalleResponse result = alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                assertNull(result.getDetalleSanitario());
        }


        private AlertaDetalleResponse detalleConUltimaSenial(LocalDateTime ultimaSenial) {
                dev.vigia.backend.model.Caravana caravana = new dev.vigia.backend.model.Caravana();
                caravana.setUltimaSenial(ultimaSenial);
                mockAnimal.setCaravana(caravana);
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                return alertaService.obtenerDetalleAlerta(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);
        }

        @Test
        void obtenerDetalleAlerta_CELO_collarSinTransmitirMasDe2h_marcaDesactualizado() {
                LocalDateTime hace3h = LocalDateTime.now().minusHours(3);

                AlertaDetalleResponse result = detalleConUltimaSenial(hace3h);

                assertTrue(result.isDatoPosiblementeDesactualizado());
                assertEquals(hace3h, result.getCaravanaUltimaSenial());
                assertEquals(2, result.getHorasUmbralDatoDesactualizado());
        }

        @Test
        void obtenerDetalleAlerta_CELO_collarTransmitiendo_noMarcaDesactualizado() {
                AlertaDetalleResponse result = detalleConUltimaSenial(LocalDateTime.now().minusMinutes(30));

                assertFalse(result.isDatoPosiblementeDesactualizado());
        }

        @Test
        void obtenerDetalleAlerta_CELO_sinTransmisiones_marcaDesactualizado() {
                AlertaDetalleResponse result = detalleConUltimaSenial(null);

                assertTrue(result.isDatoPosiblementeDesactualizado());
                assertNull(result.getCaravanaUltimaSenial());
        }

        @Test
        void obtenerDetalleAlerta_CELO_yaResuelta_noMarcaDesactualizado() {
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);

                AlertaDetalleResponse result = detalleConUltimaSenial(LocalDateTime.now().minusHours(5));

                assertFalse(result.isDatoPosiblementeDesactualizado());
        }

        @Test
        void obtenerDetalleAlerta_Anestro_noMarcaDesactualizado() {
                mockAlerta.setTipo(Alerta.TIPO_ANESTRO);
                mockAlerta.setValorDetectado(60.0);
                mockAlerta.setValorUmbral(45.0);

                AlertaDetalleResponse result = detalleConUltimaSenial(LocalDateTime.now().minusHours(5));

                assertFalse(result.isDatoPosiblementeDesactualizado());
        }

        @Test
        void listarAlertasCelo_collarSinTransmitir_marcaDesactualizadoEnListado() {
                dev.vigia.backend.model.Caravana caravana = new dev.vigia.backend.model.Caravana();
                caravana.setUltimaSenial(LocalDateTime.now().minusHours(4));
                mockAnimal.setCaravana(caravana);
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findCeloByEstablecimiento(ESTABLECIMIENTO_ID))
                                .thenReturn(List.of(mockAlerta));

                AlertaResponse result = alertaService.listarAlertasCelo(EMAIL, ESTABLECIMIENTO_ID, null).get(0);

                assertTrue(result.isDatoPosiblementeDesactualizado());
                assertEquals(caravana.getUltimaSenial(), result.getCaravanaUltimaSenial());
                assertEquals(2, result.getHorasUmbralDatoDesactualizado());
        }

        @Test
        void listarAlertasCelo_resuelta_noCargaCollarNiMarca() {
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);
                dev.vigia.backend.model.Caravana caravana = new dev.vigia.backend.model.Caravana();
                caravana.setUltimaSenial(LocalDateTime.now().minusHours(4));
                mockAnimal.setCaravana(caravana);
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findCeloByEstablecimiento(ESTABLECIMIENTO_ID))
                                .thenReturn(List.of(mockAlerta));

                AlertaResponse result = alertaService.listarAlertasCelo(EMAIL, ESTABLECIMIENTO_ID, null).get(0);

                assertFalse(result.isDatoPosiblementeDesactualizado());
                assertNull(result.getCaravanaUltimaSenial());
        }

        @Test
        void obtenerDetalleAlerta_SALUD_noMarcaDesactualizado() {
                mockAlerta.setDisparador(DisparadorAlerta.SALUD);

                AlertaDetalleResponse result = detalleConUltimaSenial(LocalDateTime.now().minusHours(5));

                assertFalse(result.isDatoPosiblementeDesactualizado());
        }

        // --- Tests para actualizarEstadoAlerta ---

        @Test
        @SuppressWarnings("null")
        void actualizarEstadoAlerta_Success_Confirmada() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.save(mockAlerta)).thenReturn(mockAlerta);

                // Act
                AlertaResponse result = alertaService.actualizarEstadoAlerta(EMAIL, ALERTA_ID, EstadoAlerta.CONFIRMADA,
                                "Notas");

                // Assert
                assertNotNull(result);
                assertEquals(EstadoAlerta.CONFIRMADA, result.getEstado());
                assertEquals("Planificar inseminación artificial dentro de la ventana óptima.",
                                result.getAccionRecomendada());
                verify(alertaRepository, times(1)).save(mockAlerta);
                verify(intervencionRepository, never()).save(any(Intervencion.class));
        }

        @Test
        void actualizarEstadoAlerta_ConfirmarAnestro_RegistraEstadoAnestro() {
                mockAlerta.setTipo(Alerta.TIPO_ANESTRO);
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.save(mockAlerta)).thenReturn(mockAlerta);

                alertaService.actualizarEstadoAlerta(EMAIL, ALERTA_ID, EstadoAlerta.CONFIRMADA, null);

                ArgumentCaptor<EstadoReproductivo> captor = ArgumentCaptor.forClass(EstadoReproductivo.class);
                verify(estadoReproductivoRepository).save(captor.capture());
                assertEquals(TipoReproductivo.ANESTRO, captor.getValue().getEstado());
                assertEquals(OrigenEstado.MANUAL, captor.getValue().getOrigen());

                // El evento SSE de actualización conserva el texto con los días (la tarjeta se reemplaza con él)
                ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
                verify(rabbitTemplate).convertAndSend(anyString(), anyString(), evento.capture());
                assertEquals(mockAlerta.getAccionRecomendada(),
                                ((dev.vigia.backend.dto.ml.AlertaEventDTO) evento.getValue()).getAccionRecomendada());
        }

        @Test
        void actualizarEstadoAlerta_ConfirmarCelo_RegistraEstadoCelo() {
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.save(mockAlerta)).thenReturn(mockAlerta);

                alertaService.actualizarEstadoAlerta(EMAIL, ALERTA_ID, EstadoAlerta.CONFIRMADA, null);

                ArgumentCaptor<EstadoReproductivo> captor = ArgumentCaptor.forClass(EstadoReproductivo.class);
                verify(estadoReproductivoRepository).save(captor.capture());
                assertEquals(TipoReproductivo.CELO, captor.getValue().getEstado());
        }

        @Test
        @SuppressWarnings("null")
        void actualizarEstadoAlerta_Success_Descartada() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.save(mockAlerta)).thenReturn(mockAlerta);

                // Act
                AlertaResponse result = alertaService.actualizarEstadoAlerta(EMAIL, ALERTA_ID, EstadoAlerta.DESCARTADA,
                                "Notas");

                // Assert
                assertNotNull(result);
                assertEquals(EstadoAlerta.DESCARTADA, result.getEstado());
                assertEquals("Planificar inseminación artificial dentro de la ventana óptima.",
                                result.getAccionRecomendada());
                verify(alertaRepository, times(1)).save(mockAlerta);
                verify(intervencionRepository, never()).save(any(Intervencion.class));
        }

        @Test
        @SuppressWarnings("null")
        void actualizarEstadoAlerta_ThrowsIllegalArgumentException_WhenUsuarioNotFound() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

                // Act & Assert
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> alertaService.actualizarEstadoAlerta(EMAIL, ALERTA_ID, EstadoAlerta.CONFIRMADA,
                                                "Notas"));
                assertEquals("Usuario no encontrado.", exception.getMessage());
                verify(alertaRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void actualizarEstadoAlerta_ThrowsIllegalArgumentException_WhenAlertaNotFound() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.empty());

                // Act & Assert
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> alertaService.actualizarEstadoAlerta(EMAIL, ALERTA_ID, EstadoAlerta.CONFIRMADA,
                                                "Notas"));
                assertEquals("Alerta no encontrada.", exception.getMessage());
                verify(alertaRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void actualizarEstadoAlerta_ThrowsAccessDeniedException_WhenUserNotBelongsToEstablecimiento() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(Collections.emptyList()); // sin pertenencia

                // Act & Assert
                assertThrows(AccessDeniedException.class,
                                () -> alertaService.actualizarEstadoAlerta(EMAIL, ALERTA_ID, EstadoAlerta.CONFIRMADA,
                                                "Notas"));
                verify(alertaRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void actualizarEstadoAlerta_ThrowsIllegalArgumentException_WhenAlertaYaConfirmada() {
                // Arrange: la alerta ya está CONFIRMADA, se intenta confirmar de nuevo
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);

                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));

                // Act & Assert
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> alertaService.actualizarEstadoAlerta(EMAIL, ALERTA_ID, EstadoAlerta.CONFIRMADA,
                                                "Notas"));
                assertEquals("Estado inválido para la actualización.", exception.getMessage());
                verify(alertaRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void actualizarEstadoAlerta_ThrowsIllegalArgumentException_WhenAlertaYaDescartada() {
                // Arrange: alerta ya DESCARTADA, se intenta confirmar
                mockAlerta.setEstado(EstadoAlerta.DESCARTADA);

                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));

                // Act & Assert
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> alertaService.actualizarEstadoAlerta(EMAIL, ALERTA_ID, EstadoAlerta.CONFIRMADA,
                                                "Notas"));
                assertEquals("Estado inválido para la actualización.", exception.getMessage());
                verify(alertaRepository, never()).save(any());
        }

        // --- Tests para intervenirAlerta ---

        @Test
        @SuppressWarnings("null")
        void intervenirAlerta_Success_CreatesIntervencion() {
                // Arrange: alerta ya confirmada, sin intervención previa
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(intervencionRepository.existsByAlertaId(ALERTA_ID)).thenReturn(false);

                IntervencionRequest request = IntervencionRequest.builder()
                                .descripcion("Se aplicó tratamiento antibiótico")
                                .fechaHora(LocalDateTime.of(2026, 1, 1, 10, 0))
                                .tipo(TipoIntervencion.TRATAMIENTO)
                                .build();

                // Act
                AlertaResponse result = alertaService.intervenirAlerta(EMAIL, ALERTA_ID, request);

                // Assert
                assertNotNull(result);
                assertEquals(EstadoAlerta.CONFIRMADA, result.getEstado());
                assertEquals("Planificar inseminación artificial dentro de la ventana óptima.",
                                result.getAccionRecomendada());

                ArgumentCaptor<Intervencion> captor = ArgumentCaptor.forClass(Intervencion.class);
                verify(intervencionRepository, times(1)).save(captor.capture());
                Intervencion guardada = captor.getValue();
                assertEquals(mockAlerta, guardada.getAlerta());
                assertEquals(mockAnimal, guardada.getAnimal());
                assertEquals(mockUsuario, guardada.getInterventor());
                assertEquals("Se aplicó tratamiento antibiótico", guardada.getDescripcion());
                assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), guardada.getFechaHora());
                assertEquals(TipoIntervencion.TRATAMIENTO, guardada.getTipo());

                // La alerta no vuelve a confirmarse ni a guardarse: ya estaba confirmada.
                verify(alertaRepository, never()).save(any());
                // Un tratamiento no deriva en ningún cambio de estado.
                verify(estadoReproductivoRepository, never()).save(any());
                verify(estadoSaludRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void intervenirAlerta_Inseminacion_RegistraEstadoReproductivoInseminada() {
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(intervencionRepository.existsByAlertaId(ALERTA_ID)).thenReturn(false);

                IntervencionRequest request = IntervencionRequest.builder()
                                .descripcion("IATF")
                                .fechaHora(LocalDateTime.of(2026, 3, 1, 9, 0))
                                .tipo(TipoIntervencion.INSEMINACION)
                                .build();

                alertaService.intervenirAlerta(EMAIL, ALERTA_ID, request);

                ArgumentCaptor<EstadoReproductivo> captor = ArgumentCaptor.forClass(EstadoReproductivo.class);
                verify(estadoReproductivoRepository, times(1)).save(captor.capture());
                EstadoReproductivo estado = captor.getValue();
                assertEquals(mockAnimal, estado.getAnimal());
                assertEquals(mockUsuario, estado.getUsuarioRegistrador());
                assertEquals(TipoReproductivo.INSEMINADA, estado.getEstado());
                assertEquals(OrigenEstado.MANUAL, estado.getOrigen());
                assertEquals(LocalDateTime.of(2026, 3, 1, 9, 0), estado.getFechaRegistro());
                assertEquals(LocalDateTime.of(2026, 3, 1, 9, 0).toLocalDate(), estado.getFechaUltInseminacion());
                verify(estadoSaludRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void intervenirAlerta_RevisionConEstadoSalud_RegistraEstadoSalud() {
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(intervencionRepository.existsByAlertaId(ALERTA_ID)).thenReturn(false);

                IntervencionRequest request = IntervencionRequest.builder()
                                .descripcion("Mastitis clínica en cuarto anterior izquierdo")
                                .fechaHora(LocalDateTime.of(2026, 4, 2, 15, 30))
                                .tipo(TipoIntervencion.REVISION_VETERINARIA)
                                .estadoSalud(TipoEstadoSalud.ENFERMO)
                                .build();

                alertaService.intervenirAlerta(EMAIL, ALERTA_ID, request);

                ArgumentCaptor<EstadoSalud> captor = ArgumentCaptor.forClass(EstadoSalud.class);
                verify(estadoSaludRepository, times(1)).save(captor.capture());
                EstadoSalud estado = captor.getValue();
                assertEquals(mockAnimal, estado.getAnimal());
                assertEquals(mockUsuario, estado.getUsuarioVeterinario());
                assertEquals(TipoEstadoSalud.ENFERMO, estado.getEstado());
                assertEquals(OrigenEstado.MANUAL, estado.getOrigen());
                assertEquals("Mastitis clínica en cuarto anterior izquierdo", estado.getDiagnosticoNotas());
                assertEquals(LocalDateTime.of(2026, 4, 2, 15, 30), estado.getFechaRegistro());
                verify(estadoReproductivoRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void intervenirAlerta_RevisionSinEstadoSalud_NoRegistraEstado() {
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(intervencionRepository.existsByAlertaId(ALERTA_ID)).thenReturn(false);

                IntervencionRequest request = IntervencionRequest.builder()
                                .descripcion("Revisión de rutina sin hallazgos")
                                .tipo(TipoIntervencion.REVISION_VETERINARIA)
                                .build();

                alertaService.intervenirAlerta(EMAIL, ALERTA_ID, request);

                verify(estadoSaludRepository, never()).save(any());
                verify(estadoReproductivoRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void intervenirAlerta_Success_UsesDefault_ForFechaHora() {
                // Arrange: descripción y tipo son obligatorios y se completan; fechaHora
                // queda sin especificar y debe completarse con un valor por defecto.
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(intervencionRepository.existsByAlertaId(ALERTA_ID)).thenReturn(false);

                IntervencionRequest request = IntervencionRequest.builder()
                                .descripcion("Revisión de rutina")
                                .tipo(TipoIntervencion.REVISION_VETERINARIA)
                                .build();

                // Act
                alertaService.intervenirAlerta(EMAIL, ALERTA_ID, request);

                // Assert: fechaHora, el único campo opcional, se completa con un valor por defecto
                ArgumentCaptor<Intervencion> captor = ArgumentCaptor.forClass(Intervencion.class);
                verify(intervencionRepository, times(1)).save(captor.capture());
                Intervencion guardada = captor.getValue();
                assertEquals("Revisión de rutina", guardada.getDescripcion());
                assertNotNull(guardada.getFechaHora());
                assertEquals(TipoIntervencion.REVISION_VETERINARIA, guardada.getTipo());
        }

        @Test
        @SuppressWarnings("null")
        void intervenirAlerta_ThrowsIllegalArgumentException_WhenDescripcionEsVacia() {
                // Arrange: no debe poder registrarse una intervención sin descripción,
                // aunque el tipo esté completo.
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(intervencionRepository.existsByAlertaId(ALERTA_ID)).thenReturn(false);

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> alertaService.intervenirAlerta(EMAIL, ALERTA_ID,
                                                IntervencionRequest.builder().descripcion("   ")
                                                                .tipo(TipoIntervencion.OTRO).build()));
                assertEquals("La descripción es obligatoria para registrar una intervención.",
                                exception.getMessage());
                verify(intervencionRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void intervenirAlerta_ThrowsIllegalArgumentException_WhenTipoEsNulo() {
                // Arrange: no debe poder registrarse una intervención sin tipo,
                // aunque la descripción esté completa.
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(intervencionRepository.existsByAlertaId(ALERTA_ID)).thenReturn(false);

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> alertaService.intervenirAlerta(EMAIL, ALERTA_ID,
                                                IntervencionRequest.builder().descripcion("Tratamiento aplicado")
                                                                .build()));
                assertEquals("El tipo de intervención es obligatorio para registrar una intervención.",
                                exception.getMessage());
                verify(intervencionRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void intervenirAlerta_ThrowsIllegalArgumentException_WhenAlertaNotFound() {
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.empty());

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> alertaService.intervenirAlerta(EMAIL, ALERTA_ID, IntervencionRequest.builder().build()));
                assertEquals("Alerta no encontrada", exception.getMessage());
                verify(intervencionRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void intervenirAlerta_ThrowsIllegalArgumentException_WhenUsuarioNotFound() {
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class,
                                () -> alertaService.intervenirAlerta(EMAIL, ALERTA_ID, IntervencionRequest.builder().build()));
                verify(intervencionRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void intervenirAlerta_ThrowsAccessDeniedException_WhenUserNotBelongsToEstablecimiento() {
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(Collections.emptyList());

                assertThrows(AccessDeniedException.class,
                                () -> alertaService.intervenirAlerta(EMAIL, ALERTA_ID, IntervencionRequest.builder().build()));
                verify(intervencionRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void intervenirAlerta_ThrowsIllegalArgumentException_WhenAlertaNoConfirmada() {
                // Arrange: la alerta sigue PENDIENTE (setUp la deja así por defecto)
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> alertaService.intervenirAlerta(EMAIL, ALERTA_ID, IntervencionRequest.builder().build()));
                assertEquals("La alerta debe estar confirmada antes de registrar una intervención.",
                                exception.getMessage());
                verify(intervencionRepository, never()).save(any());
                verify(alertaRepository, never()).save(any());
        }

        @Test
        @SuppressWarnings("null")
        void intervenirAlerta_ThrowsIllegalArgumentException_WhenYaExisteIntervencionParaLaAlerta() {
                mockAlerta.setEstado(EstadoAlerta.CONFIRMADA);
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(intervencionRepository.existsByAlertaId(ALERTA_ID)).thenReturn(true);

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> alertaService.intervenirAlerta(EMAIL, ALERTA_ID, IntervencionRequest.builder().build()));
                assertEquals("Ya existe una intervención registrada para esta alerta.", exception.getMessage());
                verify(intervencionRepository, never()).save(any());
        }

        // --- Tests para obtenerEstadoVentanaInseminacion ---

        @Test
        @SuppressWarnings("null")
        void obtenerEstadoVentanaInseminacion_Success_Futura() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));

                // La ventana empieza en 2 horas
                mockAlerta.setVentanaInicioIns(LocalDateTime.now().plusHours(2));
                mockAlerta.setVentanaFinIns(LocalDateTime.now().plusHours(20));
                when(alertaRepository.findById(ALERTA_ID))
                                .thenReturn(Optional.of(mockAlerta));

                // Act
                VentanaInseminacionResponse result = alertaService.obtenerEstadoVentanaInseminacion(EMAIL,
                                ESTABLECIMIENTO_ID, ALERTA_ID);

                // Assert
                assertNotNull(result);
                assertEquals("FUTURA", result.getEstado());
                assertEquals(0.0, result.getPorcentajeTranscurrido());
                assertFalse(result.getProximaAVencer());
        }

        @Test
        @SuppressWarnings("null")
        void obtenerEstadoVentanaInseminacion_Success_Activa() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));

                // La ventana empezó hace 2 horas y termina en 10 horas (total 12)
                mockAlerta.setVentanaInicioIns(LocalDateTime.now().minusHours(2));
                mockAlerta.setVentanaFinIns(LocalDateTime.now().plusHours(10));
                when(alertaRepository.findById(ALERTA_ID))
                                .thenReturn(Optional.of(mockAlerta));

                // Act
                VentanaInseminacionResponse result = alertaService.obtenerEstadoVentanaInseminacion(EMAIL,
                                ESTABLECIMIENTO_ID, ALERTA_ID);

                // Assert
                assertNotNull(result);
                assertEquals("ACTIVA", result.getEstado());
                assertFalse(result.getProximaAVencer());
                assertTrue(result.getPorcentajeTranscurrido() > 0.0 && result.getPorcentajeTranscurrido() < 100.0);
        }

        @Test
        @SuppressWarnings("null")
        void obtenerEstadoVentanaInseminacion_Success_ProximaAVencer() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));

                // La ventana termina en 1 hora (< 2 horas)
                mockAlerta.setVentanaInicioIns(LocalDateTime.now().minusHours(10));
                mockAlerta.setVentanaFinIns(LocalDateTime.now().plusHours(1));
                when(alertaRepository.findById(ALERTA_ID))
                                .thenReturn(Optional.of(mockAlerta));

                // Act
                VentanaInseminacionResponse result = alertaService
                                .obtenerEstadoVentanaInseminacion(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                // Assert
                assertNotNull(result);
                assertEquals("ACTIVA", result.getEstado());
                assertTrue(result.getProximaAVencer());
        }

        @Test
        @SuppressWarnings("null")
        void obtenerEstadoVentanaInseminacion_Success_Vencida() {
                // Arrange
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));

                // La ventana terminó hace 2 horas
                mockAlerta.setVentanaInicioIns(LocalDateTime.now().minusHours(20));
                mockAlerta.setVentanaFinIns(LocalDateTime.now().minusHours(2));
                when(alertaRepository.findById(ALERTA_ID))
                                .thenReturn(Optional.of(mockAlerta));

                // Act
                VentanaInseminacionResponse result = alertaService
                                .obtenerEstadoVentanaInseminacion(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                // Assert
                assertNotNull(result);
                assertEquals("VENCIDA", result.getEstado());
                assertEquals(100.0, result.getPorcentajeTranscurrido());
                assertEquals(0L, result.getTiempoRestanteSegundos());
                assertFalse(result.getProximaAVencer());
        }

        // --- Tests dedicados a la actualización de toResponse ---

        @Test
        @SuppressWarnings("null")
        void toResponse_MapeaAccionRecomendada_CuandoEsNull() {
                mockAlerta.setAccionRecomendada(null);
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findCeloByEstablecimiento(ESTABLECIMIENTO_ID))
                                .thenReturn(List.of(mockAlerta));

                List<AlertaResponse> result = alertaService.listarAlertasCelo(EMAIL, ESTABLECIMIENTO_ID, null);

                assertNotNull(result);
                assertEquals(1, result.size());
                assertNull(result.get(0).getAccionRecomendada());
        }

        @Test
        @SuppressWarnings("null")
        void toResponse_MapeaAccionRecomendada_EnArchivarAlertaPorVencimiento() {
                mockAlerta.setAccionRecomendada("Planificar inseminación artificial dentro de la ventana óptima.");
                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
                when(alertaRepository.save(mockAlerta)).thenReturn(mockAlerta);

                AlertaResponse result = alertaService.archivarAlertaPorVencimiento(EMAIL, ESTABLECIMIENTO_ID, ALERTA_ID);

                assertNotNull(result);
                assertEquals(EstadoAlerta.ARCHIVADA, result.getEstado());
                assertEquals("Planificar inseminación artificial dentro de la ventana óptima.",
                                result.getAccionRecomendada());
                verify(alertaRepository, times(1)).save(mockAlerta);
        }
}

