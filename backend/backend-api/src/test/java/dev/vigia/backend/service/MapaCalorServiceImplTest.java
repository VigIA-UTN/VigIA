package dev.vigia.backend.service;

import dev.vigia.backend.client.MlGeoClient;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.impl.MapaCalorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MapaCalorServiceImpl — Tests Unitarios")
class MapaCalorServiceImplTest {

    private static final String EMAIL = "productor@vigia.dev";
    private static final Long ESTABLECIMIENTO_ID = 10L;
    private static final LocalDateTime DESDE = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime HASTA = LocalDateTime.of(2026, 9, 23, 23, 59, 59);

    @Mock
    private TelemetriaRepository telemetriaRepository;
    @Mock
    private EstablecimientoRepository establecimientoRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private MlGeoClient mlGeoClient;

    @InjectMocks
    private MapaCalorServiceImpl service;

    private Establecimiento establecimiento;

    @BeforeEach
    void setUp() {
        establecimiento = new Establecimiento();
        establecimiento.setId(ESTABLECIMIENTO_ID);
    }

    private void usuarioProductor() {
        Usuario usuario = new Usuario();
        usuario.setEmail(EMAIL);
        usuario.setRol(Rol.PRODUCTOR);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL)).thenReturn(List.of(establecimiento));
    }

    private Animal animal(Long id, Establecimiento est) {
        Animal a = new Animal();
        a.setId(id);
        a.setEstablecimiento(est);
        return a;
    }

    private Telemetria telemetria(Animal animal, double lat, double lng) {
        Caravana c = new Caravana();
        c.setId(animal.getId() + 1000);
        c.setAnimal(animal);
        Telemetria t = new Telemetria();
        t.setCaravana(c);
        t.setLatitud(lat);
        t.setLongitud(lng);
        t.setFechaHora(DESDE.plusHours(1));
        return t;
    }

    private GeoAnalyzeRequestDTO requestEnviadoAMl() {
        ArgumentCaptor<GeoAnalyzeRequestDTO> captor = ArgumentCaptor.forClass(GeoAnalyzeRequestDTO.class);
        verify(mlGeoClient).analyze(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Sin filtros: manda todo el historial y ningún filtro de animales")
    void sinFiltros() {
        usuarioProductor();
        Animal a1 = animal(1L, establecimiento);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(ESTABLECIMIENTO_ID, DESDE, HASTA, null))
                .thenReturn(List.of(telemetria(a1, -34.0, -59.0)));
        when(mlGeoClient.analyze(any())).thenReturn(new GeoAnalyzeResponseDTO());

        service.obtenerMapaCalor(EMAIL, ESTABLECIMIENTO_ID, DESDE, HASTA, null, null);

        GeoAnalyzeRequestDTO request = requestEnviadoAMl();
        assertThat(request.getAnimalId()).isNull();
        assertThat(request.getAnimalIds()).isNull();
        assertThat(request.getHistorial7d()).hasSize(1);
        verifyNoInteractions(animalRepository);
    }

    @Test
    @DisplayName("Filtro por animal: manda animal_id y conserva el historial completo del campo")
    void filtroPorAnimal() {
        usuarioProductor();
        Animal a1 = animal(1L, establecimiento);
        Animal a2 = animal(2L, establecimiento);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(ESTABLECIMIENTO_ID, DESDE, HASTA, null))
                .thenReturn(List.of(telemetria(a1, -34.0, -59.0), telemetria(a2, -35.0, -60.0)));
        when(mlGeoClient.analyze(any())).thenReturn(new GeoAnalyzeResponseDTO());

        service.obtenerMapaCalor(EMAIL, ESTABLECIMIENTO_ID, DESDE, HASTA, 1L, null);

        GeoAnalyzeRequestDTO request = requestEnviadoAMl();
        assertThat(request.getAnimalId()).isEqualTo("1");
        assertThat(request.getAnimalIds()).isNull();
        assertThat(request.getHistorial7d()).hasSize(2);
    }

    @Test
    @DisplayName("Filtro por rodeo: manda los ids de los animales del rodeo de ese establecimiento")
    void filtroPorRodeo() {
        usuarioProductor();
        Establecimiento otro = new Establecimiento();
        otro.setId(99L);
        Animal a1 = animal(1L, establecimiento);
        Animal a2 = animal(2L, establecimiento);
        Animal ajeno = animal(3L, otro);
        when(animalRepository.findByRodeoLoteId(5L)).thenReturn(List.of(a1, a2, ajeno));
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(ESTABLECIMIENTO_ID, DESDE, HASTA, null))
                .thenReturn(List.of(telemetria(a1, -34.0, -59.0), telemetria(a2, -35.0, -60.0)));
        when(mlGeoClient.analyze(any())).thenReturn(new GeoAnalyzeResponseDTO());

        service.obtenerMapaCalor(EMAIL, ESTABLECIMIENTO_ID, DESDE, HASTA, null, 5L);

        GeoAnalyzeRequestDTO request = requestEnviadoAMl();
        assertThat(request.getAnimalId()).isNull();
        assertThat(request.getAnimalIds()).containsExactly("1", "2");
        assertThat(request.getHistorial7d()).hasSize(2);
    }

    @Test
    @DisplayName("Rodeo sin animales: manda lista vacía (mapa vacío), no null (todos)")
    void rodeoSinAnimales() {
        usuarioProductor();
        when(animalRepository.findByRodeoLoteId(5L)).thenReturn(List.of());
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(ESTABLECIMIENTO_ID, DESDE, HASTA, null))
                .thenReturn(List.of());
        when(mlGeoClient.analyze(any())).thenReturn(new GeoAnalyzeResponseDTO());

        service.obtenerMapaCalor(EMAIL, ESTABLECIMIENTO_ID, DESDE, HASTA, null, 5L);

        assertThat(requestEnviadoAMl().getAnimalIds()).isEmpty();
    }

    @Test
    @DisplayName("Animal y rodeo a la vez: IllegalArgumentException sin llamar a ml-geo")
    void animalYRodeo() {
        assertThatThrownBy(() -> service.obtenerMapaCalor(EMAIL, ESTABLECIMIENTO_ID, DESDE, HASTA, 1L, 5L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mlGeoClient);
    }

    @Test
    @DisplayName("Establecimiento ajeno: AccessDeniedException sin leer telemetría")
    void establecimientoAjeno() {
        Usuario usuario = new Usuario();
        usuario.setEmail(EMAIL);
        usuario.setRol(Rol.PRODUCTOR);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL)).thenReturn(List.of());

        assertThatThrownBy(() -> service.obtenerMapaCalor(EMAIL, ESTABLECIMIENTO_ID, DESDE, HASTA, null, 5L))
                .isInstanceOf(AccessDeniedException.class);
        verify(telemetriaRepository, never()).findByEstablecimientoAndFechaHoraBetween(any(), any(), any(), any());
        verifyNoInteractions(mlGeoClient, animalRepository);
    }
}
