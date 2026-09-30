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
import dev.vigia.backend.repository.AnimalRepository;
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
    @Mock
    private AnimalRepository animalRepository;

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

    @SuppressWarnings("null")
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

    @SuppressWarnings("null")
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
    @SuppressWarnings("null")
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
    @SuppressWarnings("null")
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
    @SuppressWarnings("null")
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

    @Test
    @DisplayName("Debe listar únicamente las caravanas disponibles para un usuario autorizado")
    void listarCaravanasDisponiblesExitoso() {
        Caravana libre = new Caravana();
        libre.setId(1L);
        libre.setDevEui("DISPO-1");
        libre.setAlias("Caravana Libre");
        libre.setEstadoSensor(EstadoSensor.DISPONIBLE);
        libre.setEstablecimiento(establecimiento);

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        establecimiento.setEmpleados(List.of(new Empleado(establecimiento, productor)));
        when(caravanaRepository.findAllByEstablecimientoIdAndEstadoSensor(10L, EstadoSensor.DISPONIBLE))
                .thenReturn(List.of(libre));

        List<CaravanaResponse> result = caravanaService.listarCaravanasDisponibles(10L, "productor@vigia.dev");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEstadoSensor()).isEqualTo(EstadoSensor.DISPONIBLE);
        assertThat(result.get(0).getDevEui()).isEqualTo("DISPO-1");
    }

    @Test
    @DisplayName("Debe denegar listar caravanas disponibles a un veterinario")
    void listarCaravanasDisponiblesVeterinarioDenegado() {
        Usuario vet = new Usuario();
        vet.setEmail("vet@vigia.dev");
        vet.setRol(Rol.VETERINARIO);

        when(usuarioRepository.findByEmail("vet@vigia.dev")).thenReturn(Optional.of(vet));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));

        assertThatThrownBy(() -> caravanaService.listarCaravanasDisponibles(10L, "vet@vigia.dev"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("VETERINARIO no tiene acceso");

        verify(caravanaRepository, never()).findAllByEstablecimientoIdAndEstadoSensor(any(), any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("darDeBajaCaravana debe marcar INACTIVO/no-activo una caravana DISPONIBLE")
    void darDeBajaCaravanaDisponibleExitoso() {
        Caravana caravana = new Caravana();
        caravana.setId(1L);
        caravana.setEstablecimiento(establecimiento);
        caravana.setEstadoSensor(EstadoSensor.DISPONIBLE);

        Usuario admin = new Usuario();
        admin.setEmail("admin@vigia.dev");
        admin.setRol(Rol.ADMINISTRADOR);

        when(usuarioRepository.findByEmail("admin@vigia.dev")).thenReturn(Optional.of(admin));
        when(caravanaRepository.findById(1L)).thenReturn(Optional.of(caravana));
        when(caravanaRepository.save(any(Caravana.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CaravanaResponse response = caravanaService.darDeBajaCaravana(1L, "Collar dañado", "admin@vigia.dev");

        assertThat(response.getEstadoSensor()).isEqualTo(EstadoSensor.INACTIVO);
        assertThat(response.getActivo()).isFalse();
        verify(animalRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("darDeBajaCaravana debe desvincular el animal cuando la caravana está VINCULADO")
    void darDeBajaCaravanaVinculadaDesvinculaAnimal() {
        Animal animal = new Animal();
        animal.setId(5L);

        Caravana caravana = new Caravana();
        caravana.setId(2L);
        caravana.setEstablecimiento(establecimiento);
        caravana.setEstadoSensor(EstadoSensor.VINCULADO);
        caravana.setAnimal(animal);

        Usuario admin = new Usuario();
        admin.setEmail("admin@vigia.dev");
        admin.setRol(Rol.ADMINISTRADOR);

        when(usuarioRepository.findByEmail("admin@vigia.dev")).thenReturn(Optional.of(admin));
        when(caravanaRepository.findById(2L)).thenReturn(Optional.of(caravana));
        when(caravanaRepository.save(any(Caravana.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CaravanaResponse response = caravanaService.darDeBajaCaravana(2L, null, "admin@vigia.dev");

        assertThat(animal.getCaravana()).isNull();
        assertThat(response.getAnimalId()).isNull();
        verify(animalRepository, times(1)).save(animal);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("darDeBajaCaravana debe rechazar una caravana ya INACTIVO con 400")
    void darDeBajaCaravanaYaInactivaRechazada() {
        Caravana caravana = new Caravana();
        caravana.setId(3L);
        caravana.setEstablecimiento(establecimiento);
        caravana.setEstadoSensor(EstadoSensor.INACTIVO);

        Usuario admin = new Usuario();
        admin.setEmail("admin@vigia.dev");
        admin.setRol(Rol.ADMINISTRADOR);

        when(usuarioRepository.findByEmail("admin@vigia.dev")).thenReturn(Optional.of(admin));
        when(caravanaRepository.findById(3L)).thenReturn(Optional.of(caravana));

        assertThatThrownBy(() -> caravanaService.darDeBajaCaravana(3L, null, "admin@vigia.dev"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(caravanaRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("darDeBajaCaravana debe rechazar el acceso de un usuario ajeno al establecimiento")
    void darDeBajaCaravanaUsuarioSinAcceso() {
        establecimiento.setEmpleados(List.of());

        Caravana caravana = new Caravana();
        caravana.setId(4L);
        caravana.setEstablecimiento(establecimiento);
        caravana.setEstadoSensor(EstadoSensor.DISPONIBLE);

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(caravanaRepository.findById(4L)).thenReturn(Optional.of(caravana));

        assertThatThrownBy(() -> caravanaService.darDeBajaCaravana(4L, null, "productor@vigia.dev"))
                .isInstanceOf(AccessDeniedException.class);

        verify(caravanaRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("reactivarCaravana debe marcar DISPONIBLE una caravana INACTIVO")
    void reactivarCaravanaExitoso() {
        Caravana caravana = new Caravana();
        caravana.setId(6L);
        caravana.setEstablecimiento(establecimiento);
        caravana.setEstadoSensor(EstadoSensor.INACTIVO);

        Usuario admin = new Usuario();
        admin.setEmail("admin@vigia.dev");
        admin.setRol(Rol.ADMINISTRADOR);

        when(usuarioRepository.findByEmail("admin@vigia.dev")).thenReturn(Optional.of(admin));
        when(caravanaRepository.findById(6L)).thenReturn(Optional.of(caravana));
        when(caravanaRepository.save(any(Caravana.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CaravanaResponse response = caravanaService.reactivarCaravana(6L, "admin@vigia.dev");

        assertThat(response.getEstadoSensor()).isEqualTo(EstadoSensor.DISPONIBLE);
        assertThat(response.getActivo()).isFalse();
    }

    @Test
    @DisplayName("listarCaravanas debe filtrar por estado cuando se indica")
    void listarCaravanasFiltraPorEstado() {
        establecimiento.setEmpleados(List.of(new Empleado(establecimiento, productor)));

        Caravana disponible = new Caravana();
        disponible.setId(1L);
        disponible.setEstablecimiento(establecimiento);
        disponible.setEstadoSensor(EstadoSensor.DISPONIBLE);

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(caravanaRepository.findAllByEstablecimientoIdAndEstadoSensor(10L, EstadoSensor.DISPONIBLE))
                .thenReturn(List.of(disponible));

        List<CaravanaResponse> result = caravanaService.listarCaravanas("productor@vigia.dev", 10L,
                EstadoSensor.DISPONIBLE, null);

        assertThat(result).hasSize(1);
        verify(caravanaRepository, never()).findAllByEstablecimientoId(any());
    }

    @Test
    @DisplayName("listarCaravanas debe filtrar por codigo cuando se indica")
    void listarCaravanasFiltraPorCodigo() {
        establecimiento.setEmpleados(List.of(new Empleado(establecimiento, productor)));

        Caravana conCodigo = new Caravana();
        conCodigo.setId(1L);
        conCodigo.setCodigo("CAR-001");
        conCodigo.setEstablecimiento(establecimiento);

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(caravanaRepository.findAllByEstablecimientoIdAndCodigoContainingIgnoreCase(10L, "CAR"))
                .thenReturn(List.of(conCodigo));

        List<CaravanaResponse> result = caravanaService.listarCaravanas("productor@vigia.dev", 10L, null, "CAR");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCodigo()).isEqualTo("CAR-001");
    }

    @Test
    @DisplayName("listarCaravanas debe filtrar por estado y codigo combinados")
    void listarCaravanasFiltraPorEstadoYCodigo() {
        establecimiento.setEmpleados(List.of(new Empleado(establecimiento, productor)));

        when(usuarioRepository.findByEmail("productor@vigia.dev")).thenReturn(Optional.of(productor));
        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(caravanaRepository.findAllByEstablecimientoIdAndEstadoSensorAndCodigoContainingIgnoreCase(10L,
                EstadoSensor.DISPONIBLE, "CAR")).thenReturn(List.of());

        List<CaravanaResponse> result = caravanaService.listarCaravanas("productor@vigia.dev", 10L,
                EstadoSensor.DISPONIBLE, "CAR");

        assertThat(result).isEmpty();
        verify(caravanaRepository, times(1))
                .findAllByEstablecimientoIdAndEstadoSensorAndCodigoContainingIgnoreCase(10L, EstadoSensor.DISPONIBLE,
                        "CAR");
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("reactivarCaravana debe rechazar una caravana que no está INACTIVO con 400")
    void reactivarCaravanaNoInactivaRechazada() {
        Caravana caravana = new Caravana();
        caravana.setId(7L);
        caravana.setEstablecimiento(establecimiento);
        caravana.setEstadoSensor(EstadoSensor.VINCULADO);

        Usuario admin = new Usuario();
        admin.setEmail("admin@vigia.dev");
        admin.setRol(Rol.ADMINISTRADOR);

        when(usuarioRepository.findByEmail("admin@vigia.dev")).thenReturn(Optional.of(admin));
        when(caravanaRepository.findById(7L)).thenReturn(Optional.of(caravana));

        assertThatThrownBy(() -> caravanaService.reactivarCaravana(7L, "admin@vigia.dev"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(caravanaRepository, never()).save(any());
    }
}
