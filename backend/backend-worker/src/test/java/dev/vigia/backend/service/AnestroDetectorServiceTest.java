package dev.vigia.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;

@ExtendWith(MockitoExtension.class)
class AnestroDetectorServiceTest {

    @Mock
    private EstablecimientoRepository establecimientoRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private AlertaRepository alertaRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AnestroDetectorService service;

    private Animal animal;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "silencioDias", 21);

        Establecimiento est = new Establecimiento();
        est.setId(1L);
        est.setDiasDeteccionAnestro(45);
        est.setSeveridadAnestro(SeveridadAlerta.MEDIA);

        animal = new Animal();
        animal.setId(10L);
        animal.setEstablecimiento(est);
        animal.setFechaNacimiento(LocalDate.now().minusYears(5));

        when(establecimientoRepository.findAll()).thenReturn(List.of(est));
        when(animalRepository.findByEstablecimientoIdInAndEstadoActualNot(List.of(1L), EstadoAnimal.INACTIVO))
                .thenReturn(List.of(animal));
        lenient().when(alertaRepository.save(any(Alerta.class))).thenAnswer(i -> i.getArgument(0));
    }

    /** Estado registrado hace {@code diasRegistro} días; parto/inseminación en días atrás o null. */
    private static EstadoReproductivo estado(TipoReproductivo tipo, int diasRegistro, Integer diasParto,
            Integer diasInseminacion) {
        EstadoReproductivo e = new EstadoReproductivo();
        e.setEstado(tipo);
        e.setFechaRegistro(LocalDateTime.now().minusDays(diasRegistro));
        e.setFechaUltParto(diasParto == null ? null : LocalDate.now().minusDays(diasParto));
        e.setFechaUltInseminacion(diasInseminacion == null ? null : LocalDate.now().minusDays(diasInseminacion));
        return e;
    }

    /** Historial como lo entrega JPA: el más reciente primero. */
    private void historial(EstadoReproductivo... estados) {
        animal.setEstadosReproductivos(List.of(estados));
    }

    private void ultimaAlertaAnestro(EstadoAlerta estado, int diasDesdeResolucion) {
        Alerta a = new Alerta();
        a.setAnimal(animal);
        a.setTipo(Alerta.TIPO_ANESTRO);
        a.setEstado(estado);
        a.setTsConfirmacion(estado == EstadoAlerta.PENDIENTE ? null : LocalDateTime.now().minusDays(diasDesdeResolucion));
        when(alertaRepository.findFirstByAnimal_IdAndTipoOrderByTsGeneracionDesc(10L, Alerta.TIPO_ANESTRO))
                .thenReturn(Optional.of(a));
    }

    @Test
    void dispara_CuandoSuperaElUmbralSinCiclo() {
        historial(estado(TipoReproductivo.VACIA, 100, 100, null));

        service.detectarAnestro();

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertEquals(Alerta.TIPO_ANESTRO, captor.getValue().getTipo());
        assertEquals(100.0, captor.getValue().getValorDetectado());

        // El evento que llega por SSE trae el texto con los días, para la tarjeta en vivo
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(anyString(), eq(""), evento.capture());
        assertEquals(captor.getValue().getAccionRecomendada(), ((AlertaEventDTO) evento.getValue()).getAccionRecomendada());
        assertTrue(((AlertaEventDTO) evento.getValue()).getAccionRecomendada().contains("100 días"));
    }

    @Test
    void noDispara_SiSeRegistroCeloAunqueArrastreUnPartoViejo() {
        // Registrar un celo copia el parto anterior: la referencia tiene que ser el celo, no el parto.
        historial(estado(TipoReproductivo.CELO, 10, 200, null));

        service.detectarAnestro();

        verify(alertaRepository, never()).save(any());
    }

    @Test
    void noDispara_SiHuboInseminacionReciente_AunqueElUltimoEstadoSeaVacia() {
        historial(
                estado(TipoReproductivo.VACIA, 0, 200, null),
                estado(TipoReproductivo.INSEMINADA, 10, 200, 10));

        service.detectarAnestro();

        verify(alertaRepository, never()).save(any());
    }

    @Test
    void noDispara_SiSeDescartoHaceMenosDelSilencio() {
        historial(estado(TipoReproductivo.VACIA, 100, 100, null));
        ultimaAlertaAnestro(EstadoAlerta.DESCARTADA, 10);

        service.detectarAnestro();

        verify(alertaRepository, never()).save(any());
    }

    @Test
    void dispara_SiSeDescartoHaceMasDelSilencio() {
        historial(estado(TipoReproductivo.VACIA, 100, 100, null));
        ultimaAlertaAnestro(EstadoAlerta.DESCARTADA, 30);

        service.detectarAnestro();

        verify(alertaRepository).save(any(Alerta.class));
    }

    @Test
    void archivaLaPendiente_SiSeRegistroUnCeloDespues() {
        historial(estado(TipoReproductivo.CELO, 5, 200, null));
        ultimaAlertaAnestro(EstadoAlerta.PENDIENTE, 0);

        service.detectarAnestro();

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertEquals(EstadoAlerta.ARCHIVADA, captor.getValue().getEstado());
        assertEquals("ciclo_registrado", captor.getValue().getMotivoDescarte());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void dispara_SiLaAnteriorFueArchivada_ElArchivadoNoSilencia() {
        historial(estado(TipoReproductivo.VACIA, 100, 100, null));
        ultimaAlertaAnestro(EstadoAlerta.ARCHIVADA, 2);

        service.detectarAnestro();

        verify(alertaRepository).save(any(Alerta.class));
    }

    @Test
    void noDispara_SiYaHayUnaAlertaPendiente() {
        historial(estado(TipoReproductivo.VACIA, 100, 100, null));
        ultimaAlertaAnestro(EstadoAlerta.PENDIENTE, 0);

        service.detectarAnestro();

        verify(alertaRepository, never()).save(any());
    }
}
