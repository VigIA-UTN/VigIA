package dev.vigia.backend.service.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.client.MlCeloClient;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.dto.ml.CeloPredictResponseDTO;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.OrigenEstado;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.vigia.backend.mensajeria.PublicadorDeAlertas;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class CeloDetectorStrategyImplTest {

    @Mock
    private TelemetriaRepository telemetriaRepository;

    @Mock
    private AlertaRepository alertaRepository;

    @Mock
    private EstadoReproductivoRepository estadoReproductivoRepository;

    @Mock
    private PublicadorDeAlertas publicadorDeAlertas;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockRestServiceServer mockServer;
    private CeloDetectorStrategyImpl celoDetectorStrategy;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8000");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        celoDetectorStrategy = new CeloDetectorStrategyImpl(
                new MlCeloClient(builder.build()),
                telemetriaRepository,
                alertaRepository,
                estadoReproductivoRepository,
                publicadorDeAlertas);
    }

    private Telemetria crearTelemetria(Animal animal, Double lat, Double lon, Integer moveState, EstadoInferencia estado) {
        Caravana caravana = new Caravana();
        caravana.setAnimal(animal);

        Telemetria t = new Telemetria();
        t.setCaravana(caravana);
        t.setEstadoInferenciaCelo(estado);
        t.setMoveState(moveState);
        t.setLatitud(lat);
        t.setLongitud(lon);
        t.setFechaHora(LocalDateTime.now());
        return t;
    }

    @Test
    void testProcess_listaNulaOVacia_noHaceNada() {
        celoDetectorStrategy.process(null, "corr-null");
        celoDetectorStrategy.process(Collections.emptyList(), "corr-empty");

        mockServer.verify();
        verifyNoInteractions(telemetriaRepository, alertaRepository, estadoReproductivoRepository, publicadorDeAlertas);
    }

    @Test
    @SuppressWarnings("null")
    void testProcess_alertaRecienteUltimas24Hs_omiteInferenciaYMarcaCompletado() {
        Animal animal = new Animal();
        animal.setId(10L);

        Telemetria t1 = crearTelemetria(animal, -34.0, -60.0, 3, EstadoInferencia.PENDIENTE);
        Telemetria t2 = crearTelemetria(animal, -34.1, -60.1, 4, EstadoInferencia.COMPLETADO);
        List<Telemetria> lista = List.of(t1, t2);

        when(alertaRepository.existsByAnimalAndTipoAndTsGeneracionAfter(
                eq(animal), eq(Alerta.TIPO_CELO), any(LocalDateTime.class)))
                .thenReturn(true);

        celoDetectorStrategy.process(lista, "corr-cooldown");

        mockServer.verify(); // No se realizan llamadas HTTP
        assertEquals(EstadoInferencia.COMPLETADO, t1.getEstadoInferenciaCelo());
        assertEquals(EstadoInferencia.COMPLETADO, t2.getEstadoInferenciaCelo());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Telemetria>> captor = ArgumentCaptor.forClass(List.class);
        verify(telemetriaRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size(), "Solo las telemetrías originalmente pendientes deben guardarse");
        assertSame(t1, captor.getValue().get(0));

        verify(alertaRepository, never()).save(any());
        verify(estadoReproductivoRepository, never()).save(any());
        verify(publicadorDeAlertas, never()).publicar(any());
    }

    @Test
    @SuppressWarnings("null")
    void testProcess_inferenciaSinCelo_noGeneraAlertaNiEstadoYMarcaCompletado() throws Exception {
        Animal animal = new Animal();
        animal.setId(11L);

        Telemetria t = crearTelemetria(animal, -34.5, -58.5, 2, EstadoInferencia.PENDIENTE);
        List<Telemetria> lista = List.of(t);

        when(alertaRepository.existsByAnimalAndTipoAndTsGeneracionAfter(
                eq(animal), eq(Alerta.TIPO_CELO), any(LocalDateTime.class)))
                .thenReturn(false);

        CeloPredictResponseDTO predictResponse = CeloPredictResponseDTO.builder()
                .eventoCelo(false)
                .confianzaModelo(0.20f)
                .ventanaOptimaHoras(0)
                .build();

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/celo/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Correlation-ID", "corr-no-celo"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(predictResponse), MediaType.APPLICATION_JSON));

        celoDetectorStrategy.process(lista, "corr-no-celo");

        mockServer.verify();
        assertEquals(EstadoInferencia.COMPLETADO, t.getEstadoInferenciaCelo());
        verify(telemetriaRepository).saveAll(anyList());

        verify(alertaRepository, never()).save(any());
        verify(estadoReproductivoRepository, never()).save(any());
        verify(publicadorDeAlertas, never()).publicar(any());
    }

    @Test
    @SuppressWarnings("null")
    void testProcess_celoDetectado_generaAlertaNuevoEstadoYPublicaRabbit() throws Exception {
        Animal animal = new Animal();
        animal.setId(12L);

        EstadoReproductivo estadoPrevio = new EstadoReproductivo();
        estadoPrevio.setFechaUltParto(LocalDate.now().minusMonths(4));
        estadoPrevio.setFechaUltInseminacion(LocalDate.now().minusDays(21));
        List<EstadoReproductivo> estadosPrevios = new ArrayList<>();
        estadosPrevios.add(estadoPrevio);
        animal.setEstadosReproductivos(estadosPrevios);

        Telemetria t = crearTelemetria(animal, -34.6, -58.4, 4, EstadoInferencia.PENDIENTE);
        List<Telemetria> lista = List.of(t);

        when(alertaRepository.existsByAnimalAndTipoAndTsGeneracionAfter(
                eq(animal), eq(Alerta.TIPO_CELO), any(LocalDateTime.class)))
                .thenReturn(false);

        when(alertaRepository.save(any(Alerta.class))).thenAnswer(inv -> {
            Alerta a = inv.getArgument(0);
            a.setId(999L);
            return a;
        });

        CeloPredictResponseDTO predictResponse = CeloPredictResponseDTO.builder()
                .eventoCelo(true)
                .confianzaModelo(0.93f)
                .ventanaOptimaHoras(18)
                .build();

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/celo/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Correlation-ID", "corr-celo-ok"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(predictResponse), MediaType.APPLICATION_JSON));

        celoDetectorStrategy.process(lista, "corr-celo-ok");

        mockServer.verify();

        // 1. Verificar telemetría
        assertEquals(EstadoInferencia.COMPLETADO, t.getEstadoInferenciaCelo());
        verify(telemetriaRepository).saveAll(anyList());

        // 2. Verificar persistencia de Alerta
        ArgumentCaptor<Alerta> alertaCaptor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(alertaCaptor.capture());
        Alerta alertaGuardada = alertaCaptor.getValue();

        assertEquals(animal, alertaGuardada.getAnimal());
        assertEquals("Celo Detectado", alertaGuardada.getTipo());
        assertEquals(SeveridadAlerta.ALTA, alertaGuardada.getSeveridad());
        assertEquals(DisparadorAlerta.CELO, alertaGuardada.getDisparador());
        assertEquals(0.5, alertaGuardada.getValorUmbral());
        assertEquals(0.93, alertaGuardada.getValorDetectado(), 0.001);
        assertEquals(EstadoAlerta.PENDIENTE, alertaGuardada.getEstado());
        assertEquals(0.93f, alertaGuardada.getConfianzaModelo());
        assertEquals(18, alertaGuardada.getVentanaOptimaHoras());
        assertNotNull(alertaGuardada.getTsGeneracion());
        assertNotNull(alertaGuardada.getVentanaInicioIns());
        assertNotNull(alertaGuardada.getVentanaFinIns());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        assertEquals("Planificar inseminación artificial dentro de la ventana óptima (entre 6 y 24 horas posteriores a la detección: de "
                        + alertaGuardada.getVentanaInicioIns().format(fmt) + " a "
                        + alertaGuardada.getVentanaFinIns().format(fmt) + ")",
                alertaGuardada.getAccionRecomendada());

        // 3. Verificar persistencia de EstadoReproductivo
        ArgumentCaptor<EstadoReproductivo> estadoCaptor = ArgumentCaptor.forClass(EstadoReproductivo.class);
        verify(estadoReproductivoRepository).save(estadoCaptor.capture());
        EstadoReproductivo nuevoEstado = estadoCaptor.getValue();

        assertEquals(animal, nuevoEstado.getAnimal());
        assertEquals(TipoReproductivo.POSIBLE_CELO, nuevoEstado.getEstado());
        assertEquals(OrigenEstado.AUTOMATICO, nuevoEstado.getOrigen());
        assertNotNull(nuevoEstado.getFechaRegistro());
        assertEquals(estadoPrevio.getFechaUltParto(), nuevoEstado.getFechaUltParto());
        assertEquals(estadoPrevio.getFechaUltInseminacion(), nuevoEstado.getFechaUltInseminacion());

        // 4. Verificar publicación del evento en RabbitMQ
        ArgumentCaptor<AlertaEventDTO> eventCaptor = ArgumentCaptor.forClass(AlertaEventDTO.class);
        verify(publicadorDeAlertas).publicar(eventCaptor.capture());
        AlertaEventDTO eventoPublicado = eventCaptor.getValue();

        assertEquals(999L, eventoPublicado.getId());
        assertEquals(animal.getId(), eventoPublicado.getAnimalId());
        assertEquals("Celo Detectado", eventoPublicado.getTipo());
        assertEquals(SeveridadAlerta.ALTA, eventoPublicado.getSeveridad());
        assertEquals(0.93f, eventoPublicado.getConfianzaModelo());
        assertEquals(18, eventoPublicado.getVentanaOptimaHoras());
    }

    @Test
    @SuppressWarnings("null")
    void testProcess_celoDetectado_animalSinEstadosPrevios_persisteSinError() throws Exception {
        Animal animal = new Animal();
        animal.setId(15L);
        animal.setEstadosReproductivos(null); // Sin historial previo

        Telemetria t = crearTelemetria(animal, -34.6, -58.4, 4, EstadoInferencia.PENDIENTE);
        List<Telemetria> lista = List.of(t);

        when(alertaRepository.existsByAnimalAndTipoAndTsGeneracionAfter(
                eq(animal), eq(Alerta.TIPO_CELO), any(LocalDateTime.class)))
                .thenReturn(false);

        when(alertaRepository.save(any(Alerta.class))).thenAnswer(inv -> {
            Alerta a = inv.getArgument(0);
            a.setId(1001L);
            return a;
        });

        CeloPredictResponseDTO predictResponse = CeloPredictResponseDTO.builder()
                .eventoCelo(true)
                .confianzaModelo(0.85f)
                .ventanaOptimaHoras(12)
                .build();

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/celo/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(predictResponse), MediaType.APPLICATION_JSON));

        celoDetectorStrategy.process(lista, "corr-sin-historial");

        mockServer.verify();

        ArgumentCaptor<EstadoReproductivo> estadoCaptor = ArgumentCaptor.forClass(EstadoReproductivo.class);
        verify(estadoReproductivoRepository).save(estadoCaptor.capture());
        EstadoReproductivo nuevoEstado = estadoCaptor.getValue();

        assertEquals(TipoReproductivo.POSIBLE_CELO, nuevoEstado.getEstado());
        assertNull(nuevoEstado.getFechaUltParto());
        assertNull(nuevoEstado.getFechaUltInseminacion());
    }

    @Test
    @SuppressWarnings("null")
    void testProcess_filtradoTelemetriasIncompletas_soloEnviaPuntosValidos() throws Exception {
        Animal animal = new Animal();
        animal.setId(20L);

        Telemetria tValida1 = crearTelemetria(animal, -34.0, -60.0, 3, EstadoInferencia.PENDIENTE);
        Telemetria tSinLat = crearTelemetria(animal, null, -60.0, 3, EstadoInferencia.PENDIENTE);
        Telemetria tSinLon = crearTelemetria(animal, -34.0, null, 3, EstadoInferencia.PENDIENTE);
        Telemetria tSinMoveState = crearTelemetria(animal, -34.0, -60.0, null, EstadoInferencia.PENDIENTE);
        Telemetria tValida2 = crearTelemetria(animal, -34.1, -60.1, 5, EstadoInferencia.PENDIENTE);

        List<Telemetria> lista = List.of(tValida1, tSinLat, tSinLon, tSinMoveState, tValida2);

        when(alertaRepository.existsByAnimalAndTipoAndTsGeneracionAfter(
                eq(animal), eq(Alerta.TIPO_CELO), any(LocalDateTime.class)))
                .thenReturn(false);

        CeloPredictResponseDTO predictResponse = CeloPredictResponseDTO.builder()
                .eventoCelo(false)
                .confianzaModelo(0.1f)
                .ventanaOptimaHoras(0)
                .build();

        // Verificamos que el cuerpo enviado contenga exactamente 2 elementos
        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/celo/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.length()").value(2))
                .andRespond(withSuccess(objectMapper.writeValueAsString(predictResponse), MediaType.APPLICATION_JSON));

        celoDetectorStrategy.process(lista, "corr-filtrado");

        mockServer.verify();
    }

    @Test
    @SuppressWarnings("null")
    void testProcess_mlCeloFalla_marcaInferenciaPendienteSinPropagar() {
        Animal animal = new Animal();
        animal.setId(30L);
        Telemetria t = crearTelemetria(animal, -34.0, -60.0, 3, EstadoInferencia.PENDIENTE);
        List<Telemetria> lista = List.of(t);

        when(alertaRepository.existsByAnimalAndTipoAndTsGeneracionAfter(
                eq(animal), eq(Alerta.TIPO_CELO), any(LocalDateTime.class)))
                .thenReturn(false);

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/celo/predict"))
                .andRespond(withServerError());

        assertDoesNotThrow(() -> celoDetectorStrategy.process(lista, "corr-caido"));

        mockServer.verify();
        assertEquals(EstadoInferencia.INFERENCIA_PENDIENTE, t.getEstadoInferenciaCelo());
        verify(alertaRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void testMarcarInferenciaPendiente() {
        Telemetria t1 = new Telemetria();
        t1.setEstadoInferenciaCelo(EstadoInferencia.PENDIENTE);

        List<Telemetria> lista = List.of(t1);

        celoDetectorStrategy.marcarInferenciaPendiente(lista, "12345", new RuntimeException("Timeout"));

        assertEquals(EstadoInferencia.INFERENCIA_PENDIENTE, t1.getEstadoInferenciaCelo());
        verify(telemetriaRepository).saveAll(lista);
    }

    @Test
    @SuppressWarnings("null")
    void testProcess_alertaAnestroPrevia_noBloqueaInferenciaDeCelo() throws Exception {
        Animal animal = new Animal();
        animal.setId(99L);

        Telemetria t = crearTelemetria(animal, -34.6, -58.4, 4, EstadoInferencia.PENDIENTE);
        List<Telemetria> lista = List.of(t);

        // No existe alerta previa de CELO (aunque pudiera existir de Anestro con DisparadorAlerta.CELO)
        when(alertaRepository.existsByAnimalAndTipoAndTsGeneracionAfter(
                eq(animal), eq(Alerta.TIPO_CELO), any(LocalDateTime.class)))
                .thenReturn(false);

        when(alertaRepository.save(any(Alerta.class))).thenAnswer(inv -> {
            Alerta a = inv.getArgument(0);
            a.setId(999L);
            return a;
        });

        CeloPredictResponseDTO predictResponse = CeloPredictResponseDTO.builder()
                .eventoCelo(true)
                .confianzaModelo(0.92f)
                .ventanaOptimaHoras(12)
                .build();

        mockServer.expect(requestTo("http://localhost:8000/api/internal/ml/celo/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(predictResponse), MediaType.APPLICATION_JSON));

        celoDetectorStrategy.process(lista, "corr-anestro-no-bloquea");

        mockServer.verify();
        verify(alertaRepository).save(any(Alerta.class));
        verify(publicadorDeAlertas).publicar(any());
    }
}
