package dev.vigia.backend.config;

import dev.vigia.backend.dto.AnimalAisladoResponse;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CohesionBatchConfig — Tests Unitarios")
class CohesionBatchConfigTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private EstablecimientoRepository establecimientoRepository;

    @Mock
    private TelemetriaRepository telemetriaRepository;

    @Mock
    private AlertaRepository alertaRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RegistroCohesionRepository registroCohesionRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AlertaAnimalAisladoRepository alertaAnimalAisladoRepository;

    private CohesionBatchConfig batchConfig;

    @BeforeEach
    void setUp() {
        batchConfig = new CohesionBatchConfig(
                jobRepository,
                transactionManager,
                establecimientoRepository,
                telemetriaRepository,
                alertaRepository,
                rabbitTemplate,
                registroCohesionRepository,
                animalRepository,
                alertaAnimalAisladoRepository
        );
        ReflectionTestUtils.setField(batchConfig, "restTemplate", restTemplate);
    }

    @Test
    @DisplayName("Sin telemetría en las últimas 24h, no llama a ml-geo ni persiste registros")
    void sinTelemetria_noProcesa() throws Exception {
        Establecimiento est = new Establecimiento();
        est.setId(1L);

        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(Collections.emptyList());

        ItemProcessor<Establecimiento, Establecimiento> processor = batchConfig.cohesionProcessor();
        Establecimiento result = processor.process(est);

        assertThat(result).isSameAs(est);
        verifyNoInteractions(restTemplate);
        verifyNoInteractions(registroCohesionRepository);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Con telemetría y respuesta ml-geo exitosa, guarda RegistroCohesion por cada animal")
    void conTelemetria_guardaRegistroCohesionPorAnimal() throws Exception {
        Establecimiento est = new Establecimiento();
        est.setId(1L);
        est.setUmbralAislamientoM(120.0);

        Animal a1 = new Animal();
        a1.setId(10L);
        Caravana c1 = new Caravana();
        c1.setAnimal(a1);

        Telemetria t1 = new Telemetria();
        t1.setCaravana(c1);
        t1.setLatitud(-34.60);
        t1.setLongitud(-58.38);
        t1.setFechaHora(LocalDateTime.now().minusHours(1));

        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(t1));

        CohesionBatchConfig.GeoAlerta ga = new CohesionBatchConfig.GeoAlerta();
        ga.setAnimalId("10");
        ga.setIndiceCohesion(0.85);
        ga.setDistanciaCentroideM(35.0);
        ga.setAlertaAislamiento(false);
        ga.setTiempoFueraGrupoS(0.0);

        CohesionBatchConfig.GeoResponse geoResp = new CohesionBatchConfig.GeoResponse();
        geoResp.setAlertas(List.of(ga));

        when(restTemplate.postForEntity(anyString(), any(), eq(CohesionBatchConfig.GeoResponse.class)))
                .thenReturn(new ResponseEntity<>(geoResp, HttpStatus.OK));

        ItemProcessor<Establecimiento, Establecimiento> processor = batchConfig.cohesionProcessor();
        processor.process(est);

        ArgumentCaptor<RegistroCohesion> captor = ArgumentCaptor.forClass(RegistroCohesion.class);
        verify(registroCohesionRepository).save(captor.capture());

        RegistroCohesion saved = captor.getValue();
        assertThat(saved.getAnimal().getId()).isEqualTo(10L);
        assertThat(saved.getEstablecimiento().getId()).isEqualTo(1L);
        assertThat(saved.getIndiceCohesion()).isEqualTo(0.85);
        assertThat(saved.getDistanciaCentroideM()).isEqualTo(35.0);
        assertThat(saved.getTiempoFueraGrupoS()).isEqualTo(0L);
        assertThat(saved.getFecha()).isNotNull();

        // Como alertaAislamiento es false, no debe generar alerta
        verifyNoInteractions(alertaRepository);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Envía un request a ml-geo por rodeo (y uno para los animales sin rodeo), cada uno con el umbral del establecimiento")
    void unRequestPorRodeo() throws Exception {
        Establecimiento est = new Establecimiento();
        est.setId(1L);
        est.setUmbralAislamientoM(150.0);
        RodeoLote r1 = rodeo(100L);
        RodeoLote r2 = rodeo(200L);

        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(
                        telemetria(10L, r1), telemetria(11L, r1),
                        telemetria(20L, r2),
                        telemetria(30L, null)));
        when(restTemplate.postForEntity(anyString(), any(), eq(CohesionBatchConfig.GeoResponse.class)))
                .thenReturn(new ResponseEntity<>(new CohesionBatchConfig.GeoResponse(), HttpStatus.OK));

        batchConfig.cohesionProcessor().process(est);

        ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate, times(3)).postForEntity(anyString(), requests.capture(),
                eq(CohesionBatchConfig.GeoResponse.class));
        assertThat(requests.getAllValues())
                .extracting(CohesionBatchConfigTest::animalIdsDe)
                .containsExactlyInAnyOrder(List.of("10", "11"), List.of("20"), List.of("30"));
        assertThat(requests.getAllValues())
                .allSatisfy(r -> assertThat(((Map<?, ?>) r).get("umbral_aislamiento_m")).isEqualTo(150.0));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Si ml-geo falla para un rodeo, los demás rodeos se procesan igual")
    void falloEnUnRodeoNoFrenaALosDemas() throws Exception {
        Establecimiento est = new Establecimiento();
        est.setId(1L);

        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(telemetria(10L, rodeo(100L)), telemetria(20L, rodeo(200L))));
        when(restTemplate.postForEntity(anyString(), any(), eq(CohesionBatchConfig.GeoResponse.class)))
                .thenAnswer(inv -> {
                    List<String> ids = animalIdsDe(inv.getArgument(1));
                    if (ids.contains("10")) {
                        throw new RestClientException("ml-geo no disponible");
                    }
                    CohesionBatchConfig.GeoAlerta ga = new CohesionBatchConfig.GeoAlerta();
                    ga.setAnimalId(ids.get(0));
                    ga.setIndiceCohesion(0.9);
                    ga.setAlertaAislamiento(false);
                    ga.setTiempoFueraGrupoS(0.0);
                    CohesionBatchConfig.GeoResponse resp = new CohesionBatchConfig.GeoResponse();
                    resp.setAlertas(List.of(ga));
                    return new ResponseEntity<>(resp, HttpStatus.OK);
                });

        batchConfig.cohesionProcessor().process(est);

        ArgumentCaptor<RegistroCohesion> captor = ArgumentCaptor.forClass(RegistroCohesion.class);
        verify(registroCohesionRepository).save(captor.capture());
        assertThat(captor.getValue().getAnimal().getId()).isEqualTo(20L);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Animales aislados 15+ min sin episodio abierto: una alerta AISLAMIENTO_SOCIAL con un episodio por animal")
    void aislamientoNuevo_creaUnaAlertaConLosAnimales() throws Exception {
        Establecimiento est = establecimiento(150.0);
        RodeoLote r1 = rodeo(100L);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(telemetria(10L, r1), telemetria(11L, r1), telemetria(12L, r1)));
        mlGeoResponde(
                geo("10", true, 400.0, 1800.0, 1200.0),
                geo("11", true, 200.0, 2400.0, 1200.0),
                geo("12", false, 20.0, 0.0, 0.0));
        alertaRepositoryAsignaIds();

        batchConfig.cohesionProcessor().process(est);

        ArgumentCaptor<Alerta> alerta = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(alerta.capture());
        assertThat(alerta.getValue().getDisparador()).isEqualTo(DisparadorAlerta.AISLAMIENTO_SOCIAL);
        assertThat(alerta.getValue().getEstado()).isEqualTo(EstadoAlerta.PENDIENTE);
        assertThat(alerta.getValue().getValorUmbral()).isEqualTo(150.0);
        assertThat(alerta.getValue().getAnimal().getId()).isEqualTo(11L); // el que más tiempo lleva fuera
        assertThat(alerta.getValue().getValorDetectado()).isEqualTo(400.0);
        assertThat(alerta.getValue().getSeveridad()).isEqualTo(SeveridadAlerta.ALTA); // 400 m > 2 × 150 m

        ArgumentCaptor<AlertaAnimalAislado> episodios = ArgumentCaptor.forClass(AlertaAnimalAislado.class);
        verify(alertaAnimalAisladoRepository, times(2)).save(episodios.capture());
        assertThat(episodios.getAllValues())
                .extracting(e -> e.getAnimal().getId(), AlertaAnimalAislado::getSegundosFuera,
                        AlertaAnimalAislado::getDistanciaGrupoM)
                .containsExactlyInAnyOrder(tuple(10L, 1800L, 400.0), tuple(11L, 2400L, 200.0));
        assertThat(episodios.getAllValues()).allSatisfy(e -> {
            assertThat(e.getAlerta()).isSameAs(alerta.getValue());
            // El aislamiento empezó cuando se alejó (hace 1200 s seguidos), no cuando se abrió la alerta
            assertThat(e.getTsInicio()).isBetween(
                    LocalDateTime.now().minusSeconds(1205), LocalDateTime.now().minusSeconds(1195));
            assertThat(e.getTsRegreso()).isNull();
        });

        ArgumentCaptor<AlertaEventDTO> evento = ArgumentCaptor.forClass(AlertaEventDTO.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.ALERTAS_EXCHANGE), eq(""), evento.capture());
        assertThat(evento.getValue().getAnimalesAislados())
                .extracting(AnimalAisladoResponse::getAnimalId, AnimalAisladoResponse::getMinutosFuera,
                        AnimalAisladoResponse::getSeveridad)
                .containsExactly(tuple(11L, 40L, "MEDIA"), tuple(10L, 30L, "ALTA"));
    }

    @Test
    @DisplayName("Un animal alejado hace menos de 15 minutos seguidos todavía no genera alerta")
    void menosDeQuinceMinutosSeguidos_noAlerta() throws Exception {
        Establecimiento est = establecimiento(150.0);
        RodeoLote r1 = rodeo(100L);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(telemetria(10L, r1), telemetria(11L, r1)));
        mlGeoResponde(
                geo("10", true, 400.0, 3600.0, 600.0), // mucho tiempo acumulado, pero solo 10 min seguidos
                geo("11", false, 20.0, 0.0, 0.0));

        batchConfig.cohesionProcessor().process(est);

        verify(alertaRepository, never()).save(any());
        verify(alertaAnimalAisladoRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Aislamientos en dos rodeos generan una alerta por rodeo")
    void aislamientosEnDosRodeos_creanUnaAlertaPorRodeo() throws Exception {
        Establecimiento est = establecimiento(150.0);
        RodeoLote r1 = rodeo(100L);
        RodeoLote r2 = rodeo(200L);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(telemetria(10L, r1), telemetria(11L, r1), telemetria(20L, r2), telemetria(21L, r2)));
        mlGeoResponde(
                geo("10", true, 400.0, 1800.0, 1200.0),
                geo("11", false, 20.0, 0.0, 0.0),
                geo("20", true, 200.0, 900.0, 900.0),
                geo("21", false, 20.0, 0.0, 0.0));
        alertaRepositoryAsignaIds();

        batchConfig.cohesionProcessor().process(est);

        ArgumentCaptor<Alerta> alertas = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository, times(2)).save(alertas.capture());
        assertThat(alertas.getAllValues()).extracting(a -> a.getAnimal().getId()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Sin umbral configurado se usa 100 m, en el request a ml-geo y en la alerta")
    void sinUmbralConfigurado_usa100() throws Exception {
        Establecimiento est = establecimiento(null);
        RodeoLote r1 = rodeo(100L);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(telemetria(10L, r1), telemetria(11L, r1)));
        mlGeoResponde(
                geo("10", true, 150.0, 900.0, 900.0),
                geo("11", false, 20.0, 0.0, 0.0));
        alertaRepositoryAsignaIds();

        batchConfig.cohesionProcessor().process(est);

        ArgumentCaptor<Object> request = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(anyString(), request.capture(), eq(CohesionBatchConfig.GeoResponse.class));
        assertThat(((Map<?, ?>) request.getValue()).get("umbral_aislamiento_m")).isEqualTo(100.0);

        ArgumentCaptor<Alerta> alerta = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(alerta.capture());
        assertThat(alerta.getValue().getValorUmbral()).isEqualTo(100.0);
        assertThat(alerta.getValue().getSeveridad()).isEqualTo(SeveridadAlerta.MEDIA); // 150 m <= 2 × 100 m
    }

    @Test
    @DisplayName("Un animal con su aislamiento ya abierto no genera otra alerta")
    void animalConEpisodioAbierto_noCreaOtraAlerta() throws Exception {
        Establecimiento est = establecimiento(150.0);
        RodeoLote r1 = rodeo(100L);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(telemetria(10L, r1), telemetria(11L, r1)));
        mlGeoResponde(
                geo("10", true, 400.0, 1800.0, 1200.0),
                geo("11", false, 20.0, 0.0, 0.0));
        when(alertaAnimalAisladoRepository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(1L))
                .thenReturn(List.of(episodioAbierto(10L, 900L)));

        batchConfig.cohesionProcessor().process(est);

        verify(alertaRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Alerta ya confirmada y el animal sigue aislado: se refresca su episodio, sin alerta nueva")
    void alertaConfirmada_animalSigueAislado_refrescaSinDuplicar() throws Exception {
        Establecimiento est = establecimiento(150.0);
        RodeoLote r1 = rodeo(100L);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(telemetria(10L, r1), telemetria(11L, r1)));
        mlGeoResponde(
                geo("10", true, 420.0, 2400.0, 1800.0),
                geo("11", false, 20.0, 0.0, 0.0));
        Alerta confirmada = alerta(900L, 10L, r1, EstadoAlerta.CONFIRMADA);
        AlertaAnimalAislado episodio = episodio(confirmada, 10L, null);
        when(alertaAnimalAisladoRepository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(1L))
                .thenReturn(List.of(episodio));

        batchConfig.cohesionProcessor().process(est);

        verify(alertaRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
        verify(alertaAnimalAisladoRepository).save(episodio);
        assertThat(episodio.getSegundosFuera()).isEqualTo(2400L);
        assertThat(episodio.getDistanciaGrupoM()).isEqualTo(420.0);
        assertThat(episodio.getTsRegreso()).isNull();
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Si el animal vuelve al grupo se cierra su episodio; si después se vuelve a alejar, alerta nueva")
    void animalVuelveYSeVuelveAAlejar_cierraYDespuesAlertaDeNuevo() throws Exception {
        Establecimiento est = establecimiento(150.0);
        RodeoLote r1 = rodeo(100L);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(telemetria(10L, r1), telemetria(11L, r1)));
        when(restTemplate.postForEntity(anyString(), any(), eq(CohesionBatchConfig.GeoResponse.class)))
                .thenReturn(
                        respuesta(geo("10", false, 30.0, 1800.0, 0.0), geo("11", false, 20.0, 0.0, 0.0)),
                        respuesta(geo("10", true, 400.0, 2700.0, 900.0), geo("11", false, 20.0, 0.0, 0.0)));
        Alerta pendiente = alerta(900L, 10L, r1, EstadoAlerta.PENDIENTE);
        AlertaAnimalAislado episodio = episodio(pendiente, 10L, null);
        when(alertaAnimalAisladoRepository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(1L))
                .thenReturn(List.of(episodio), List.of());
        alertaRepositoryAsignaIds();

        batchConfig.cohesionProcessor().process(est);

        verify(alertaAnimalAisladoRepository).save(episodio);
        assertThat(episodio.getTsRegreso()).isNotNull();
        assertThat(pendiente.getEstado()).isEqualTo(EstadoAlerta.PENDIENTE); // sin auto-resolver
        verify(alertaRepository, never()).save(any());

        pendiente.setEstado(EstadoAlerta.DESCARTADA);
        batchConfig.cohesionProcessor().process(est);

        ArgumentCaptor<Alerta> nueva = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(nueva.capture());
        assertThat(nueva.getValue().getId()).isNotEqualTo(900L);
        assertThat(nueva.getValue().getAnimal().getId()).isEqualTo(10L);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Un animal nuevo que se aleja con una alerta pendiente del rodeo se suma a esa alerta")
    void nuevoAislamientoConAlertaPendienteDelRodeo_seSumaALaMismaAlerta() throws Exception {
        Establecimiento est = establecimiento(150.0);
        RodeoLote r1 = rodeo(100L);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(telemetria(10L, r1), telemetria(11L, r1), telemetria(12L, r1)));
        mlGeoResponde(
                geo("10", true, 200.0, 1200.0, 1200.0),
                geo("11", true, 350.0, 3000.0, 900.0),
                geo("12", false, 20.0, 0.0, 0.0));
        Alerta pendiente = alerta(900L, 10L, r1, EstadoAlerta.PENDIENTE);
        AlertaAnimalAislado episodio10 = episodio(pendiente, 10L, null);
        when(alertaAnimalAisladoRepository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(1L))
                .thenReturn(List.of(episodio10));
        when(alertaRepository.findByFiltros(1L, EstadoAlerta.PENDIENTE, Set.of(DisparadorAlerta.AISLAMIENTO_SOCIAL), null))
                .thenReturn(List.of(pendiente));
        when(alertaAnimalAisladoRepository.findByAlerta_IdOrderBySegundosFueraDesc(900L))
                .thenReturn(List.of(episodio10));
        alertaRepositoryAsignaIds();

        batchConfig.cohesionProcessor().process(est);

        ArgumentCaptor<Alerta> guardada = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(guardada.capture());
        assertThat(guardada.getValue()).isSameAs(pendiente);
        assertThat(pendiente.getAnimal().getId()).isEqualTo(11L); // ahora es el que más tiempo lleva fuera
        assertThat(pendiente.getValorDetectado()).isEqualTo(350.0);
        assertThat(pendiente.getSeveridad()).isEqualTo(SeveridadAlerta.ALTA);

        ArgumentCaptor<AlertaAnimalAislado> episodios = ArgumentCaptor.forClass(AlertaAnimalAislado.class);
        verify(alertaAnimalAisladoRepository, times(2)).save(episodios.capture());
        assertThat(episodios.getAllValues())
                .filteredOn(e -> e.getAnimal().getId().equals(11L))
                .singleElement()
                .satisfies(e -> assertThat(e.getAlerta()).isSameAs(pendiente));

        ArgumentCaptor<AlertaEventDTO> evento = ArgumentCaptor.forClass(AlertaEventDTO.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.ALERTAS_EXCHANGE), eq(""), evento.capture());
        assertThat(evento.getValue().getId()).isEqualTo(900L);
        assertThat(evento.getValue().getAnimalesAislados())
                .extracting(AnimalAisladoResponse::getAnimalId)
                .containsExactly(11L, 10L); // el que se sumó y el que ya estaba, más tiempo fuera primero
    }

    @Test
    @DisplayName("Si el animal vuelve a alejarse con la alerta todavía pendiente, se reabre su fila en esa alerta")
    void reaislamientoConAlertaPendiente_reabreLaFilaDelAnimal() throws Exception {
        Establecimiento est = establecimiento(150.0);
        RodeoLote r1 = rodeo(100L);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(telemetria(10L, r1), telemetria(11L, r1)));
        mlGeoResponde(
                geo("10", true, 400.0, 2700.0, 900.0),
                geo("11", false, 20.0, 0.0, 0.0));
        Alerta pendiente = alerta(900L, 10L, r1, EstadoAlerta.PENDIENTE);
        AlertaAnimalAislado cerrado = episodio(pendiente, 10L, LocalDateTime.now().minusMinutes(30));
        when(alertaRepository.findByFiltros(1L, EstadoAlerta.PENDIENTE, Set.of(DisparadorAlerta.AISLAMIENTO_SOCIAL), null))
                .thenReturn(List.of(pendiente));
        when(alertaAnimalAisladoRepository.findByAlerta_IdOrderBySegundosFueraDesc(900L))
                .thenReturn(List.of(cerrado));
        alertaRepositoryAsignaIds();

        batchConfig.cohesionProcessor().process(est);

        verify(alertaAnimalAisladoRepository).save(cerrado);
        assertThat(cerrado.getTsRegreso()).isNull();
        assertThat(cerrado.getSegundosFuera()).isEqualTo(2700L);
        assertThat(cerrado.getTsInicio()).isBetween( // se alejó de nuevo hace 900 s seguidos
                LocalDateTime.now().minusSeconds(905), LocalDateTime.now().minusSeconds(895));
        verify(alertaRepository).save(pendiente);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Una alerta pendiente de otro rodeo no absorbe el aislamiento: se crea una alerta nueva")
    void alertaPendienteDeOtroRodeo_creaAlertaNueva() throws Exception {
        Establecimiento est = establecimiento(150.0);
        RodeoLote r1 = rodeo(100L);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(telemetria(10L, r1), telemetria(11L, r1)));
        mlGeoResponde(
                geo("10", true, 400.0, 1800.0, 1200.0),
                geo("11", false, 20.0, 0.0, 0.0));
        when(alertaRepository.findByFiltros(1L, EstadoAlerta.PENDIENTE, Set.of(DisparadorAlerta.AISLAMIENTO_SOCIAL), null))
                .thenReturn(List.of(alerta(900L, 20L, rodeo(200L), EstadoAlerta.PENDIENTE)));
        alertaRepositoryAsignaIds();

        batchConfig.cohesionProcessor().process(est);

        ArgumentCaptor<Alerta> guardada = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(guardada.capture());
        assertThat(guardada.getValue().getId()).isNotEqualTo(900L);
        assertThat(guardada.getValue().getAnimal().getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Un animal sin datos en esta corrida conserva su episodio abierto")
    void animalSinDatos_conservaSuEpisodioAbierto() throws Exception {
        Establecimiento est = establecimiento(150.0);
        RodeoLote r1 = rodeo(100L);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(telemetria(10L, r1), telemetria(11L, r1)));
        mlGeoResponde(
                geo("10", false, 20.0, 0.0, 0.0),
                geo("11", false, 20.0, 0.0, 0.0));
        AlertaAnimalAislado sinDatos = episodio(alerta(900L, 30L, r1, EstadoAlerta.PENDIENTE), 30L, null);
        when(alertaAnimalAisladoRepository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(1L))
                .thenReturn(List.of(sinDatos));

        batchConfig.cohesionProcessor().process(est);

        verify(alertaAnimalAisladoRepository, never()).save(any());
        assertThat(sinDatos.getTsRegreso()).isNull();
    }

    private static Alerta alerta(Long id, Long animalId, RodeoLote rodeo, EstadoAlerta estado) {
        Animal animal = new Animal();
        animal.setId(animalId);
        animal.setRodeoLote(rodeo);
        Alerta alerta = new Alerta();
        alerta.setId(id);
        alerta.setAnimal(animal);
        alerta.setDisparador(DisparadorAlerta.AISLAMIENTO_SOCIAL);
        alerta.setEstado(estado);
        alerta.setValorUmbral(150.0);
        return alerta;
    }

    private static AlertaAnimalAislado episodio(Alerta alerta, Long animalId, LocalDateTime tsRegreso) {
        Animal animal = new Animal();
        animal.setId(animalId);
        return AlertaAnimalAislado.builder()
                .alerta(alerta)
                .animal(animal)
                .segundosFuera(1200L)
                .distanciaGrupoM(300.0)
                .tsInicio(LocalDateTime.now().minusMinutes(40))
                .tsRegreso(tsRegreso)
                .build();
    }

    private static ResponseEntity<CohesionBatchConfig.GeoResponse> respuesta(CohesionBatchConfig.GeoAlerta... alertas) {
        CohesionBatchConfig.GeoResponse resp = new CohesionBatchConfig.GeoResponse();
        resp.setAlertas(List.of(alertas));
        return new ResponseEntity<>(resp, HttpStatus.OK);
    }

    private static Establecimiento establecimiento(Double umbral) {
        Establecimiento est = new Establecimiento();
        est.setId(1L);
        est.setUmbralAislamientoM(umbral);
        return est;
    }

    private static CohesionBatchConfig.GeoAlerta geo(String animalId, boolean alejado, double distanciaM,
            double acumuladoS, double continuoS) {
        CohesionBatchConfig.GeoAlerta ga = new CohesionBatchConfig.GeoAlerta();
        ga.setAnimalId(animalId);
        ga.setIndiceCohesion(alejado ? 0.2 : 0.9);
        ga.setAlertaAislamiento(alejado);
        ga.setDistanciaCentroideM(distanciaM);
        ga.setTiempoFueraGrupoS(acumuladoS);
        ga.setTiempoFueraContinuoS(continuoS);
        return ga;
    }

    /** ml-geo responde, para cada request, solo los animales que vinieron en ese request (un rodeo). */
    private void mlGeoResponde(CohesionBatchConfig.GeoAlerta... alertas) {
        when(restTemplate.postForEntity(anyString(), any(), eq(CohesionBatchConfig.GeoResponse.class)))
                .thenAnswer(inv -> {
                    List<String> ids = animalIdsDe(inv.getArgument(1));
                    CohesionBatchConfig.GeoResponse resp = new CohesionBatchConfig.GeoResponse();
                    resp.setAlertas(Arrays.stream(alertas).filter(a -> ids.contains(a.getAnimalId())).toList());
                    return new ResponseEntity<>(resp, HttpStatus.OK);
                });
    }

    private void alertaRepositoryAsignaIds() {
        AtomicLong ids = new AtomicLong(500L);
        when(alertaRepository.save(any(Alerta.class))).thenAnswer(inv -> {
            Alerta a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(ids.getAndIncrement());
            }
            return a;
        });
    }

    private static AlertaAnimalAislado episodioAbierto(Long animalId, Long alertaId) {
        Animal animal = new Animal();
        animal.setId(animalId);
        Alerta alerta = new Alerta();
        alerta.setId(alertaId);
        return AlertaAnimalAislado.builder()
                .alerta(alerta)
                .animal(animal)
                .segundosFuera(600L)
                .distanciaGrupoM(300.0)
                .tsInicio(LocalDateTime.now().minusMinutes(20))
                .build();
    }

    private static RodeoLote rodeo(Long id) {
        RodeoLote rodeo = new RodeoLote();
        rodeo.setId(id);
        return rodeo;
    }

    private static Telemetria telemetria(Long animalId, RodeoLote rodeo) {
        Animal animal = new Animal();
        animal.setId(animalId);
        animal.setRodeoLote(rodeo);
        Caravana caravana = new Caravana();
        caravana.setAnimal(animal);
        Telemetria t = new Telemetria();
        t.setCaravana(caravana);
        t.setLatitud(-34.60);
        t.setLongitud(-58.38);
        t.setFechaHora(LocalDateTime.now().minusHours(1));
        return t;
    }

    @SuppressWarnings("unchecked")
    private static List<String> animalIdsDe(Object request) {
        List<Map<String, Object>> historial = (List<Map<String, Object>>) ((Map<String, Object>) request)
                .get("historial_7d");
        return historial.stream().map(p -> (String) p.get("animal_id")).distinct().sorted().toList();
    }
}
