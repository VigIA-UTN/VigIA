package dev.vigia.backend.service;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.CentroMandoResponse;
import dev.vigia.backend.dto.ResumenEjecutivoResponse;
import dev.vigia.backend.dto.SaludReproduccionResponse;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.Intervencion;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.PerfilReferencia;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.TipoIntervencion;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoSaludRepository;
import dev.vigia.backend.repository.EstadoSaludRepository.UltimoEstadoSalud;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private IntervencionRepository intervencionRepository;
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
                intervencionRepository, establecimientoRepository, rodeoLoteRepository, alertaService,
                6, 20, 3, 35, 8, 24, 90, 283);
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
    @DisplayName("Parto e inseminación salen del último registro que los tiene, aunque el estado actual no los traiga")
    void fechasDelHistorial() {
        miembro();
        LocalDate hoy = LocalDate.now();
        // Parida hace 60 días y después un POSIBLE_CELO automático, sin fechas.
        Animal parida = animal(1L, rodeo1, true, true);
        EstadoReproductivo posibleCelo = estado(TipoReproductivo.POSIBLE_CELO);
        EstadoReproductivo parto = estado(TipoReproductivo.PARIDA);
        parto.setFechaUltParto(hoy.minusDays(60));
        parida.setEstadosReproductivos(new ArrayList<>(List.of(posibleCelo, parto)));
        // Inseminada hace 263 días y después registrada como gestante sin repetir la fecha.
        Animal gestante = animal(2L, rodeo1, true, true);
        EstadoReproductivo diagnostico = estado(TipoReproductivo.GESTANTE);
        EstadoReproductivo inseminacion = estado(TipoReproductivo.INSEMINADA);
        inseminacion.setFechaUltInseminacion(hoy.minusDays(263));
        EstadoReproductivo anterior = estado(TipoReproductivo.INSEMINADA);
        anterior.setFechaUltInseminacion(hoy.minusDays(400));
        gestante.setEstadosReproductivos(new ArrayList<>(List.of(diagnostico, inseminacion, anterior)));
        when(animalRepository.findParaDashboardByEstablecimientoIdAndEstadoActualNot(EST, EstadoAnimal.INACTIVO))
                .thenReturn(List.of(parida, gestante));
        when(estadoSaludRepository.findUltimoPorAnimal(anyCollection())).thenReturn(List.of());
        when(alertaService.listarAlertas(EMAIL, EST, null, null, EstadoAlerta.PENDIENTE)).thenReturn(List.of());
        when(alertaRepository.findGeneradasDesde(eq(EST), any(), any())).thenReturn(List.of());

        SaludReproduccionResponse r = service.saludReproduccion(EMAIL, EST, null, 30);

        assertThat(r.getIndicadores().getVacasAbiertas()).isEqualTo(1);
        assertThat(r.getIndicadores().getDiasAbiertosPromedio()).isBetween(60L, 61L);
        assertThat(r.getEmbudo().getDistribucion().get("POSIBLE_CELO")).isEqualTo(1L);
        // Parto a 283 días de la inseminación más reciente: en 20 días, la tercera semana.
        assertThat(r.getPartos().getGestantesSinFecha()).isZero();
        assertThat(r.getPartos().getSemanas().get(2).getAnimales()).extracting(a -> a.getId()).containsExactly(2L);
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
                new Object[] { 1L, DisparadorAlerta.SALUD, "SALUD", hace1h, EstadoAlerta.PENDIENTE, null },
                new Object[] { 2L, DisparadorAlerta.SALUD, "SALUD", hace1h, EstadoAlerta.PENDIENTE, null }));

        CentroMandoResponse r = service.centroMando(EMAIL, EST, 10L);

        assertThat(r.getCobertura().getTotal()).isEqualTo(1);
        assertThat(r.getAlertasPendientes().getTotal()).isEqualTo(1);
        assertThat(r.getCola()).extracting(i -> i.getAlerta().getId()).containsExactly(50L);
        // Se suman las 8 semanas: en la primera hora del lunes, "hace 1 h" cae en la semana anterior. En qué semana
        // va cada alerta lo prueba CentroMandoCalculadoraTest con una fecha fija.
        List<Long> salud = r.getTendencia().getDatos().get("SALUD");
        assertThat(salud.stream().mapToLong(Long::longValue).sum()).isEqualTo(1L);
        verify(alertaRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Salud: 400 con un período inválido, antes de consultar nada")
    void saludPeriodoInvalido() {
        assertThatThrownBy(() -> service.saludReproduccion(EMAIL, EST, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.saludReproduccion(EMAIL, EST, null, 366))
                .isInstanceOf(IllegalArgumentException.class);
        verify(establecimientoRepository, never()).findActiveEstablecimientoByUsuarioEmail(any());
    }

    @Test
    @DisplayName("Salud: con rodeo, el anestro pendiente se busca en todo el establecimiento y las intervenciones "
            + "solo de sus animales")
    void saludConRodeo() {
        miembro();
        when(rodeoLoteRepository.findById(10L)).thenReturn(Optional.of(rodeo1));
        Animal enRodeo = animal(1L, rodeo1, true, true);
        enRodeo.setEstadosReproductivos(new ArrayList<>(List.of(estado(TipoReproductivo.ANESTRO))));
        when(animalRepository.findParaDashboardByEstablecimientoIdAndEstadoActualNot(EST, EstadoAnimal.INACTIVO))
                .thenReturn(List.of(enRodeo, animal(2L, rodeo2, true, true)));
        when(estadoSaludRepository.findUltimoPorAnimal(List.of(1L))).thenReturn(List.of());
        when(alertaService.listarAlertas(EMAIL, EST, null, null, EstadoAlerta.PENDIENTE)).thenReturn(List.of(
                pendiente(50L, 1L, DisparadorAlerta.CELO, "Anestro Detectado"),
                pendiente(51L, 2L, DisparadorAlerta.SALUD, "SALUD")));
        when(alertaRepository.findGeneradasDesde(eq(EST), any(), any())).thenReturn(List.of());
        Intervencion intervencion = new Intervencion();
        intervencion.setAnimal(enRodeo);
        intervencion.setTipo(TipoIntervencion.TRATAMIENTO);
        intervencion.setFechaHora(LocalDateTime.now().minusDays(2));
        when(intervencionRepository.findUltimaPorAnimal(Set.of(1L))).thenReturn(List.of(intervencion));

        SaludReproduccionResponse r = service.saludReproduccion(EMAIL, EST, 10L, 30);

        assertThat(r.getEmbudo().getAnestroSinIntervencion()).extracting(a -> a.getId()).containsExactly(1L);
        assertThat(r.getAlertasSaludSinAtender()).isEmpty();
        assertThat(r.getUltimaIntervencion().getMasReciente().getTipo()).isEqualTo(TipoIntervencion.TRATAMIENTO);
        assertThat(r.getUmbrales().getDiasGestacion()).isEqualTo(283);
    }

    @Test
    @DisplayName("Resumen: una fila por rodeo activo, en orden, y las alertas desde lo que cubra más entre el "
            + "período y la tendencia")
    void resumenEjecutivo() {
        miembro();
        RodeoLote inactivo = rodeo(5L, establecimiento);
        inactivo.setActivo(false);
        when(rodeoLoteRepository.findByEstablecimientoId(EST)).thenReturn(List.of(rodeo2, inactivo, rodeo1));
        when(animalRepository.findParaDashboardByEstablecimientoIdAndEstadoActualNot(EST, EstadoAnimal.INACTIVO))
                .thenReturn(List.of(animal(1L, rodeo1, true, true)));
        when(estadoSaludRepository.findUltimoPorAnimal(anyCollection())).thenReturn(List.of());
        when(alertaService.listarAlertas(EMAIL, EST, null, null, EstadoAlerta.PENDIENTE)).thenReturn(List.of());
        when(alertaRepository.findGeneradasDesde(eq(EST), any(), any())).thenReturn(List.of());

        ResumenEjecutivoResponse r = service.resumenEjecutivo(EMAIL, EST, 90);

        assertThat(r.getRodeos()).extracting(ResumenEjecutivoResponse.FilaRodeo::getId).containsExactly(10L, 20L);
        assertThat(r.getRodeos().get(0).getAnimales()).isEqualTo(1);
        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(alertaRepository).findGeneradasDesde(eq(EST), desde.capture(), any());
        // 90 días es más que las 8 semanas de la tendencia.
        assertThat(desde.getValue()).isBefore(LocalDateTime.now().minusDays(89));
    }

    private static EstadoReproductivo estado(TipoReproductivo tipo) {
        EstadoReproductivo e = new EstadoReproductivo();
        e.setEstado(tipo);
        return e;
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
