package dev.vigia.backend.service;

import dev.vigia.backend.dto.CoordenadaDTO;
import dev.vigia.backend.dto.PerimetroRequest;
import dev.vigia.backend.dto.PerimetroResponse;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.PerimetroRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.impl.PerimetroServiceImpl;
import dev.vigia.backend.util.GeometryUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PerimetroServiceImpl — Tests Unitarios")
class PerimetroServiceImplTest {

    @Mock
    private PerimetroRepository perimetroRepository;
    @Mock
    private EstablecimientoRepository establecimientoRepository;
    @Mock
    private RodeoLoteRepository rodeoLoteRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private PerimetroServiceImpl perimetroService;

    private Usuario productor;
    private Establecimiento establecimiento;
    private RodeoLote lote;
    private List<CoordenadaDTO> coordenadasValidas;

    @BeforeEach
    void setUp() {
        productor = new Usuario();
        productor.setId(1L);
        productor.setEmail("productor@vigia.dev");

        establecimiento = new Establecimiento();
        establecimiento.setId(10L);
        establecimiento.setNombre("La Cabaña");

        Empleado empleado = new Empleado();
        empleado.setUsuario(productor);
        empleado.setEstablecimiento(establecimiento);
        empleado.setActivo(true);
        establecimiento.setEmpleados(List.of(empleado));

        lote = new RodeoLote(establecimiento);
        lote.setId(100L);
        lote.setNombre("Lote 1");

        coordenadasValidas = List.of(
                new CoordenadaDTO(-34.0, -58.0),
                new CoordenadaDTO(-34.0, -57.0),
                new CoordenadaDTO(-35.0, -57.0),
                new CoordenadaDTO(-35.0, -58.0));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Debe crear un perímetro exitosamente cuando el productor pertenece al establecimiento")
    void crearPerimetroExitoso() {
        PerimetroRequest request = PerimetroRequest.builder()
                .establecimientoId(10L)
                .nombre("Potrero Bajo")
                .descripcion("Zona baja")
                .loteIds(List.of(100L))
                .coordenadas(coordenadasValidas)
                .build();

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(rodeoLoteRepository.findAllById(List.of(100L))).thenReturn(List.of(lote));
        when(perimetroRepository.save(any(Perimetro.class))).thenAnswer(invocation -> {
            Perimetro p = invocation.getArgument(0);
            return p;
        });

        PerimetroResponse response = perimetroService.crearPerimetro(request, "productor@vigia.dev");

        assertThat(response).isNotNull();
        assertThat(response.getNombre()).isEqualTo("Potrero Bajo");
        assertThat(response.isActivo()).isTrue();
        assertThat(response.getLoteIds()).contains(100L);
        assertThat(response.getCoordenadas()).hasSize(5); // Anillo cerrado auto

        verify(perimetroRepository, times(1)).save(any(Perimetro.class));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Debe fallar con AccessDeniedException si el usuario no pertenece al establecimiento")
    void crearPerimetroUsuarioSinAcceso() {
        Usuario ajeno = new Usuario();
        ajeno.setId(2L);
        ajeno.setEmail("ajeno@vigia.dev");

        PerimetroRequest request = PerimetroRequest.builder()
                .establecimientoId(10L)
                .nombre("Potrero")
                .coordenadas(coordenadasValidas)
                .build();

        when(usuarioRepository.findByEmail("ajeno@vigia.dev")).thenReturn(Optional.of(ajeno));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));

        assertThatThrownBy(() -> perimetroService.crearPerimetro(request, "ajeno@vigia.dev"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("no pertenece al establecimiento");

        verify(perimetroRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Debe fallar si se intenta asignar un lote de otro establecimiento")
    void crearPerimetroConLoteDeOtroEstablecimiento() {
        Establecimiento otroEstablecimiento = new Establecimiento();
        otroEstablecimiento.setId(999L);

        RodeoLote loteAjeno = new RodeoLote(otroEstablecimiento);
        loteAjeno.setId(200L);

        PerimetroRequest request = PerimetroRequest.builder()
                .establecimientoId(10L)
                .nombre("Potrero")
                .loteIds(List.of(200L))
                .coordenadas(coordenadasValidas)
                .build();

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(rodeoLoteRepository.findAllById(List.of(200L))).thenReturn(List.of(loteAjeno));

        assertThatThrownBy(() -> perimetroService.crearPerimetro(request, "productor@vigia.dev"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no pertenece al establecimiento");

        verify(perimetroRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Debe desactivar y activar perímetro cambiando el estado activo")
    void desactivarYActivarPerimetro() {
        Perimetro perimetro = new Perimetro(establecimiento, "Potrero",
                GeometryUtils.crearPoligono(coordenadasValidas));

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(perimetroRepository.findById(1L)).thenReturn(Optional.of(perimetro));
        when(perimetroRepository.save(any(Perimetro.class))).thenReturn(perimetro);

        perimetroService.desactivarPerimetro(1L, "productor@vigia.dev");
        assertThat(perimetro.isActivo()).isFalse();

        perimetroService.activarPerimetro(1L, "productor@vigia.dev");
        assertThat(perimetro.isActivo()).isTrue();
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Debe crear perímetro con tolerancia y zona estricta indicadas")
    void crearPerimetroConUmbral() {
        PerimetroRequest request = PerimetroRequest.builder()
                .establecimientoId(10L)
                .nombre("Potrero Alto")
                .coordenadas(coordenadasValidas)
                .distanciaToleranciaMetros(new BigDecimal("50"))
                .zonaEstricta(true)
                .build();

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(perimetroRepository.save(any(Perimetro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PerimetroResponse response = perimetroService.crearPerimetro(request, "productor@vigia.dev");

        assertThat(response.getDistanciaToleranciaMetros()).isEqualByComparingTo("50");
        assertThat(response.isZonaEstricta()).isTrue();
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Debe crear perímetro con tolerancia 0 y zona no estricta si no se indican")
    void crearPerimetroConUmbralPorDefecto() {
        PerimetroRequest request = PerimetroRequest.builder()
                .establecimientoId(10L)
                .nombre("Potrero Alto")
                .coordenadas(coordenadasValidas)
                .build();

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(perimetroRepository.save(any(Perimetro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PerimetroResponse response = perimetroService.crearPerimetro(request, "productor@vigia.dev");

        assertThat(response.getDistanciaToleranciaMetros()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.isZonaEstricta()).isFalse();
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Debe fallar al crear perímetro con tolerancia negativa")
    void crearPerimetroConToleranciaNegativa() {
        PerimetroRequest request = PerimetroRequest.builder()
                .establecimientoId(10L)
                .nombre("Potrero Alto")
                .coordenadas(coordenadasValidas)
                .distanciaToleranciaMetros(new BigDecimal("-5"))
                .build();

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));

        assertThatThrownBy(() -> perimetroService.crearPerimetro(request, "productor@vigia.dev"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede ser negativa");

        verify(perimetroRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Debe actualizar tolerancia y zona estricta cuando se envían")
    void actualizarPerimetroConUmbral() {
        Perimetro perimetro = new Perimetro(establecimiento, "Potrero",
                GeometryUtils.crearPoligono(coordenadasValidas));

        PerimetroRequest request = PerimetroRequest.builder()
                .establecimientoId(10L)
                .nombre("Potrero")
                .coordenadas(coordenadasValidas)
                .distanciaToleranciaMetros(new BigDecimal("30.5"))
                .zonaEstricta(true)
                .build();

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(perimetroRepository.findById(1L)).thenReturn(Optional.of(perimetro));
        when(perimetroRepository.save(any(Perimetro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PerimetroResponse response = perimetroService.actualizarPerimetro(1L, request, "productor@vigia.dev");

        assertThat(response.getDistanciaToleranciaMetros()).isEqualByComparingTo("30.5");
        assertThat(response.isZonaEstricta()).isTrue();
        assertThat(perimetro.getToleranciaEfectivaMetros()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Debe conservar tolerancia y zona estricta si no se envían al actualizar")
    void actualizarPerimetroSinUmbralConservaValores() {
        Perimetro perimetro = new Perimetro(establecimiento, "Potrero",
                GeometryUtils.crearPoligono(coordenadasValidas));
        perimetro.configurarUmbral(new BigDecimal("40"), true);

        PerimetroRequest request = PerimetroRequest.builder()
                .establecimientoId(10L)
                .nombre("Potrero Renombrado")
                .coordenadas(coordenadasValidas)
                .build();

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(perimetroRepository.findById(1L)).thenReturn(Optional.of(perimetro));
        when(perimetroRepository.save(any(Perimetro.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PerimetroResponse response = perimetroService.actualizarPerimetro(1L, request, "productor@vigia.dev");

        assertThat(response.getNombre()).isEqualTo("Potrero Renombrado");
        assertThat(response.getDistanciaToleranciaMetros()).isEqualByComparingTo("40");
        assertThat(response.isZonaEstricta()).isTrue();
    }
}
