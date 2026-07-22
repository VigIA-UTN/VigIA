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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import dev.vigia.backend.dto.AlertaDetalleResponse;
import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.impl.AlertaServiceImpl;

@ExtendWith(MockitoExtension.class)
public class AlertaServiceImplTest {

    @Mock
    private AlertaRepository alertaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EstablecimientoRepository establecimientoRepository;

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
}
