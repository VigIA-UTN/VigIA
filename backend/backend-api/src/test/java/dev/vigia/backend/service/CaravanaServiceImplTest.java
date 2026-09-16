package dev.vigia.backend.service;

import dev.vigia.backend.dto.CaravanaRequest;
import dev.vigia.backend.dto.CaravanaResponse;
import dev.vigia.backend.exception.DevEuiDuplicadoException;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.impl.CaravanaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaravanaServiceImpl — Tests Unitarios")
class CaravanaServiceImplTest {

    @Mock
    private CaravanaRepository caravanaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EstablecimientoRepository establecimientoRepository;

    @InjectMocks
    private CaravanaServiceImpl caravanaService;

    private Establecimiento establecimiento;
    private Usuario productor;

    @BeforeEach
    void setUp() {
        establecimiento = new Establecimiento();
        establecimiento.setId(10L);
        establecimiento.setNombre("La Cabaña");

        productor = new Usuario();
        productor.setId(1L);
        productor.setEmail("productor@vigia.dev");
        productor.setRol(Rol.PRODUCTOR);
    }

    @Test
    @DisplayName("Debe crear una caravana DISPONIBLE, sin animal ni codigo, asociada al establecimiento indicado")
    void crearCaravanaSinCodigoExitoso() {
        CaravanaRequest request = CaravanaRequest.builder()
                .dev_eui("AA-BB-CC-DD")
                .alias("Collar 1")
                .establecimientoId(10L)
                .build();

        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(caravanaRepository.findByDevEui("AA-BB-CC-DD")).thenReturn(Optional.empty());
        when(caravanaRepository.save(any(Caravana.class))).thenAnswer(invocation -> {
            Caravana c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });

        CaravanaResponse response = caravanaService.crearCaravana(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getDevEui()).isEqualTo("AA-BB-CC-DD");
        assertThat(response.getAlias()).isEqualTo("Collar 1");
        assertThat(response.getCodigo()).isNull();
        assertThat(response.getFechaAsignacion()).isNull();
        assertThat(response.getEstadoSensor()).isEqualTo(EstadoSensor.DISPONIBLE);
        assertThat(response.getEstablecimientoId()).isEqualTo(10L);
        assertThat(response.getAnimalId()).isNull();

        verify(caravanaRepository, never()).findByCodigo(any());
        verify(caravanaRepository, times(1)).save(any(Caravana.class));
    }

    @Test
    @DisplayName("Debe crear una caravana con idCaravanaOficial, fijando la fecha de asignacion")
    void crearCaravanaConCodigoExitoso() {
        CaravanaRequest request = CaravanaRequest.builder()
                .dev_eui("AA-BB-CC-DD")
                .idCaravanaOficial("CAR-001")
                .establecimientoId(10L)
                .build();

        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(caravanaRepository.findByDevEui("AA-BB-CC-DD")).thenReturn(Optional.empty());
        when(caravanaRepository.findByCodigo("CAR-001")).thenReturn(Optional.empty());
        when(caravanaRepository.save(any(Caravana.class))).thenAnswer(invocation -> {
            Caravana c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });

        CaravanaResponse response = caravanaService.crearCaravana(request);

        assertThat(response.getCodigo()).isEqualTo("CAR-001");
        assertThat(response.getFechaAsignacion()).isNotNull();
    }

    @Test
    @DisplayName("Debe rechazar el alta con DevEuiDuplicadoException si el dev_eui ya existe")
    void crearCaravanaDevEuiDuplicado() {
        CaravanaRequest request = CaravanaRequest.builder()
                .dev_eui("AA-BB-CC-DD")
                .establecimientoId(10L)
                .build();

        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(caravanaRepository.findByDevEui("AA-BB-CC-DD")).thenReturn(Optional.of(new Caravana()));

        assertThatThrownBy(() -> caravanaService.crearCaravana(request))
                .isInstanceOf(DevEuiDuplicadoException.class)
                .hasMessageContaining("AA-BB-CC-DD");

        verify(caravanaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe rechazar el alta si el idCaravanaOficial ya existe")
    void crearCaravanaCodigoDuplicado() {
        CaravanaRequest request = CaravanaRequest.builder()
                .dev_eui("AA-BB-CC-DD")
                .idCaravanaOficial("CAR-001")
                .establecimientoId(10L)
                .build();

        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(caravanaRepository.findByDevEui("AA-BB-CC-DD")).thenReturn(Optional.empty());
        when(caravanaRepository.findByCodigo("CAR-001")).thenReturn(Optional.of(new Caravana()));

        assertThatThrownBy(() -> caravanaService.crearCaravana(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("codigo");

        verify(caravanaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe rechazar el alta si el establecimiento no existe")
    void crearCaravanaEstablecimientoInexistente() {
        CaravanaRequest request = CaravanaRequest.builder()
                .dev_eui("AA-BB-CC-DD")
                .establecimientoId(999L)
                .build();

        when(establecimientoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caravanaService.crearCaravana(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Establecimiento no encontrado");

        verify(caravanaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe listar las caravanas del establecimiento, incluyendo el animal vinculado si corresponde, cuando el usuario tiene acceso")
    void listarCaravanasIncluyeAnimalVinculado() {
        establecimiento.setEmpleados(List.of(new Empleado(establecimiento, productor)));

        Caravana disponible = new Caravana();
        disponible.setId(1L);
        disponible.setDevEui("DISPONIBLE-1");
        disponible.setEstablecimiento(establecimiento);
        disponible.setEstadoSensor(EstadoSensor.DISPONIBLE);

        Caravana vinculada = new Caravana();
        vinculada.setId(2L);
        vinculada.setDevEui("VINCULADO-1");
        vinculada.setEstablecimiento(establecimiento);
        vinculada.setEstadoSensor(EstadoSensor.VINCULADO);
        Animal animal = new Animal();
        animal.setId(5L);
        animal.setAlias("Lola");
        vinculada.setAnimal(animal);

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(caravanaRepository.findAllByEstablecimientoId(10L)).thenReturn(List.of(disponible, vinculada));

        List<CaravanaResponse> response = caravanaService.listarCaravanas("productor@vigia.dev", 10L);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getEstadoSensor()).isEqualTo(EstadoSensor.DISPONIBLE);
        assertThat(response.get(0).getAnimalId()).isNull();
        assertThat(response.get(1).getEstadoSensor()).isEqualTo(EstadoSensor.VINCULADO);
        assertThat(response.get(1).getAnimalId()).isEqualTo(5L);
        assertThat(response.get(1).getAnimalAlias()).isEqualTo("Lola");
    }

    @Test
    @DisplayName("Debe rechazar el listado si el usuario no pertenece al establecimiento")
    void listarCaravanasUsuarioSinAcceso() {
        establecimiento.setEmpleados(List.of());

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));

        assertThatThrownBy(() -> caravanaService.listarCaravanas("productor@vigia.dev", 10L))
                .isInstanceOf(AccessDeniedException.class);

        verify(caravanaRepository, never()).findAllByEstablecimientoId(any());
    }

    @Test
    @DisplayName("Debe permitir el listado a un ADMINISTRADOR aunque no sea empleado del establecimiento")
    void listarCaravanasAdminSinSerEmpleado() {
        Usuario admin = new Usuario();
        admin.setId(2L);
        admin.setEmail("admin@vigia.dev");
        admin.setRol(Rol.ADMINISTRADOR);
        establecimiento.setEmpleados(List.of());

        when(usuarioRepository.findByEmail("admin@vigia.dev")).thenReturn(Optional.of(admin));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(caravanaRepository.findAllByEstablecimientoId(10L)).thenReturn(List.of());

        List<CaravanaResponse> response = caravanaService.listarCaravanas("admin@vigia.dev", 10L);

        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("Debe rechazar el listado si el establecimiento no existe")
    void listarCaravanasEstablecimientoInexistente() {
        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(establecimientoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caravanaService.listarCaravanas("productor@vigia.dev", 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Establecimiento no encontrado");

        verify(caravanaRepository, never()).findAllByEstablecimientoId(any());
    }
}
