package dev.vigia.backend.service;

import dev.vigia.backend.dto.SensorRequest;
import dev.vigia.backend.dto.SensorResponse;
import dev.vigia.backend.exception.DevEuiDuplicadoException;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.service.impl.SensorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SensorServiceImpl — Tests Unitarios")
class SensorServiceImplTest {

    @Mock
    private CaravanaRepository caravanaRepository;
    @Mock
    private EstablecimientoRepository establecimientoRepository;

    @InjectMocks
    private SensorServiceImpl sensorService;

    private Establecimiento establecimiento;

    @BeforeEach
    void setUp() {
        establecimiento = new Establecimiento();
        establecimiento.setId(10L);
        establecimiento.setNombre("La Cabaña");
    }

    @Test
    @DisplayName("Debe crear un sensor DISPONIBLE, sin animal, asociado al establecimiento indicado")
    void crearSensorExitoso() {
        SensorRequest request = SensorRequest.builder()
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

        SensorResponse response = sensorService.crearSensor(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getDevEui()).isEqualTo("AA-BB-CC-DD");
        assertThat(response.getAlias()).isEqualTo("Collar 1");
        assertThat(response.getEstadoSensor()).isEqualTo(EstadoSensor.DISPONIBLE);
        assertThat(response.getEstablecimientoId()).isEqualTo(10L);
        assertThat(response.getAnimalId()).isNull();

        verify(caravanaRepository, times(1)).save(any(Caravana.class));
    }

    @Test
    @DisplayName("Debe rechazar el alta con DevEuiDuplicadoException si el dev_eui ya existe")
    void crearSensorDevEuiDuplicado() {
        SensorRequest request = SensorRequest.builder()
                .dev_eui("AA-BB-CC-DD")
                .establecimientoId(10L)
                .build();

        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(caravanaRepository.findByDevEui("AA-BB-CC-DD")).thenReturn(Optional.of(new Caravana()));

        assertThatThrownBy(() -> sensorService.crearSensor(request))
                .isInstanceOf(DevEuiDuplicadoException.class)
                .hasMessageContaining("AA-BB-CC-DD");

        verify(caravanaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe rechazar el alta si el establecimiento no existe")
    void crearSensorEstablecimientoInexistente() {
        SensorRequest request = SensorRequest.builder()
                .dev_eui("AA-BB-CC-DD")
                .establecimientoId(999L)
                .build();

        when(establecimientoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sensorService.crearSensor(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Establecimiento no encontrado");

        verify(caravanaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe listar los sensores del establecimiento, incluyendo el animal vinculado si corresponde")
    void listarSensoresIncluyeAnimalVinculado() {
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

        when(establecimientoRepository.findById(10L)).thenReturn(Optional.of(establecimiento));
        when(caravanaRepository.findAllByEstablecimientoId(10L)).thenReturn(List.of(disponible, vinculada));

        List<SensorResponse> response = sensorService.listarSensores(10L);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getEstadoSensor()).isEqualTo(EstadoSensor.DISPONIBLE);
        assertThat(response.get(0).getAnimalId()).isNull();
        assertThat(response.get(1).getEstadoSensor()).isEqualTo(EstadoSensor.VINCULADO);
        assertThat(response.get(1).getAnimalId()).isEqualTo(5L);
        assertThat(response.get(1).getAnimalAlias()).isEqualTo("Lola");
    }

    @Test
    @DisplayName("Debe rechazar el listado si el establecimiento no existe")
    void listarSensoresEstablecimientoInexistente() {
        when(establecimientoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sensorService.listarSensores(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Establecimiento no encontrado");

        verify(caravanaRepository, never()).findAllByEstablecimientoId(any());
    }
}
