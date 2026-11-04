package dev.vigia.backend.service;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.CentroMandoResponse;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.PerfilReferencia;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoSaludRepository;
import dev.vigia.backend.repository.EstadoSaludRepository.UltimoEstadoSalud;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardServiceImpl")
class DashboardServiceImplTest {

    private static final String EMAIL = "productor@vigia.dev";
    private static final Long EST = 1L;
    private static final Long OTRO_EST = 2L;

    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private EstadoSaludRepository estadoSaludRepository;
    @Mock
    private AlertaRepository alertaRepository;
    @Mock
    private EstablecimientoRepository establecimientoRepository;
    @Mock
    private RodeoLoteRepository rodeoLoteRepository;
    @Mock
    private AlertaService alertaService;

    private DashboardServiceImpl service;
    private Establecimiento establecimiento;
    private RodeoLote rodeo1;
    private RodeoLote rodeo2;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl(animalRepository, estadoSaludRepository, alertaRepository,
                establecimientoRepository, rodeoLoteRepository, alertaService, 6, 20, 3, 35, 8);
        establecimiento = establecimiento(EST);
        rodeo1 = rodeo(10L, establecimiento);
        rodeo2 = rodeo(20L, establecimiento);
    }

    private static Establecimiento establecimiento(Long id) {
        Establecimiento e = new Establecimiento();
        e.setId(id);
        return e;
    }

    private static RodeoLote rodeo(Long id, Establecimiento establecimiento) {
        RodeoLote r = new RodeoLote();
        r.setId(id);
        r.setEstablecimiento(establecimiento);
        return r;
    }

    private static Animal animal(Long id, RodeoLote rodeo, boolean conCaravana, boolean conPerfil) {
        Animal a = new Animal();
        a.setId(id);
        a.setIdCaravanaOficial("A-" + id);
        a.setRodeoLote(rodeo);
        a.setFechaNacimiento(LocalDate.now().minusYears(3));
        a.setEstadoActual(EstadoAnimal.ACTIVO);
        if (conCaravana) {
            Caravana c = new Caravana();
            c.setId(100 + id);
            c.setUltimaSenial(LocalDateTime.now().minusMinutes(5));
            c.setBateria(80.0);
            a.setCaravana(c);
        }
        if (conPerfil) {
            a.setPerfilReferencia(new PerfilReferencia());
        }
        a.setEstadosReproductivos(new ArrayList<>());
        return a;
    }

    private static AlertaResponse pendiente(Long id, Long animalId, DisparadorAlerta tipo, String subtipo) {
        return AlertaResponse.builder().id(id).animalId(animalId).tipo(tipo.name()).subtipo(subtipo)
                .severidad("ALTA").estado(EstadoAlerta.PENDIENTE).tsGeneracion(LocalDateTime.now().minusHours(1))
                .build();
    }

    private void miembro() {
        when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                .thenReturn(List.of(establecimiento));
    }

    @Test
    @DisplayName("403 si el usuario no es del establecimiento")
    void noMiembro() {
        when(establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(EMAIL))
                .thenReturn(List.of(establecimiento(OTRO_EST)));

        assertThatThrownBy(() -> service.centroMando(EMAIL, EST, null)).isInstanceOf(AccessDeniedException.class);
        verify(animalRepository, never()).findParaDashboardByEstablecimientoIdAndEstadoActualNot(any(), any());
    }

    @Test
    @DisplayName("403 si el rodeo es de otro establecimiento y 400 si no existe")
    void rodeoAjenoOInexistente() {
        miembro();
        when(rodeoLoteRepository.findById(30L)).thenReturn(Optional.of(rodeo(30L, establecimiento(OTRO_EST))));
        when(rodeoLoteRepository.findById(40L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.centroMando(EMAIL, EST, 30L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.centroMando(EMAIL, EST, 40L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Arma cada animal con su estado actual y calcula las ventanas solo de los celos que la tienen")
    void armaAnimalesYVentanas() {
        miembro();
        Animal conPerfil = animal(1L, rodeo1, true, true);
        Animal calibrando = animal(2L, rodeo1, true, false);
        Animal sinCaravana = animal(3L, rodeo1, false, false);
        EstadoReproductivo actual = new EstadoReproductivo();
        actual.setEstado(TipoReproductivo.INSEMINADA);
        actual.setFechaUltInseminacion(LocalDate.now().minusDays(40));
        EstadoReproductivo anterior = new EstadoReproductivo();
        anterior.setEstado(TipoReproductivo.VACIA);
        conPerfil.setEstadosReproductivos(new ArrayList<>(List.of(actual, anterior)));
        when(animalRepository.findParaDashboardByEstablecimientoIdAndEstadoActualNot(EST, EstadoAnimal.INACTIVO))
                .thenReturn(List.of(conPerfil, calibrando, sinCaravana));
        when(estadoSaludRepository.findUltimoPorAnimal(anyCollection()))
                .thenReturn(List.of(salud(2L, "ENFERMO")));

        AlertaResponse celo = pendiente(50L, 1L, DisparadorAlerta.CELO, "Celo Detectado");
        AlertaResponse anestro = pendiente(51L, 2L, DisparadorAlerta.CELO, "Anestro Detectado");
        AlertaResponse salud = pendiente(52L, 3L, DisparadorAlerta.SALUD, "SALUD");
        when(alertaService.listarAlertas(EMAIL, EST, null, null, EstadoAlerta.PENDIENTE))
                .thenReturn(List.of(celo, anestro, salud));
        Alerta celoEntidad = new Alerta();
        celoEntidad.setId(50L);
        celoEntidad.setTsGeneracion(LocalDateTime.now().minusHours(10));
        when(alertaRepository.findAllById(List.of(50L))).thenReturn(List.of(celoEntidad));
        when(alertaRepository.findGeneradasDesde(eq(EST), any(), any())).thenReturn(List.of());

        CentroMandoResponse r = service.centroMando(EMAIL, EST, null);

        // Ventana de 6 a 24 h desde la detección: abierta, quedan 14 h.
        assertThat(r.getVentanas()).hasSize(1);
        assertThat(r.getVentanas().get(0).getAlerta().getId()).isEqualTo(50L);
        assertThat(r.getVentanas().get(0).getVentana().getEstado()).isEqualTo("ACTIVA");
        assertThat(r.getCobertura().getOk()).isEqualTo(1);
        assertThat(r.getCobertura().getCalibrando()).isEqualTo(1);
        assertThat(r.getCobertura().getSinCaravana()).isEqualTo(1);
        assertThat(r.getBajoObservacion().getEnfermos()).isEqualTo(1);
        assertThat(r.getEmbudo().getDistribucion().get("INSEMINADA")).isEqualTo(1L);
        assertThat(r.getEmbudo().getInseminadasSinDiagnostico()).isEqualTo(1);
        assertThat(r.getAlertasPendientes().getTotal()).isEqualTo(3);
    }

    @Test
    @DisplayName("Con rodeo, solo sus animales, sus alertas pendientes y su tendencia")
    void filtraPorRodeo() {
        miembro();
        when(rodeoLoteRepository.findById(10L)).thenReturn(Optional.of(rodeo1));
        when(animalRepository.findParaDashboardByEstablecimientoIdAndEstadoActualNot(EST, EstadoAnimal.INACTIVO))
                .thenReturn(List.of(animal(1L, rodeo1, true, true), animal(2L, rodeo2, true, true)));
        when(estadoSaludRepository.findUltimoPorAnimal(List.of(1L))).thenReturn(List.of());
        when(alertaService.listarAlertas(EMAIL, EST, null, null, EstadoAlerta.PENDIENTE)).thenReturn(List.of(
                pendiente(50L, 1L, DisparadorAlerta.SALUD, "SALUD"),
                pendiente(51L, 2L, DisparadorAlerta.SALUD, "SALUD")));
        LocalDateTime hace1h = LocalDateTime.now().minusHours(1);
        when(alertaRepository.findGeneradasDesde(eq(EST), any(), any())).thenReturn(List.of(
                new Object[] { 1L, DisparadorAlerta.SALUD, "SALUD", hace1h, EstadoAlerta.PENDIENTE },
                new Object[] { 2L, DisparadorAlerta.SALUD, "SALUD", hace1h, EstadoAlerta.PENDIENTE }));

        CentroMandoResponse r = service.centroMando(EMAIL, EST, 10L);

        assertThat(r.getCobertura().getTotal()).isEqualTo(1);
        assertThat(r.getAlertasPendientes().getTotal()).isEqualTo(1);
        assertThat(r.getCola()).extracting(i -> i.getAlerta().getId()).containsExactly(50L);
        List<Long> salud = r.getTendencia().getDatos().get("SALUD");
        assertThat(salud.get(salud.size() - 1)).isEqualTo(1L);
        verify(alertaRepository, never()).findAllById(any());
    }

    private static UltimoEstadoSalud salud(Long animalId, String estado) {
        return new UltimoEstadoSalud() {
            @Override
            public Long getAnimalId() {
                return animalId;
            }

            @Override
            public String getEstado() {
                return estado;
            }
        };
    }
}
