package dev.vigia.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.vigia.backend.mensajeria.PublicadorDeAlertas;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

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
    private PublicadorDeAlertas publicadorDeAlertas;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private AnestroDetectorService service;

    private Animal animal;

    @BeforeEach
    @SuppressWarnings("null")
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
        when(animalRepository.findConEstadosByEstablecimientoIdAndEstadoActualNot(1L, EstadoAnimal.INACTIVO))
                .thenReturn(List.of(animal));
        lenient().when(alertaRepository.save(any(Alerta.class))).thenAnswer(i -> i.getArgument(0));
    }

    /** Estado registrado hace {@code diasRegistro} días; parto/inseminación en días atrás o null. */
    @SuppressWarnings("null")
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

    @SuppressWarnings("null")
    private static Alerta alertaAnestro(Animal deAnimal, EstadoAlerta estado, int diasDesdeResolucion) {
        Alerta a = new Alerta();
        a.setAnimal(deAnimal);
        a.setTipo(Alerta.TIPO_ANESTRO);
        a.setEstado(estado);
        a.setTsConfirmacion(estado == EstadoAlerta.PENDIENTE ? null : LocalDateTime.now().minusDays(diasDesdeResolucion));
        return a;
    }

    @SuppressWarnings("null")
    private void ultimaAlertaAnestro(EstadoAlerta estado, int diasDesdeResolucion) {
        when(alertaRepository.findByAnimal_Establecimiento_IdAndTipoOrderByTsGeneracionDesc(1L, Alerta.TIPO_ANESTRO))
                .thenReturn(List.of(alertaAnestro(animal, estado, diasDesdeResolucion)));
    }

    @SuppressWarnings("null")
    private static Animal vacaDe(Establecimiento est, long id, EstadoReproductivo... estados) {
        Animal a = new Animal();
        a.setId(id);
        a.setEstablecimiento(est);
        a.setFechaNacimiento(LocalDate.now().minusYears(5));
        a.setEstadosReproductivos(List.of(estados));
        return a;
    }

    @Test
    @SuppressWarnings("null")
    void dispara_CuandoSuperaElUmbralSinCiclo() {
        historial(estado(TipoReproductivo.VACIA, 100, 100, null));

        service.detectarAnestro();

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertEquals(Alerta.TIPO_ANESTRO, captor.getValue().getTipo());
        assertEquals(100.0, captor.getValue().getValorDetectado());

        // El evento que llega por SSE trae el texto con los días, para la tarjeta en vivo
        ArgumentCaptor<dev.vigia.backend.dto.ml.AlertaEventDTO> evento = ArgumentCaptor.forClass(dev.vigia.backend.dto.ml.AlertaEventDTO.class);
        verify(publicadorDeAlertas).publicar(evento.capture());
        assertEquals(captor.getValue().getAccionRecomendada(), ((AlertaEventDTO) evento.getValue()).getAccionRecomendada());
        assertTrue(((AlertaEventDTO) evento.getValue()).getAccionRecomendada().contains("100 días"));
    }

    @Test
    @SuppressWarnings("null")
    void noDispara_SiSeRegistroCeloAunqueArrastreUnPartoViejo() {
        // Registrar un celo copia el parto anterior: la referencia tiene que ser el celo, no el parto.
        historial(estado(TipoReproductivo.CELO, 10, 200, null));

        service.detectarAnestro();

        verify(alertaRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void noDispara_SiElModeloDetectoUnCeloReciente() {
        // El celo detectado por el ML (POSIBLE_CELO) también cuenta como actividad de ciclo
        historial(estado(TipoReproductivo.POSIBLE_CELO, 10, 200, null));

        service.detectarAnestro();

        verify(alertaRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void archivaLaPendiente_SiElModeloDetectoUnCeloDespues() {
        historial(estado(TipoReproductivo.POSIBLE_CELO, 5, 200, null));
        ultimaAlertaAnestro(EstadoAlerta.PENDIENTE, 0);

        service.detectarAnestro();

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertEquals(EstadoAlerta.ARCHIVADA, captor.getValue().getEstado());
        assertEquals("ciclo_registrado", captor.getValue().getMotivoDescarte());
    }

    @Test
    @SuppressWarnings("null")
    void noDispara_SiHuboInseminacionReciente_AunqueElUltimoEstadoSeaVacia() {
        historial(
                estado(TipoReproductivo.VACIA, 0, 200, null),
                estado(TipoReproductivo.INSEMINADA, 10, 200, 10));

        service.detectarAnestro();

        verify(alertaRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void noDispara_SiSeDescartoHaceMenosDelSilencio() {
        historial(estado(TipoReproductivo.VACIA, 100, 100, null));
        ultimaAlertaAnestro(EstadoAlerta.DESCARTADA, 10);

        service.detectarAnestro();

        verify(alertaRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void dispara_SiSeDescartoHaceMasDelSilencio() {
        historial(estado(TipoReproductivo.VACIA, 100, 100, null));
        ultimaAlertaAnestro(EstadoAlerta.DESCARTADA, 30);

        service.detectarAnestro();

        verify(alertaRepository).save(any(Alerta.class));
    }

    @Test
    @SuppressWarnings("null")
    void archivaLaPendiente_SiSeRegistroUnCeloDespues() {
        historial(estado(TipoReproductivo.CELO, 5, 200, null));
        ultimaAlertaAnestro(EstadoAlerta.PENDIENTE, 0);

        service.detectarAnestro();

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertEquals(EstadoAlerta.ARCHIVADA, captor.getValue().getEstado());
        assertEquals("ciclo_registrado", captor.getValue().getMotivoDescarte());
        verify(publicadorDeAlertas).publicar(any());
    }

    @Test
    @SuppressWarnings("null")
    void dispara_SiLaAnteriorFueArchivada_ElArchivadoNoSilencia() {
        historial(estado(TipoReproductivo.VACIA, 100, 100, null));
        ultimaAlertaAnestro(EstadoAlerta.ARCHIVADA, 2);

        service.detectarAnestro();

        verify(alertaRepository).save(any(Alerta.class));
    }

    @Test
    @SuppressWarnings("null")
    void noDispara_SiYaHayUnaAlertaPendiente() {
        historial(estado(TipoReproductivo.VACIA, 100, 100, null));
        ultimaAlertaAnestro(EstadoAlerta.PENDIENTE, 0);

        service.detectarAnestro();

        verify(alertaRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void unEstablecimientoQueFalla_noFrenaALosDemas_yCadaUnoTieneSuTransaccion() {
        // Con 25.000 animales en una sola transacción la corrida no terminaba (OutOfMemoryError tras 12 min)
        // y cualquier error deshacía todo: ahora cada establecimiento confirma o se deshace por su cuenta.
        Establecimiento otro = new Establecimiento();
        otro.setId(2L);
        otro.setDiasDeteccionAnestro(45);
        otro.setSeveridadAnestro(SeveridadAlerta.MEDIA);
        Animal delOtro = vacaDe(otro, 20L, estado(TipoReproductivo.VACIA, 100, 100, null));
        when(establecimientoRepository.findAll()).thenReturn(List.of(animal.getEstablecimiento(), otro));
        when(animalRepository.findConEstadosByEstablecimientoIdAndEstadoActualNot(1L, EstadoAnimal.INACTIVO))
                .thenThrow(new IllegalStateException("falló la consulta"));
        when(animalRepository.findConEstadosByEstablecimientoIdAndEstadoActualNot(2L, EstadoAnimal.INACTIVO))
                .thenReturn(List.of(delOtro));

        service.detectarAnestro();

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(captor.capture());
        assertEquals(20L, captor.getValue().getAnimal().getId());
        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager).rollback(any());
        verify(transactionManager).commit(any());
    }

    @Test
    @SuppressWarnings("null")
    void buscaLasAlertasDeAnestroUnaVezPorEstablecimiento_noUnaPorAnimal() {
        historial(estado(TipoReproductivo.CELO, 10, null, null));
        Animal otra = vacaDe(animal.getEstablecimiento(), 11L, estado(TipoReproductivo.CELO, 10, null, null));
        when(animalRepository.findConEstadosByEstablecimientoIdAndEstadoActualNot(1L, EstadoAnimal.INACTIVO))
                .thenReturn(List.of(animal, otra));

        service.detectarAnestro();

        verify(alertaRepository, times(1))
                .findByAnimal_Establecimiento_IdAndTipoOrderByTsGeneracionDesc(1L, Alerta.TIPO_ANESTRO);
    }

    @Test
    @SuppressWarnings("null")
    void usaLaAlertaMasNuevaDeCadaAnimal_DeLasDelEstablecimiento() {
        historial(estado(TipoReproductivo.VACIA, 100, 100, null));
        Animal otra = vacaDe(animal.getEstablecimiento(), 11L);
        // Como las devuelve el repositorio: de todo el establecimiento, la más nueva primero.
        when(alertaRepository.findByAnimal_Establecimiento_IdAndTipoOrderByTsGeneracionDesc(1L, Alerta.TIPO_ANESTRO))
                .thenReturn(List.of(
                        alertaAnestro(otra, EstadoAlerta.PENDIENTE, 0),
                        alertaAnestro(animal, EstadoAlerta.DESCARTADA, 30),
                        alertaAnestro(animal, EstadoAlerta.PENDIENTE, 0)));

        service.detectarAnestro();

        // La más nueva de este animal se descartó hace 30 días, fuera del silencio: vuelve a alertar. No la
        // silencian ni la pendiente de otro animal ni una pendiente más vieja del mismo.
        verify(alertaRepository).save(any(Alerta.class));
    }
}
