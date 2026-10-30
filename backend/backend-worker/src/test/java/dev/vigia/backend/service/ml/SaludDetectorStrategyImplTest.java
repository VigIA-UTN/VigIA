package dev.vigia.backend.service.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.client.MlSaludClient;
import dev.vigia.backend.dto.SerieComparativaSnapshot;
import dev.vigia.backend.dto.ml.PerfilCalibracionResponseDTO;
import dev.vigia.backend.dto.ml.PuntoTelemetriaDTO;
import dev.vigia.backend.dto.ml.SaludPredictResponseDTO;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.PerfilReferencia;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.PerfilReferenciaRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.vigia.backend.mensajeria.PublicadorDeAlertas;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntPredicate;

import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class SaludDetectorStrategyImplTest {

    @Mock
    private TelemetriaRepository telemetriaRepository;
    @Mock
    private AlertaRepository alertaRepository;
    @Mock
    private PerfilReferenciaRepository perfilReferenciaRepository;
    @Mock
    private PublicadorDeAlertas publicadorDeAlertas;

    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockRestServiceServer mockServer;
    private SaludDetectorStrategyImpl saludDetectorStrategy;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        saludDetectorStrategy = new SaludDetectorStrategyImpl(
                new MlSaludClient(builder.build()),
                telemetriaRepository,
                alertaRepository,
                perfilReferenciaRepository,
                publicadorDeAlertas,
                objectMapper,
                7,
                30,
                0.9,
                60);
    }

    private Telemetria telemetriaDeAnimal(Animal animal) {
        Caravana caravana = new Caravana();
        caravana.setAnimal(animal);

        Telemetria t = new Telemetria();
        t.setCaravana(caravana);
        t.setEstadoInferenciaSalud(EstadoInferencia.PENDIENTE);
        t.setMoveState(3);
        t.setLatitud(-34.0);
        t.setLongitud(-60.0);
        t.setFechaHora(LocalDateTime.now());
        return t;
    }

    private List<Telemetria> crearVentana(Animal animal, int size) {
        List<Telemetria> lista = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        Caravana caravana = new Caravana();
        caravana.setAnimal(animal);

        for (int i = 0; i < size; i++) {
            Telemetria t = new Telemetria();
            t.setCaravana(caravana);
            t.setEstadoInferenciaSalud(EstadoInferencia.PENDIENTE);
            // primer bloque de 6hs (i<72) con actividad baja, resto alta
            t.setMoveState(i < 72 ? 1 : 5);
            t.setLatitud(-34.0 + 0.0001 * i);
            t.setLongitud(-60.0);
            t.setFechaHora(base.plusMinutes(5L * i));
            lista.add(t);
        }
        return lista;
    }

    @SuppressWarnings("null")
    @Test
    void testMarcarInferenciaPendiente() {
        Telemetria t1 = new Telemetria();
        t1.setEstadoInferenciaSalud(EstadoInferencia.PENDIENTE);

        List<Telemetria> lista = List.of(t1);

        saludDetectorStrategy.marcarInferenciaPendiente(lista, "12345", new RuntimeException("Timeout"));

        assertEquals(EstadoInferencia.INFERENCIA_PENDIENTE, t1.getEstadoInferenciaSalud());
        verify(telemetriaRepository).saveAll(anyList());
    }

    private Telemetria datoDel(LocalDateTime fechaHora) {
        Telemetria t = new Telemetria();
        t.setFechaHora(fechaHora);
        return t;
    }

    private void esperarPerfilYPrediccionSinDesvio() throws Exception {
        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/salud/perfil"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(
                        PerfilCalibracionResponseDTO.builder().perfil(Map.of("feature1", 1.0)).build()),
                        MediaType.APPLICATION_JSON));
        SaludPredictResponseDTO sinDesvio = new SaludPredictResponseDTO();
        sinDesvio.setDesvioDetectado(false);
        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/salud/predict"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(sinDesvio), MediaType.APPLICATION_JSON));
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_animalEnCalibracion_noPredicionNiAlerta() {
        Animal animal = new Animal();
        animal.setId(1L);
        animal.setFechaAlta(LocalDate.now().minusYears(3)); // el alta no cuenta, solo los datos

        Telemetria t = telemetriaDeAnimal(animal);
        List<Telemetria> lista = List.of(t);
        when(telemetriaRepository.findFirstByCaravanaAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(
                eq(t.getCaravana()), any())).thenReturn(Optional.of(datoDel(LocalDateTime.now().minusDays(3))));

        saludDetectorStrategy.process(lista, "corr-calib-1");

        assertEquals(EstadoInferencia.COMPLETADO, t.getEstadoInferenciaSalud());
        verify(telemetriaRepository).saveAll(anyList());
        verify(telemetriaRepository, never()).findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(any(), any(), any());
        verify(alertaRepository, never()).save(any());
        verify(perfilReferenciaRepository, never()).save(any());

        // No interaction with mock server means no HTTP calls were made
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_caravanaSinTelemetriaTodavia_sigueEnCalibracionSinError() {
        Animal animal = new Animal();
        animal.setId(5L);
        animal.setFechaAlta(LocalDate.now().minusYears(3));

        Telemetria t = telemetriaDeAnimal(animal);
        t.getCaravana().setFechaAsignacion(LocalDate.now());
        // findFirst... devuelve Optional.empty(): nada desde la asignación

        saludDetectorStrategy.process(List.of(t), "corr-sin-datos");

        assertEquals(EstadoInferencia.COMPLETADO, t.getEstadoInferenciaSalud());
        verify(telemetriaRepository).findFirstByCaravanaAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(
                t.getCaravana(), LocalDate.now().atStartOfDay());
        verify(telemetriaRepository, never()).findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(any(), any(), any());
        verify(perfilReferenciaRepository, never()).save(any());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_primeraCalibracion_usaLosPrimeros7DiasDeDatos_aunqueElAltaSeaVieja() throws Exception {
        Animal animal = new Animal();
        animal.setId(4L);
        animal.setFechaAlta(LocalDate.now().minusYears(3));

        List<Telemetria> lista = crearVentana(animal, 288);
        Caravana caravana = lista.get(0).getCaravana();
        caravana.setFechaAsignacion(LocalDate.now().minusDays(10));
        LocalDateTime primerDato = LocalDateTime.now().minusDays(9);

        when(telemetriaRepository.findFirstByCaravanaAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(
                caravana, caravana.getFechaAsignacion().atStartOfDay())).thenReturn(Optional.of(datoDel(primerDato)));
        when(telemetriaRepository.findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(any(), any(), any()))
                .thenReturn(calibracion7Dias(primerDato, i -> false));
        when(perfilReferenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        esperarPerfilYPrediccionSinDesvio();

        saludDetectorStrategy.process(lista, "corr-primera");

        mockServer.verify();
        verify(telemetriaRepository).findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(
                caravana, primerDato, primerDato.plusDays(7));
        verify(perfilReferenciaRepository).save(any());
        assertEquals(EstadoInferencia.COMPLETADO, lista.get(0).getEstadoInferenciaSalud());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_sinFechaAlta_calibraIgualConSusPrimerosDatos() throws Exception {
        Animal animal = new Animal();
        animal.setId(2L);
        animal.setFechaAlta(null);

        List<Telemetria> lista = crearVentana(animal, 288);
        Caravana caravana = lista.get(0).getCaravana();
        LocalDateTime primerDato = LocalDateTime.now().minusDays(8);

        when(telemetriaRepository.findFirstByCaravanaAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(eq(caravana), any()))
                .thenReturn(Optional.of(datoDel(primerDato)));
        when(telemetriaRepository.findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(any(), any(), any()))
                .thenReturn(calibracion7Dias(primerDato, i -> false));
        when(perfilReferenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        esperarPerfilYPrediccionSinDesvio();

        saludDetectorStrategy.process(lista, "corr-sin-alta");

        mockServer.verify();
        verify(telemetriaRepository).findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(
                caravana, primerDato, primerDato.plusDays(7));
        verify(perfilReferenciaRepository).save(any());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_perfilDeMasDe30Dias_recalibraConLosUltimos7DiasSinBuscarElPrimerDato() throws Exception {
        Animal animal = new Animal();
        animal.setId(6L);
        animal.setFechaAlta(LocalDate.now().minusDays(60));

        List<Telemetria> lista = crearVentana(animal, 288);
        Caravana caravana = lista.get(0).getCaravana();

        PerfilReferencia perfil = new PerfilReferencia();
        perfil.setAnimal(animal);
        perfil.setFechaCalculo(LocalDateTime.now().minusDays(31));
        perfil.setPerfilJson("{\"feature1\": 1.0}");

        when(perfilReferenciaRepository.findByAnimal(animal)).thenReturn(Optional.of(perfil));
        when(telemetriaRepository.findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(any(), any(), any()))
                .thenReturn(calibracion7Dias(LocalDateTime.now().minusDays(7), i -> false));
        when(perfilReferenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        esperarPerfilYPrediccionSinDesvio();

        LocalDateTime antes = LocalDateTime.now();
        saludDetectorStrategy.process(lista, "corr-recalib");

        mockServer.verify();
        org.mockito.ArgumentCaptor<LocalDateTime> desde = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.ArgumentCaptor<LocalDateTime> hasta = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(telemetriaRepository).findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(
                eq(caravana), desde.capture(), hasta.capture());
        assertEquals(Duration.ofDays(7), Duration.between(desde.getValue(), hasta.getValue()));
        assertTrue(!hasta.getValue().isBefore(antes), "la recalibración termina ahora");
        verify(telemetriaRepository, never()).findFirstByCaravanaAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(any(), any());
    }

    /**
     * 7 días a 5 min desde `desde`: 2017 lecturas, porque el Between también trae la de `desde + 7 d`.
     * Sin las i que cumplan `perdida`. La latitud cambia con i para poder ubicarlas.
     */
    private List<Telemetria> calibracion7Dias(LocalDateTime desde, IntPredicate perdida) {
        List<Telemetria> lista = new ArrayList<>();
        for (int i = 0; i <= 2016; i++) {
            if (perdida.test(i)) {
                continue;
            }
            Telemetria t = new Telemetria();
            t.setMoveState(i % 8);
            t.setLatitud(-34.0 + 0.00001 * i);
            t.setLongitud(-60.0);
            t.setFechaHora(desde.plusMinutes(5L * i));
            lista.add(t);
        }
        return lista;
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_primeraCalibracionConUnPuntoPerdidoYUnoSinPosicion_mandaLos2016SlotsYEvalua() throws Exception {
        Animal animal = new Animal();
        animal.setId(20L);
        List<Telemetria> lista = crearVentana(animal, 288);
        Caravana caravana = lista.get(0).getCaravana();
        LocalDateTime primerDato = LocalDateTime.now().minusDays(8);
        List<Telemetria> calibracion = calibracion7Dias(primerDato, i -> i == 1000);
        calibracion.get(1499).setLatitud(null); // la i = 1500: una fila sin posición, como la de un heartbeat

        when(telemetriaRepository.findFirstByCaravanaAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(eq(caravana), any()))
                .thenReturn(Optional.of(datoDel(primerDato)));
        when(telemetriaRepository.findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(any(), any(), any()))
                .thenReturn(calibracion);
        when(perfilReferenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/salud/perfil"))
                .andExpect(jsonPath("$.calibracion.length()").value(2016))
                .andExpect(jsonPath("$.calibracion[1000].latitud").value(closeTo(-34.0 + 0.00001 * 1000, 1e-9), Double.class))
                .andExpect(jsonPath("$.calibracion[1500].latitud").value(closeTo(-34.0 + 0.00001 * 1500, 1e-9), Double.class))
                .andRespond(withSuccess(objectMapper.writeValueAsString(
                        PerfilCalibracionResponseDTO.builder().perfil(Map.of("feature1", 1.0)).build()),
                        MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/salud/predict"))
                .andRespond(withSuccess(sinDesvio(), MediaType.APPLICATION_JSON));

        saludDetectorStrategy.process(lista, "corr-calib-perdida");

        mockServer.verify();
        verify(perfilReferenciaRepository).save(any());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_primeraCalibracionConPocaCobertura_sigueEnCalibracionSinLlamarAMlSalud() {
        Animal animal = new Animal();
        animal.setId(21L);
        List<Telemetria> lista = crearVentana(animal, 288);
        Caravana caravana = lista.get(0).getCaravana();
        LocalDateTime primerDato = LocalDateTime.now().minusDays(8);

        when(telemetriaRepository.findFirstByCaravanaAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(eq(caravana), any()))
                .thenReturn(Optional.of(datoDel(primerDato)));
        when(telemetriaRepository.findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(any(), any(), any()))
                .thenReturn(calibracion7Dias(primerDato, i -> i % 5 == 0)); // 80 %

        saludDetectorStrategy.process(lista, "corr-calib-poca");

        mockServer.verify(); // ningún request a ml-salud
        verify(perfilReferenciaRepository, never()).save(any());
        assertEquals(EstadoInferencia.COMPLETADO, lista.get(0).getEstadoInferenciaSalud());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_recalibracionConPocaCobertura_evaluaConElPerfilAnterior() throws Exception {
        Animal animal = new Animal();
        animal.setId(22L);
        List<Telemetria> lista = crearVentana(animal, 288);

        PerfilReferencia perfil = new PerfilReferencia();
        perfil.setAnimal(animal);
        perfil.setFechaCalculo(LocalDateTime.now().minusDays(31));
        perfil.setPerfilJson("{\"feature1\": 1.0}");
        when(perfilReferenciaRepository.findByAnimal(animal)).thenReturn(Optional.of(perfil));
        when(telemetriaRepository.findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(any(), any(), any()))
                .thenReturn(calibracion7Dias(LocalDateTime.now().minusDays(7).plusMinutes(1), i -> i % 5 == 0));

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/salud/predict"))
                .andExpect(jsonPath("$.perfil.feature1").value(1.0))
                .andRespond(withSuccess(sinDesvio(), MediaType.APPLICATION_JSON));

        saludDetectorStrategy.process(lista, "corr-recalib-poca");

        mockServer.verify(); // sin /perfil: solo /predict, con el perfil anterior
        verify(perfilReferenciaRepository, never()).save(any());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_primeraCalibracionSinCoberturaEnLosPrimeros7Dias_usaLosUltimos7Dias() throws Exception {
        Animal animal = new Animal();
        animal.setId(23L);
        List<Telemetria> lista = crearVentana(animal, 288);
        Caravana caravana = lista.get(0).getCaravana();
        LocalDateTime primerDato = LocalDateTime.now().minusDays(20);

        when(telemetriaRepository.findFirstByCaravanaAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(eq(caravana), any()))
                .thenReturn(Optional.of(datoDel(primerDato)));
        // Los últimos 7 días, completos; los primeros 7 (el stub más específico, que gana), al 80 %.
        when(telemetriaRepository.findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(any(), any(), any()))
                .thenReturn(calibracion7Dias(LocalDateTime.now().minusDays(7), i -> false));
        when(telemetriaRepository.findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(
                caravana, primerDato, primerDato.plusDays(7)))
                .thenReturn(calibracion7Dias(primerDato, i -> i % 5 == 0));
        when(perfilReferenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        esperarPerfilYPrediccionSinDesvio(); // un solo /perfil: el de los últimos 7 días

        LocalDateTime antes = LocalDateTime.now();
        saludDetectorStrategy.process(lista, "corr-calib-fallback");

        mockServer.verify();
        verify(perfilReferenciaRepository).save(any());
        org.mockito.ArgumentCaptor<LocalDateTime> desde = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.ArgumentCaptor<LocalDateTime> hasta = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(telemetriaRepository, times(2)).findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(
                eq(caravana), desde.capture(), hasta.capture());
        assertEquals(primerDato, desde.getAllValues().get(0));
        assertEquals(Duration.ofDays(7), Duration.between(desde.getAllValues().get(1), hasta.getAllValues().get(1)));
        assertTrue(!hasta.getAllValues().get(1).isBefore(antes), "el reintento son los últimos 7 días");
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_desvioDetectado_generaAlertaYPublicaEnRabbit() throws Exception {
        Animal animal = new Animal();
        animal.setId(1L);
        animal.setFechaAlta(LocalDate.now().minusDays(10)); // Ya calibró

        Caravana c = new Caravana();
        c.setAnimal(animal);
        animal.setCaravana(c);

        List<Telemetria> lista = crearVentana(animal, 288);

        PerfilReferencia perfil = new PerfilReferencia();
        perfil.setAnimal(animal);
        perfil.setFechaCalculo(LocalDateTime.now().minusDays(1));
        perfil.setPerfilJson("{\"feature1\": 1.0}");

        when(perfilReferenciaRepository.findByAnimal(animal)).thenReturn(Optional.of(perfil));
        when(alertaRepository.existsByAnimalAndDisparadorAndTsGeneracionAfter(eq(animal), any(), any()))
                .thenReturn(false);
        when(alertaRepository.save(any())).thenAnswer(inv -> {
            dev.vigia.backend.model.Alerta a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });

        SaludPredictResponseDTO predictResponse = new SaludPredictResponseDTO();
        predictResponse.setDesvioDetectado(true);
        predictResponse.setSeveridad(3); // ALTA
        predictResponse.setConfianzaModelo(0.95f);
        predictResponse.setPosibleCausa("Actividad reducida y aislamiento");

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/salud/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(predictResponse), MediaType.APPLICATION_JSON));

        saludDetectorStrategy.process(lista, "corr-1");

        mockServer.verify();
        verify(alertaRepository).save(any());
        verify(publicadorDeAlertas).publicar(any(dev.vigia.backend.dto.ml.AlertaEventDTO.class));

        // La telemetría procesada se marca como COMPLETADA
        assertEquals(EstadoInferencia.COMPLETADO, lista.get(0).getEstadoInferenciaSalud());
        verify(telemetriaRepository).saveAll(anyList());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_desvioDetectado_persisteCausaYDesvioYPublicaEnEvento() throws Exception {
        Animal animal = new Animal();
        animal.setId(7L);
        animal.setFechaAlta(LocalDate.now().minusDays(10));

        Caravana c = new Caravana();
        c.setAnimal(animal);
        animal.setCaravana(c);

        List<Telemetria> lista = crearVentana(animal, 288);

        PerfilReferencia perfil = new PerfilReferencia();
        perfil.setAnimal(animal);
        perfil.setFechaCalculo(LocalDateTime.now().minusDays(1));
        perfil.setPerfilJson("{"
                + "\"quarter_moveState_mean\":[3.0,3.0,3.0,3.0],"
                + "\"quarter_moveState_std\":[0.5,0.5,0.5,0.5],"
                + "\"quarter_distancia_mean\":[10.0,10.0,10.0,10.0],"
                + "\"quarter_distancia_std\":[2.0,2.0,2.0,2.0]}");

        when(perfilReferenciaRepository.findByAnimal(animal)).thenReturn(Optional.of(perfil));
        when(alertaRepository.existsByAnimalAndDisparadorAndTsGeneracionAfter(eq(animal), any(), any()))
                .thenReturn(false);
        when(alertaRepository.save(any())).thenAnswer(inv -> {
            dev.vigia.backend.model.Alerta a = inv.getArgument(0);
            a.setId(101L);
            return a;
        });

        SaludPredictResponseDTO predictResponse = new SaludPredictResponseDTO();
        predictResponse.setDesvioDetectado(true);
        predictResponse.setSeveridad(2); // MEDIA
        predictResponse.setConfianzaModelo(0.80f);
        predictResponse.setPosibleCausa("Enfermedad moderada / Cojera clínica");

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/salud/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(predictResponse), MediaType.APPLICATION_JSON));

        saludDetectorStrategy.process(lista, "corr-sanitario");

        mockServer.verify();

        org.mockito.ArgumentCaptor<dev.vigia.backend.model.Alerta> alertaCaptor =
                org.mockito.ArgumentCaptor.forClass(dev.vigia.backend.model.Alerta.class);
        verify(alertaRepository).save(alertaCaptor.capture());
        dev.vigia.backend.model.Alerta guardada = alertaCaptor.getValue();
        assertEquals(Boolean.TRUE, guardada.getDesvioDetectado());
        assertEquals("Enfermedad moderada / Cojera clínica", guardada.getPosibleCausa());
        assertEquals("SALUD", guardada.getTipo());
        assertEquals("Apartar al animal para revisión clínica y evaluar tratamiento. Causa sospechada: Enfermedad moderada / Cojera clínica (riesgo medio).",
                guardada.getAccionRecomendada());

        // La serie comparativa se congela como JSON en la alerta (no se recalcula on-read).
        assertNotNull(guardada.getSerieComparativaJson());
        SerieComparativaSnapshot snap = objectMapper.readValue(
                guardada.getSerieComparativaJson(), SerieComparativaSnapshot.class);
        assertEquals(4, snap.getSerieActividad().size());
        assertEquals(4, snap.getSerieDesplazamiento().size());
        assertEquals("0-6h", snap.getSerieActividad().get(0).getBloque());
        assertEquals(1.0, snap.getSerieActividad().get(0).getActual(), 0.001);
        assertEquals(3.0, snap.getSerieActividad().get(0).getReferenciaMedia(), 0.001);
        assertNotNull(snap.getDescripcionDesvio());
        assertNotNull(snap.getVentanaDesde());
        assertNotNull(snap.getPerfilCalculadoEn());

        org.mockito.ArgumentCaptor<dev.vigia.backend.dto.ml.AlertaEventDTO> eventCaptor =
                org.mockito.ArgumentCaptor.forClass(dev.vigia.backend.dto.ml.AlertaEventDTO.class);
        verify(publicadorDeAlertas).publicar(eventCaptor.capture());
        dev.vigia.backend.dto.ml.AlertaEventDTO evento = eventCaptor.getValue();
        assertEquals(Boolean.TRUE, evento.getDesvioDetectado());
        assertEquals("Enfermedad moderada / Cojera clínica", evento.getPosibleCausa());
        assertEquals("Apartar al animal para revisión clínica y evaluar tratamiento. Causa sospechada: Enfermedad moderada / Cojera clínica (riesgo medio).",
                evento.getAccionRecomendada());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_sinDesvio_noGeneraAlerta() throws Exception {
        Animal animal = new Animal();
        animal.setId(2L);
        animal.setFechaAlta(LocalDate.now().minusDays(10));

        Caravana c = new Caravana();
        c.setAnimal(animal);
        animal.setCaravana(c);

        List<Telemetria> lista = crearVentana(animal, 288);

        PerfilReferencia perfil = new PerfilReferencia();
        perfil.setAnimal(animal);
        perfil.setFechaCalculo(LocalDateTime.now().minusDays(1));
        perfil.setPerfilJson("{\"feature1\": 1.0}");

        when(perfilReferenciaRepository.findByAnimal(animal)).thenReturn(Optional.of(perfil));
        when(alertaRepository.existsByAnimalAndDisparadorAndTsGeneracionAfter(eq(animal), any(), any()))
                .thenReturn(false);

        SaludPredictResponseDTO predictResponse = new SaludPredictResponseDTO();
        predictResponse.setDesvioDetectado(false);

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/salud/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(predictResponse), MediaType.APPLICATION_JSON));

        saludDetectorStrategy.process(lista, "corr-2");

        mockServer.verify();
        verify(alertaRepository, never()).save(any());
        verify(publicadorDeAlertas, never()).publicar(any());

        assertEquals(EstadoInferencia.COMPLETADO, lista.get(0).getEstadoInferenciaSalud());
        verify(telemetriaRepository).saveAll(anyList());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_mlSaludFalla_marcaInferenciaPendienteSinPropagar() {
        Animal animal = new Animal();
        animal.setId(3L);
        animal.setFechaAlta(LocalDate.now().minusDays(10));

        Caravana c = new Caravana();
        c.setAnimal(animal);
        animal.setCaravana(c);

        List<Telemetria> lista = crearVentana(animal, 288);

        PerfilReferencia perfil = new PerfilReferencia();
        perfil.setAnimal(animal);
        perfil.setFechaCalculo(LocalDateTime.now().minusDays(1));
        perfil.setPerfilJson("{\"feature1\": 1.0}");

        when(perfilReferenciaRepository.findByAnimal(animal)).thenReturn(Optional.of(perfil));
        when(alertaRepository.existsByAnimalAndDisparadorAndTsGeneracionAfter(eq(animal), any(), any()))
                .thenReturn(false);

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/salud/predict"))
                .andRespond(withServerError());

        saludDetectorStrategy.process(lista, "corr-caido");

        mockServer.verify();
        assertEquals(EstadoInferencia.INFERENCIA_PENDIENTE, lista.get(0).getEstadoInferenciaSalud());
        verify(alertaRepository, never()).save(any());
    }

    /**
     * 24 hs a 5 min empezando 1 min después del inicio de la ventana, así cada lectura cae en su slot,
     * sin las lecturas i que cumplan `perdida`. moveState y latitud cambian con i para poder ubicarlas.
     */
    private List<Telemetria> ventana24hs(Animal animal, IntPredicate perdida) {
        Caravana caravana = new Caravana();
        caravana.setAnimal(animal);
        LocalDateTime base = LocalDateTime.now().minusHours(24).plusMinutes(1);
        List<Telemetria> lista = new ArrayList<>();
        for (int i = 0; i < 288; i++) {
            if (perdida.test(i)) {
                continue;
            }
            Telemetria t = new Telemetria();
            t.setCaravana(caravana);
            t.setEstadoInferenciaSalud(EstadoInferencia.PENDIENTE);
            t.setMoveState(i % 8);
            t.setLatitud(-34.0 + 0.0001 * i);
            t.setLongitud(-60.0);
            t.setFechaHora(base.plusMinutes(5L * i));
            lista.add(t);
        }
        return lista;
    }

    private void conPerfilVigente(Animal animal) {
        PerfilReferencia perfil = new PerfilReferencia();
        perfil.setAnimal(animal);
        perfil.setFechaCalculo(LocalDateTime.now().minusDays(1));
        perfil.setPerfilJson("{\"feature1\": 1.0}");
        when(perfilReferenciaRepository.findByAnimal(animal)).thenReturn(Optional.of(perfil));
    }

    private String sinDesvio() throws Exception {
        return objectMapper.writeValueAsString(new SaludPredictResponseDTO());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_unUplinkPerdido_rellenaElSlotYEvalua() throws Exception {
        Animal animal = new Animal();
        animal.setId(10L);
        List<Telemetria> lista = ventana24hs(animal, i -> i == 143);
        conPerfilVigente(animal);

        // El slot 143 repite el moveState del 142 y queda a mitad de camino entre el 142 y el 144.
        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/salud/predict"))
                .andExpect(jsonPath("$.serie.length()").value(288))
                .andExpect(jsonPath("$.serie[143].moveState").value(142 % 8))
                .andExpect(jsonPath("$.serie[143].latitud").value(closeTo(-34.0 + 0.0001 * 143, 1e-9), Double.class))
                .andExpect(jsonPath("$.serie[144].latitud").value(closeTo(-34.0 + 0.0001 * 144, 1e-9), Double.class))
                .andRespond(withSuccess(sinDesvio(), MediaType.APPLICATION_JSON));

        saludDetectorStrategy.process(lista, "corr-un-perdido");

        mockServer.verify();
        assertEquals(EstadoInferencia.COMPLETADO, lista.get(0).getEstadoInferenciaSalud());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_coberturaMenorAl90_noEvaluaYQuedaCompletado() {
        Animal animal = new Animal();
        animal.setId(11L);
        List<Telemetria> lista = ventana24hs(animal, i -> i % 10 == 0); // 259 de 288: 89,9 %
        conPerfilVigente(animal);

        saludDetectorStrategy.process(lista, "corr-poca-cobertura");

        mockServer.verify(); // ningún request a ml-salud
        assertEquals(EstadoInferencia.COMPLETADO, lista.get(0).getEstadoInferenciaSalud());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_huecoDeMasDeUnaHora_noEvalua() {
        Animal animal = new Animal();
        animal.setId(12L);
        List<Telemetria> lista = ventana24hs(animal, i -> i >= 100 && i < 113); // 13 slots: 65 min
        conPerfilVigente(animal);

        saludDetectorStrategy.process(lista, "corr-hueco-largo");

        mockServer.verify();
        assertEquals(EstadoInferencia.COMPLETADO, lista.get(0).getEstadoInferenciaSalud());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_huecoDeUnaHoraJusta_rellenaYEvalua() throws Exception {
        Animal animal = new Animal();
        animal.setId(13L);
        List<Telemetria> lista = ventana24hs(animal, i -> i >= 100 && i < 112); // 12 slots: 60 min
        conPerfilVigente(animal);

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/salud/predict"))
                .andExpect(jsonPath("$.serie.length()").value(288))
                .andRespond(withSuccess(sinDesvio(), MediaType.APPLICATION_JSON));

        saludDetectorStrategy.process(lista, "corr-hueco-1h");

        mockServer.verify();
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_masDe288Puntos_mandaLosUltimos288ComoSiempre() throws Exception {
        // La cadencia de los simuladores de la demo: un paquete cada 5 s, cada uno es un tick.
        Animal animal = new Animal();
        animal.setId(14L);
        Caravana caravana = new Caravana();
        caravana.setAnimal(animal);
        LocalDateTime base = LocalDateTime.now().minusMinutes(30);
        List<Telemetria> lista = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            Telemetria t = new Telemetria();
            t.setCaravana(caravana);
            t.setEstadoInferenciaSalud(EstadoInferencia.PENDIENTE);
            t.setMoveState(3);
            t.setLatitud(-34.0 + 0.0001 * i);
            t.setLongitud(-60.0);
            t.setFechaHora(base.plusSeconds(5L * i));
            lista.add(t);
        }
        conPerfilVigente(animal);

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/salud/predict"))
                .andExpect(jsonPath("$.serie.length()").value(288))
                .andExpect(jsonPath("$.serie[0].latitud").value(closeTo(-34.0 + 0.0001 * 12, 1e-9), Double.class))
                .andRespond(withSuccess(sinDesvio(), MediaType.APPLICATION_JSON));

        saludDetectorStrategy.process(lista, "corr-demo");

        mockServer.verify();
    }

    @Test
    void puntosParaMl_ventanaCorta_losBordesRepitenLaLecturaMasCercanaYSeIgnoraLoDeAntes() {
        LocalDateTime desde = LocalDateTime.of(2026, 10, 1, 0, 0);
        List<Telemetria> filas = new ArrayList<>();
        for (Object[] f : new Object[][] {
                { desde.minusMinutes(3), 9, -1.0 },   // antes de la ventana: no cae en el slot 0
                { desde.plusMinutes(6), 1, -34.0 },   // slot 1
                { desde.plusMinutes(11), 2, -34.2 } }) { // slot 2
            Telemetria t = new Telemetria();
            t.setFechaHora((LocalDateTime) f[0]);
            t.setMoveState((Integer) f[1]);
            t.setLatitud((Double) f[2]);
            t.setLongitud(-60.0);
            filas.add(t);
        }

        List<PuntoTelemetriaDTO> puntos = VentanaTelemetria.puntosParaMl(filas, desde, 4, 0.5, 60);

        assertEquals(List.of(1, 1, 2, 2), puntos.stream().map(PuntoTelemetriaDTO::getMoveState).toList());
        assertEquals(List.of(-34.0, -34.0, -34.2, -34.2), puntos.stream().map(PuntoTelemetriaDTO::getLatitud).toList());
    }

    @Test
    void buildAccionRecomendada_severidadAlta() {
        assertEquals("Atención veterinaria urgente y aislamiento en enfermería. Causa sospechada: Problema severo (riesgo alto).",
                saludDetectorStrategy.buildAccionRecomendada("Problema severo", SeveridadAlerta.ALTA));
    }

    @Test
    void buildAccionRecomendada_severidadMedia() {
        assertEquals("Apartar al animal para revisión clínica y evaluar tratamiento. Causa sospechada: Enfermedad moderada (riesgo medio).",
                saludDetectorStrategy.buildAccionRecomendada("Enfermedad moderada", SeveridadAlerta.MEDIA));
    }

    @Test
    void buildAccionRecomendada_severidadBaja() {
        assertEquals("Monitorear la evolución y el comportamiento del animal en los próximos rodeos. Causa sospechada: Enfermedad leve / Inicio (riesgo bajo).",
                saludDetectorStrategy.buildAccionRecomendada("Enfermedad leve / Inicio", SeveridadAlerta.BAJA));
    }

    @Test
    void buildAccionRecomendada_causaNulaOVacia_noMuestraNull() {
        String esperado = "Atención veterinaria urgente y aislamiento en enfermería. Causa sospechada: no determinada (riesgo alto).";
        assertEquals(esperado, saludDetectorStrategy.buildAccionRecomendada(null, SeveridadAlerta.ALTA));
        assertEquals(esperado, saludDetectorStrategy.buildAccionRecomendada("   ", SeveridadAlerta.ALTA));
    }

    @Test
    void buildAccionRecomendada_severidadNula_textoGenerico() {
        assertEquals("Revisar al animal y realizar seguimiento clínico. Causa sospechada: no determinada.",
                saludDetectorStrategy.buildAccionRecomendada(null, null));
    }
}
