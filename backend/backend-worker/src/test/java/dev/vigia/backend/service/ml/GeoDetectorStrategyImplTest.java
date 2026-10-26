package dev.vigia.backend.service.ml;

import dev.vigia.backend.client.MlGeoClient;
import dev.vigia.backend.client.MlServicioDegradadoException;
import dev.vigia.backend.dto.AnimalAisladoResponse;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.dto.ml.geo.AlertaAnimalDTO;
import dev.vigia.backend.dto.ml.geo.CoordenadaGPSDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.AlertaAnimalAisladoRepository;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.RegistroCohesionRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.vigia.backend.mensajeria.PublicadorDeAlertas;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeoDetectorStrategyImpl - Pruebas Unitarias")
class GeoDetectorStrategyImplTest {

    @Mock
    private MlGeoClient mlGeoClient;
    @Mock
    private TelemetriaRepository telemetriaRepository;
    @Mock
    private AlertaRepository alertaRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private RegistroCohesionRepository registroCohesionRepository;
    @Mock
    private AlertaAnimalAisladoRepository alertaAnimalAisladoRepository;
    @Mock
    private PublicadorDeAlertas publicadorDeAlertas;

    private GeoDetectorStrategyImpl strategy;
    private Establecimiento establecimiento;

    @BeforeEach
    void setUp() {
        strategy = new GeoDetectorStrategyImpl(mlGeoClient, telemetriaRepository, alertaRepository,
                animalRepository, registroCohesionRepository, alertaAnimalAisladoRepository, publicadorDeAlertas, 15, 6);
        establecimiento = new Establecimiento();
        establecimiento.setId(1L);
        establecimiento.setUmbralAislamientoM(150.0);
    }

    // ── Telemetría y registros de cohesión ─────────────────────────────────────

    @Test
    void listaNulaOVacia_noHaceNada() {
        strategy.process(null, "corr-null");
        strategy.process(Collections.emptyList(), "corr-empty");

        verifyNoInteractions(mlGeoClient, telemetriaRepository, alertaRepository, registroCohesionRepository);
    }

    @Test
    void sinCoordenadasValidas_marcaCompletadosYNoLlamaMlGeo() {
        Telemetria t = telemetria(10L, null);
        t.setLatitud(null);

        strategy.process(List.of(t), "corr-sin-coord");

        assertThat(t.getEstadoInferenciaGeo()).isEqualTo(EstadoInferencia.COMPLETADO);
        verifyNoInteractions(mlGeoClient, registroCohesionRepository, alertaRepository);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Guarda un RegistroCohesion por animal y marca la telemetría como completada")
    void guardaRegistroCohesionPorAnimal() {
        Telemetria t1 = telemetria(10L, rodeo(100L));
        mlGeoResponde(geo("10", false, 35.0, 0.0, 0.0));

        strategy.process(List.of(t1), "corr-ok");

        ArgumentCaptor<RegistroCohesion> captor = ArgumentCaptor.forClass(RegistroCohesion.class);
        verify(registroCohesionRepository).save(captor.capture());
        RegistroCohesion saved = captor.getValue();
        assertThat(saved.getAnimal().getId()).isEqualTo(10L);
        assertThat(saved.getEstablecimiento()).isSameAs(establecimiento);
        assertThat(saved.getIndiceCohesion()).isEqualTo(0.9);
        assertThat(saved.getDistanciaCentroideM()).isEqualTo(35.0);
        assertThat(saved.getTiempoFueraGrupoS()).isEqualTo(0L);
        assertThat(t1.getEstadoInferenciaGeo()).isEqualTo(EstadoInferencia.COMPLETADO);
        verify(alertaRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Un request a ml-geo por rodeo (y uno para los animales sin rodeo), cada uno con el umbral del establecimiento")
    void unRequestPorRodeo() {
        RodeoLote r1 = rodeo(100L);
        RodeoLote r2 = rodeo(200L);
        when(mlGeoClient.analyze(any(), anyString())).thenReturn(new GeoAnalyzeResponseDTO());

        strategy.process(List.of(telemetria(10L, r1), telemetria(20L, r2), telemetria(11L, r1), telemetria(30L, null)),
                "corr-rodeos");

        ArgumentCaptor<GeoAnalyzeRequestDTO> requests = ArgumentCaptor.forClass(GeoAnalyzeRequestDTO.class);
        verify(mlGeoClient, times(3)).analyze(requests.capture(), eq("corr-rodeos"));
        assertThat(requests.getAllValues())
                .extracting(GeoDetectorStrategyImplTest::animalIdsDe)
                .containsExactlyInAnyOrder(List.of("10", "11"), List.of("20"), List.of("30"));
        assertThat(requests.getAllValues()).allSatisfy(r -> assertThat(r.getUmbralAislamientoM()).isEqualTo(150.0));
    }

    @Test
    @DisplayName("Sin umbral configurado se usa 100 m, en el request a ml-geo y en la alerta")
    void sinUmbralConfigurado_usa100() {
        establecimiento.setUmbralAislamientoM(null);
        RodeoLote r1 = rodeo(100L);
        mlGeoResponde(geo("10", true, 150.0, 900.0, 900.0), geo("11", false, 20.0, 0.0, 0.0));
        alertaRepositoryAsignaIds();

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1)), "corr-default");

        ArgumentCaptor<GeoAnalyzeRequestDTO> request = ArgumentCaptor.forClass(GeoAnalyzeRequestDTO.class);
        verify(mlGeoClient).analyze(request.capture(), anyString());
        assertThat(request.getValue().getUmbralAislamientoM()).isEqualTo(100.0);

        ArgumentCaptor<Alerta> alerta = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(alerta.capture());
        assertThat(alerta.getValue().getValorUmbral()).isEqualTo(100.0);
        assertThat(alerta.getValue().getSeveridad()).isEqualTo(SeveridadAlerta.MEDIA); // 150 m <= 2 × 100 m
    }

    @Test
    @DisplayName("Si ml-geo falla para un rodeo, los demás se procesan igual y solo esa telemetría queda pendiente")
    void falloEnUnRodeoNoFrenaALosDemas() {
        Telemetria rodeoCaido = telemetria(10L, rodeo(100L));
        Telemetria rodeoOk = telemetria(20L, rodeo(200L));
        when(mlGeoClient.analyze(any(), anyString())).thenAnswer(inv -> {
            List<String> ids = animalIdsDe(inv.getArgument(0));
            if (ids.contains("10")) {
                throw new MlServicioDegradadoException("ml-geo degradado", new RuntimeException("timeout"));
            }
            return respuesta(geo("20", false, 20.0, 0.0, 0.0));
        });

        assertThatCode(() -> strategy.process(List.of(rodeoCaido, rodeoOk), "corr-caido")).doesNotThrowAnyException();

        assertThat(rodeoCaido.getEstadoInferenciaGeo()).isEqualTo(EstadoInferencia.INFERENCIA_PENDIENTE);
        assertThat(rodeoOk.getEstadoInferenciaGeo()).isEqualTo(EstadoInferencia.COMPLETADO);
        ArgumentCaptor<RegistroCohesion> captor = ArgumentCaptor.forClass(RegistroCohesion.class);
        verify(registroCohesionRepository).save(captor.capture());
        assertThat(captor.getValue().getAnimal().getId()).isEqualTo(20L);
    }

    // ── Episodios de aislamiento y alertas ─────────────────────────────────────

    @Test
    @SuppressWarnings("null")
    @DisplayName("Animales aislados 15+ min sin episodio abierto: una alerta AISLAMIENTO_SOCIAL con un episodio por animal")
    void aislamientoNuevo_creaUnaAlertaConLosAnimales() {
        RodeoLote r1 = rodeo(100L);
        mlGeoResponde(
                geo("10", true, 400.0, 1800.0, 1200.0),
                geo("11", true, 200.0, 2400.0, 1200.0),
                geo("12", false, 20.0, 0.0, 0.0));
        alertaRepositoryAsignaIds();

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1), telemetria(12L, r1)), "corr");

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
        verify(publicadorDeAlertas).publicar(evento.capture());
        assertThat(evento.getValue().getAnimalesAislados())
                .extracting(AnimalAisladoResponse::getAnimalId, AnimalAisladoResponse::getMinutosFuera,
                        AnimalAisladoResponse::getSeveridad)
                .containsExactly(tuple(11L, 40L, "MEDIA"), tuple(10L, 30L, "ALTA"));
    }

    @Test
    @DisplayName("Un animal alejado hace menos de 15 minutos seguidos todavía no genera alerta")
    void menosDeQuinceMinutosSeguidos_noAlerta() {
        RodeoLote r1 = rodeo(100L);
        mlGeoResponde(
                geo("10", true, 400.0, 3600.0, 600.0), // mucho tiempo acumulado, pero solo 10 min seguidos
                geo("11", false, 20.0, 0.0, 0.0));

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1)), "corr");

        verify(alertaRepository, never()).save(any());
        verify(alertaAnimalAisladoRepository, never()).save(any());
        verifyNoInteractions(publicadorDeAlertas);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Aislamientos en dos rodeos generan una alerta por rodeo")
    void aislamientosEnDosRodeos_creanUnaAlertaPorRodeo() {
        RodeoLote r1 = rodeo(100L);
        RodeoLote r2 = rodeo(200L);
        mlGeoResponde(
                geo("10", true, 400.0, 1800.0, 1200.0),
                geo("11", false, 20.0, 0.0, 0.0),
                geo("20", true, 200.0, 900.0, 900.0),
                geo("21", false, 20.0, 0.0, 0.0));
        alertaRepositoryAsignaIds();

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1), telemetria(20L, r2), telemetria(21L, r2)),
                "corr");

        ArgumentCaptor<Alerta> alertas = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository, times(2)).save(alertas.capture());
        assertThat(alertas.getAllValues()).extracting(a -> a.getAnimal().getId()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @DisplayName("Un animal con su aislamiento ya abierto no genera otra alerta")
    void animalConEpisodioAbierto_noCreaOtraAlerta() {
        RodeoLote r1 = rodeo(100L);
        mlGeoResponde(geo("10", true, 400.0, 1800.0, 1200.0), geo("11", false, 20.0, 0.0, 0.0));
        when(alertaAnimalAisladoRepository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(1L))
                .thenReturn(List.of(episodio(alerta(900L, 10L, r1, EstadoAlerta.PENDIENTE), 10L, null)));

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1)), "corr");

        verify(alertaRepository, never()).save(any());
        verifyNoInteractions(publicadorDeAlertas);
    }

    @Test
    @DisplayName("Alerta ya confirmada y el animal sigue aislado: se refresca su episodio, sin alerta nueva")
    void alertaConfirmada_animalSigueAislado_refrescaSinDuplicar() {
        RodeoLote r1 = rodeo(100L);
        mlGeoResponde(geo("10", true, 420.0, 2400.0, 1800.0), geo("11", false, 20.0, 0.0, 0.0));
        AlertaAnimalAislado episodio = episodio(alerta(900L, 10L, r1, EstadoAlerta.CONFIRMADA), 10L, null);
        when(alertaAnimalAisladoRepository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(1L))
                .thenReturn(List.of(episodio));

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1)), "corr");

        verify(alertaRepository, never()).save(any());
        verifyNoInteractions(publicadorDeAlertas);
        verify(alertaAnimalAisladoRepository).save(episodio);
        assertThat(episodio.getSegundosFuera()).isEqualTo(2400L);
        assertThat(episodio.getDistanciaGrupoM()).isEqualTo(420.0);
        assertThat(episodio.getTsRegreso()).isNull();
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Si el animal vuelve al grupo se cierra su episodio; si después se vuelve a alejar, alerta nueva")
    void animalVuelveYSeVuelveAAlejar_cierraYDespuesAlertaDeNuevo() {
        RodeoLote r1 = rodeo(100L);
        when(mlGeoClient.analyze(any(), anyString())).thenReturn(
                respuesta(geo("10", false, 30.0, 1800.0, 0.0), geo("11", false, 20.0, 0.0, 0.0)),
                respuesta(geo("10", true, 400.0, 2700.0, 900.0), geo("11", false, 20.0, 0.0, 0.0)));
        Alerta pendiente = alerta(900L, 10L, r1, EstadoAlerta.PENDIENTE);
        AlertaAnimalAislado episodio = episodio(pendiente, 10L, null);
        when(alertaAnimalAisladoRepository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(1L))
                .thenReturn(List.of(episodio), List.of());
        alertaRepositoryAsignaIds();

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1)), "corr-1");

        verify(alertaAnimalAisladoRepository).save(episodio);
        assertThat(episodio.getTsRegreso()).isNotNull();
        assertThat(pendiente.getEstado()).isEqualTo(EstadoAlerta.PENDIENTE); // sin auto-resolver
        verify(alertaRepository, never()).save(any());

        pendiente.setEstado(EstadoAlerta.DESCARTADA);
        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1)), "corr-2");

        ArgumentCaptor<Alerta> nueva = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(nueva.capture());
        assertThat(nueva.getValue().getId()).isNotEqualTo(900L);
        assertThat(nueva.getValue().getAnimal().getId()).isEqualTo(10L);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Cuando animales vuelven al grupo se re-publica su alerta una vez (mismo id), con el regreso marcado")
    void animalesVuelven_republicanSuAlertaUnaVez() {
        RodeoLote r1 = rodeo(100L);
        mlGeoResponde(
                geo("10", false, 30.0, 1800.0, 0.0),
                geo("11", false, 25.0, 1500.0, 0.0),
                geo("12", false, 20.0, 0.0, 0.0));
        Alerta pendiente = alerta(900L, 10L, r1, EstadoAlerta.PENDIENTE);
        AlertaAnimalAislado episodio10 = episodio(pendiente, 10L, null);
        AlertaAnimalAislado episodio11 = episodio(pendiente, 11L, null);
        when(alertaAnimalAisladoRepository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(1L))
                .thenReturn(List.of(episodio10, episodio11));
        when(alertaAnimalAisladoRepository.findByAlerta_IdOrderBySegundosFueraDesc(900L))
                .thenReturn(List.of(episodio10, episodio11));

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1), telemetria(12L, r1)), "corr");

        ArgumentCaptor<AlertaEventDTO> evento = ArgumentCaptor.forClass(AlertaEventDTO.class);
        verify(publicadorDeAlertas).publicar(evento.capture());
        assertThat(evento.getValue().getId()).isEqualTo(900L);
        assertThat(evento.getValue().getEstado()).isEqualTo(EstadoAlerta.PENDIENTE); // sin auto-resolver
        assertThat(evento.getValue().getAnimalesAislados())
                .extracting(AnimalAisladoResponse::getAnimalId, a -> a.getTsRegreso() != null,
                        AnimalAisladoResponse::getMotivoCierre)
                .containsExactlyInAnyOrder(tuple(10L, true, MotivoCierreAislamiento.REGRESO),
                        tuple(11L, true, MotivoCierreAislamiento.REGRESO));
        verify(alertaRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Un animal nuevo que se aleja con una alerta pendiente del rodeo se suma a esa alerta")
    void nuevoAislamientoConAlertaPendienteDelRodeo_seSumaALaMismaAlerta() {
        RodeoLote r1 = rodeo(100L);
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
        when(alertaRepository.bloquearYObtenerEstado(900L)).thenReturn("PENDIENTE");
        alertaRepositoryAsignaIds();

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1), telemetria(12L, r1)), "corr");

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
        verify(publicadorDeAlertas).publicar(evento.capture());
        assertThat(evento.getValue().getId()).isEqualTo(900L);
        assertThat(evento.getValue().getAnimalesAislados())
                .extracting(AnimalAisladoResponse::getAnimalId)
                .containsExactly(11L, 10L); // el que se sumó y el que ya estaba, más tiempo fuera primero
    }

    @Test
    @DisplayName("Si el animal vuelve a alejarse con la alerta todavía pendiente, se reabre su fila en esa alerta")
    void reaislamientoConAlertaPendiente_reabreLaFilaDelAnimal() {
        RodeoLote r1 = rodeo(100L);
        mlGeoResponde(geo("10", true, 400.0, 2700.0, 900.0), geo("11", false, 20.0, 0.0, 0.0));
        Alerta pendiente = alerta(900L, 10L, r1, EstadoAlerta.PENDIENTE);
        AlertaAnimalAislado cerrado = episodio(pendiente, 10L, LocalDateTime.now().minusMinutes(30));
        when(alertaRepository.findByFiltros(1L, EstadoAlerta.PENDIENTE, Set.of(DisparadorAlerta.AISLAMIENTO_SOCIAL), null))
                .thenReturn(List.of(pendiente));
        when(alertaAnimalAisladoRepository.findByAlerta_IdOrderBySegundosFueraDesc(900L))
                .thenReturn(List.of(cerrado));
        when(alertaRepository.bloquearYObtenerEstado(900L)).thenReturn("PENDIENTE");
        alertaRepositoryAsignaIds();

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1)), "corr");

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
    void alertaPendienteDeOtroRodeo_creaAlertaNueva() {
        RodeoLote r1 = rodeo(100L);
        mlGeoResponde(geo("10", true, 400.0, 1800.0, 1200.0), geo("11", false, 20.0, 0.0, 0.0));
        when(alertaRepository.findByFiltros(1L, EstadoAlerta.PENDIENTE, Set.of(DisparadorAlerta.AISLAMIENTO_SOCIAL), null))
                .thenReturn(List.of(alerta(900L, 20L, rodeo(200L), EstadoAlerta.PENDIENTE)));
        alertaRepositoryAsignaIds();

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1)), "corr");

        ArgumentCaptor<Alerta> guardada = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(guardada.capture());
        assertThat(guardada.getValue().getId()).isNotEqualTo(900L);
        assertThat(guardada.getValue().getAnimal().getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Un animal sin datos en esta corrida (ml-geo no lo clasifica) conserva su episodio abierto")
    void animalSinDatos_conservaSuEpisodioAbierto() {
        RodeoLote r1 = rodeo(100L);
        mlGeoResponde(geo("10", false, 20.0, 0.0, 0.0), geo("11", false, 20.0, 0.0, 0.0));
        AlertaAnimalAislado sinDatos = episodio(alerta(900L, 30L, r1, EstadoAlerta.PENDIENTE), 30L, null);
        when(alertaAnimalAisladoRepository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(1L))
                .thenReturn(List.of(sinDatos));
        Telemetria haceDosHoras = telemetria(30L, r1);
        haceDosHoras.setFechaHora(LocalDateTime.now().minusHours(2));

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1), haceDosHoras), "corr");

        verify(alertaAnimalAisladoRepository, never()).save(any());
        assertThat(sinDatos.getTsRegreso()).isNull();
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Un aislamiento abierto sin posiciones hace más de 6 h se cierra como SIN_DATOS y se re-publica la alerta")
    void episodioSinPosicionesHaceMasDe6h_seCierraSinDatos() {
        RodeoLote r1 = rodeo(100L);
        mlGeoResponde(geo("10", false, 20.0, 0.0, 0.0), geo("11", false, 20.0, 0.0, 0.0));
        Alerta pendiente = alerta(900L, 30L, r1, EstadoAlerta.PENDIENTE);
        AlertaAnimalAislado mudo = episodio(pendiente, 30L, null);
        Caravana caravana = new Caravana();
        caravana.setUltimaSenial(LocalDateTime.now().minusHours(7));
        mudo.getAnimal().setCaravana(caravana);
        when(alertaAnimalAisladoRepository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(1L))
                .thenReturn(List.of(mudo));
        when(alertaAnimalAisladoRepository.findByAlerta_IdOrderBySegundosFueraDesc(900L)).thenReturn(List.of(mudo));

        strategy.process(List.of(telemetria(10L, r1), telemetria(11L, r1)), "corr");

        verify(alertaAnimalAisladoRepository).save(mudo);
        assertThat(mudo.getTsRegreso()).isNotNull();
        assertThat(mudo.getMotivoCierre()).isEqualTo(MotivoCierreAislamiento.SIN_DATOS);
        assertThat(pendiente.getEstado()).isEqualTo(EstadoAlerta.PENDIENTE);
        verify(alertaRepository, never()).save(any());

        ArgumentCaptor<AlertaEventDTO> evento = ArgumentCaptor.forClass(AlertaEventDTO.class);
        verify(publicadorDeAlertas).publicar(evento.capture());
        assertThat(evento.getValue().getId()).isEqualTo(900L);
        assertThat(evento.getValue().getAnimalesAislados())
                .extracting(AnimalAisladoResponse::getAnimalId, AnimalAisladoResponse::getMotivoCierre)
                .containsExactly(tuple(30L, MotivoCierreAislamiento.SIN_DATOS));
    }

    @Test
    @DisplayName("Sin posiciones en la ventana pero con señal reciente de la caravana, el aislamiento sigue abierto")
    void episodioSinPosicionesConSenialReciente_sigueAbierto() {
        RodeoLote r1 = rodeo(100L);
        mlGeoResponde(geo("10", false, 20.0, 0.0, 0.0));
        AlertaAnimalAislado episodio = episodio(alerta(900L, 30L, r1, EstadoAlerta.PENDIENTE), 30L, null);
        Caravana caravana = new Caravana();
        caravana.setUltimaSenial(LocalDateTime.now().minusHours(1));
        episodio.getAnimal().setCaravana(caravana);
        when(alertaAnimalAisladoRepository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(1L))
                .thenReturn(List.of(episodio));

        strategy.process(List.of(telemetria(10L, r1)), "corr");

        assertThat(episodio.getTsRegreso()).isNull();
        verify(alertaAnimalAisladoRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Si la alerta pendiente del rodeo se resolvió antes de bloquearla, no se la pisa: alerta nueva")
    void alertaPendienteResueltaAlBloquearla_creaAlertaNueva() {
        RodeoLote r1 = rodeo(100L);
        mlGeoResponde(geo("11", true, 350.0, 3000.0, 900.0), geo("12", false, 20.0, 0.0, 0.0));
        Alerta cargadaComoPendiente = alerta(900L, 10L, r1, EstadoAlerta.PENDIENTE);
        when(alertaRepository.findByFiltros(1L, EstadoAlerta.PENDIENTE, Set.of(DisparadorAlerta.AISLAMIENTO_SOCIAL), null))
                .thenReturn(List.of(cargadaComoPendiente));
        when(alertaRepository.bloquearYObtenerEstado(900L)).thenReturn("CONFIRMADA");
        alertaRepositoryAsignaIds();

        strategy.process(List.of(telemetria(11L, r1), telemetria(12L, r1)), "corr");

        ArgumentCaptor<Alerta> guardada = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(guardada.capture());
        assertThat(guardada.getValue()).isNotSameAs(cargadaComoPendiente);
        assertThat(guardada.getValue().getAnimal().getId()).isEqualTo(11L);
        verify(alertaAnimalAisladoRepository, never()).findByAlerta_IdOrderBySegundosFueraDesc(900L);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Telemetria telemetria(Long animalId, RodeoLote rodeo) {
        Animal animal = new Animal();
        animal.setId(animalId);
        animal.setEstablecimiento(establecimiento);
        animal.setRodeoLote(rodeo);
        Caravana caravana = new Caravana();
        caravana.setAnimal(animal);
        caravana.setEstablecimiento(establecimiento);
        Telemetria t = new Telemetria();
        t.setCaravana(caravana);
        t.setLatitud(-34.60);
        t.setLongitud(-58.38);
        t.setFechaHora(LocalDateTime.now().minusMinutes(5));
        t.setEstadoInferenciaGeo(EstadoInferencia.PENDIENTE);
        return t;
    }

    private static RodeoLote rodeo(Long id) {
        RodeoLote rodeo = new RodeoLote();
        rodeo.setId(id);
        return rodeo;
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

    private static AlertaAnimalDTO geo(String animalId, boolean alejado, double distanciaM, double acumuladoS,
            double continuoS) {
        return AlertaAnimalDTO.builder()
                .animalId(animalId)
                .indiceCohesion(alejado ? 0.2 : 0.9)
                .alertaAislamiento(alejado)
                .distanciaCentroideM(distanciaM)
                .tiempoFueraGrupoS(acumuladoS)
                .tiempoFueraContinuoS(continuoS)
                .build();
    }

    private static GeoAnalyzeResponseDTO respuesta(AlertaAnimalDTO... alertas) {
        return GeoAnalyzeResponseDTO.builder().alertas(List.of(alertas)).build();
    }

    /** ml-geo responde, para cada request, solo los animales que vinieron en ese request (un rodeo). */
    private void mlGeoResponde(AlertaAnimalDTO... alertas) {
        when(mlGeoClient.analyze(any(), anyString())).thenAnswer(inv -> {
            List<String> ids = animalIdsDe(inv.getArgument(0));
            return respuesta(Arrays.stream(alertas).filter(a -> ids.contains(a.getAnimalId()))
                    .toArray(AlertaAnimalDTO[]::new));
        });
    }

    private static List<String> animalIdsDe(GeoAnalyzeRequestDTO request) {
        return request.getHistorial7d().stream().map(CoordenadaGPSDTO::getAnimalId).distinct().toList();
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
}
