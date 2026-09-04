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
import dev.vigia.backend.dto.SerieComparativaSnapshot;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
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
        private RabbitTemplate rabbitTemplate;

        @Spy
        private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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
                mockAlerta.setValorDetectado(98.5);
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
                verify(alertaRepository, times(1)).findCeloByEstablecimiento(ESTABLECIMIENTO_ID);
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

        private void prepararAlertaSanitaria(String serieComparativaJson) {
                mockAlerta.setDisparador(DisparadorAlerta.SALUD);
                mockAlerta.setSeveridad(SeveridadAlerta.MEDIA);
                mockAlerta.setDesvioDetectado(true);
                mockAlerta.setPosibleCausa("Enfermedad moderada / Cojera clínica");
                mockAlerta.setConfianzaModelo(0.82f);
                mockAlerta.setTipo("SALUD");
                mockAlerta.setSerieComparativaJson(serieComparativaJson);

                when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(mockUsuario));
                when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                                .thenReturn(List.of(mockEstablecimiento));
                when(alertaRepository.findById(ALERTA_ID)).thenReturn(Optional.of(mockAlerta));
        }

        private String snapshotJson() throws Exception {
                SerieComparativaSnapshot snap = SerieComparativaSnapshot.builder()
                                .descripcionDesvio("Actividad promedio 66% por debajo del perfil de referencia del animal "
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
                verify(alertaRepository, times(1)).save(mockAlerta);
                verify(intervencionRepository, times(1)).save(any(Intervencion.class));
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
                verify(alertaRepository, times(1)).save(mockAlerta);
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
}
