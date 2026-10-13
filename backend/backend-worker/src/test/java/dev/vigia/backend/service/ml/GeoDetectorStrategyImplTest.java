package dev.vigia.backend.service.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.dto.ml.geo.AlertaAnimalDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.dto.ml.geo.MatrizDensidadDTO;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeoDetectorStrategyImpl - Pruebas Unitarias")
class GeoDetectorStrategyImplTest {

    @Mock
    private TelemetriaRepository telemetriaRepository;

    @Mock
    private AlertaRepository alertaRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockRestServiceServer mockServer;
    private GeoDetectorStrategyImpl geoDetectorStrategy;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        geoDetectorStrategy = new GeoDetectorStrategyImpl(
                builder,
                "http://localhost:8003",
                telemetriaRepository,
                alertaRepository,
                animalRepository,
                rabbitTemplate,
                6);
    }

    private Telemetria crearTelemetria(Animal animal, Double lat, Double lng, EstadoInferencia estado, LocalDateTime fechaHora) {
        Caravana caravana = new Caravana();
        caravana.setCodigo("CAR-00" + animal.getId());
        caravana.setAnimal(animal);

        Telemetria t = new Telemetria();
        t.setCaravana(caravana);
        t.setLatitud(lat);
        t.setLongitud(lng);
        t.setEstadoInferenciaGeo(estado);
        t.setFechaHora(fechaHora);
        return t;
    }

    @Test
    void testProcess_listaNulaOVacia_noHaceNada() {
        geoDetectorStrategy.process(null, "corr-null");
        geoDetectorStrategy.process(Collections.emptyList(), "corr-empty");

        verify(telemetriaRepository, never()).saveAll(anyList());
        verify(alertaRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void testProcess_sinCoordenadasValidas_marcaCompletadosYNoLlamaMicroservicio() {
        Animal animal = new Animal();
        animal.setId(1L);

        Telemetria t = crearTelemetria(animal, null, null, EstadoInferencia.PENDIENTE, LocalDateTime.now());
        List<Telemetria> lista = List.of(t);

        geoDetectorStrategy.process(lista, "corr-sin-coord");

        assertEquals(EstadoInferencia.COMPLETADO, t.getEstadoInferenciaGeo());
        verify(telemetriaRepository).saveAll(anyList());
        verify(alertaRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void testProcess_llamadaExitosaSinAislamiento_marcaCompletadosYNoGeneraAlerta() throws Exception {
        Establecimiento est = new Establecimiento();
        est.setUmbralAislamientoM(120.0);

        Animal a1 = new Animal();
        a1.setId(10L);
        a1.setEstablecimiento(est);

        Animal a2 = new Animal();
        a2.setId(20L);
        a2.setEstablecimiento(est);

        Telemetria t1 = crearTelemetria(a1, -34.500, -58.500, EstadoInferencia.PENDIENTE, LocalDateTime.now().minusMinutes(10));
        Telemetria t2 = crearTelemetria(a2, -34.501, -58.501, EstadoInferencia.PENDIENTE, LocalDateTime.now().minusMinutes(5));
        List<Telemetria> lista = List.of(t1, t2);

        GeoAnalyzeResponseDTO mlResponse = GeoAnalyzeResponseDTO.builder()
                .mapaCalor(List.of(MatrizDensidadDTO.builder().lat(-34.5).lng(-58.5).intensidad(1.0).build()))
                .alertas(List.of(
                        AlertaAnimalDTO.builder()
                                .animalId("10")
                                .indiceCohesion(0.95)
                                .distanciaCentroideM(15.0)
                                .alertaAislamiento(false)
                                .tiempoFueraGrupoS(0.0)
                                .build(),
                        AlertaAnimalDTO.builder()
                                .animalId("20")
                                .indiceCohesion(0.95)
                                .distanciaCentroideM(15.0)
                                .alertaAislamiento(false)
                                .tiempoFueraGrupoS(0.0)
                                .build()
                ))
                .build();

        mockServer.expect(requestTo("http://localhost:8003/api/internal/ml/geo/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Correlation-ID", "corr-ok"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(mlResponse), MediaType.APPLICATION_JSON));

        geoDetectorStrategy.process(lista, "corr-ok");

        mockServer.verify();

        assertEquals(EstadoInferencia.COMPLETADO, t1.getEstadoInferenciaGeo());
        assertEquals(EstadoInferencia.COMPLETADO, t2.getEstadoInferenciaGeo());
        verify(telemetriaRepository).saveAll(anyList());
        verify(alertaRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @SuppressWarnings("null")
    void testProcess_alertaAislamientoDetectada_generaAlertaYPublicaRabbit() throws Exception {
        Establecimiento est = new Establecimiento();
        est.setUmbralAislamientoM(100.0);

        Animal a1 = new Animal();
        a1.setId(10L);
        a1.setEstablecimiento(est);

        Animal a2 = new Animal();
        a2.setId(20L);
        a2.setEstablecimiento(est);

        LocalDateTime ahora = LocalDateTime.now();
        Telemetria t1 = crearTelemetria(a1, -34.500, -58.500, EstadoInferencia.PENDIENTE, ahora.minusMinutes(10));
        Telemetria t2 = crearTelemetria(a2, -34.550, -58.550, EstadoInferencia.PENDIENTE, ahora.minusMinutes(5));
        List<Telemetria> lista = List.of(t1, t2);

        when(animalRepository.findById(20L)).thenReturn(Optional.of(a2));
        when(alertaRepository.findFirstByAnimalAndDisparadorAndTsGeneracionAfterOrderByTsGeneracionDesc(
                eq(a2), eq(DisparadorAlerta.ABIGEATO), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        when(alertaRepository.save(any(Alerta.class))).thenAnswer(invocation -> {
            Alerta arg = invocation.getArgument(0);
            arg.setId(777L);
            return arg;
        });

        GeoAnalyzeResponseDTO mlResponse = GeoAnalyzeResponseDTO.builder()
                .mapaCalor(Collections.emptyList())
                .alertas(List.of(
                        AlertaAnimalDTO.builder()
                                .animalId("20")
                                .indiceCohesion(0.20)
                                .distanciaCentroideM(400.0)
                                .alertaAislamiento(true)
                                .tiempoFueraGrupoS(1800.0)
                                .build()
                ))
                .build();

        mockServer.expect(requestTo("http://localhost:8003/api/internal/ml/geo/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Correlation-ID", "corr-alerta"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(mlResponse), MediaType.APPLICATION_JSON));

        geoDetectorStrategy.process(lista, "corr-alerta");

        mockServer.verify();

        ArgumentCaptor<Alerta> alertaCaptor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(alertaCaptor.capture());
        Alerta alertaGuardada = alertaCaptor.getValue();

        assertEquals(a2, alertaGuardada.getAnimal());
        assertEquals("Aislamiento de Rodeo", alertaGuardada.getTipo());
        assertEquals(SeveridadAlerta.ALTA, alertaGuardada.getSeveridad());
        assertEquals(DisparadorAlerta.ABIGEATO, alertaGuardada.getDisparador());
        assertEquals(100.0, alertaGuardada.getValorUmbral());
        assertEquals(400.0, alertaGuardada.getValorDetectado());
        assertEquals(0.20f, alertaGuardada.getConfianzaModelo());
        assertEquals(-34.550, alertaGuardada.getLatitud());
        assertEquals(-58.550, alertaGuardada.getLongitud());
        assertEquals(EstadoAlerta.PENDIENTE, alertaGuardada.getEstado());

        ArgumentCaptor<AlertaEventDTO> rabbitCaptor = ArgumentCaptor.forClass(AlertaEventDTO.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.ALERTAS_EXCHANGE), eq(""), rabbitCaptor.capture());
        AlertaEventDTO evento = rabbitCaptor.getValue();

        assertEquals(777L, evento.getId());
        assertEquals(20L, evento.getAnimalId());
        assertEquals(DisparadorAlerta.ABIGEATO, evento.getDisparador());
        assertEquals(400.0, evento.getValorDetectado());
    }

    @Test
    @SuppressWarnings("null")
    void testProcess_alertaExistentePendiente_actualizaPosicionYNoDuplicaAlerta() throws Exception {
        Animal a1 = new Animal();
        a1.setId(10L);

        Telemetria t1 = crearTelemetria(a1, -34.600, -58.600, EstadoInferencia.PENDIENTE, LocalDateTime.now());
        List<Telemetria> lista = List.of(t1);

        Alerta alertaExistente = new Alerta();
        alertaExistente.setId(888L);
        alertaExistente.setAnimal(a1);
        alertaExistente.setEstado(EstadoAlerta.PENDIENTE);
        alertaExistente.setLatitud(-34.500);
        alertaExistente.setLongitud(-58.500);

        when(animalRepository.findById(10L)).thenReturn(Optional.of(a1));
        when(alertaRepository.findFirstByAnimalAndDisparadorAndTsGeneracionAfterOrderByTsGeneracionDesc(
                eq(a1), eq(DisparadorAlerta.ABIGEATO), any(LocalDateTime.class)))
                .thenReturn(Optional.of(alertaExistente));

        GeoAnalyzeResponseDTO mlResponse = GeoAnalyzeResponseDTO.builder()
                .alertas(List.of(
                        AlertaAnimalDTO.builder()
                                .animalId("10")
                                .indiceCohesion(0.15)
                                .distanciaCentroideM(550.0)
                                .alertaAislamiento(true)
                                .tiempoFueraGrupoS(3600.0)
                                .build()
                ))
                .build();

        mockServer.expect(requestTo("http://localhost:8003/api/internal/ml/geo/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(mlResponse), MediaType.APPLICATION_JSON));

        geoDetectorStrategy.process(lista, "corr-update");

        mockServer.verify();

        verify(alertaRepository).save(alertaExistente);
        assertEquals(-34.600, alertaExistente.getLatitud());
        assertEquals(-58.600, alertaExistente.getLongitud());
        assertEquals(550.0, alertaExistente.getValorDetectado());

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void testProcess_errorMicroservicio_ejecutaFallbackYMarcaInferenciaPendiente() {
        Animal a1 = new Animal();
        a1.setId(10L);

        Telemetria t1 = crearTelemetria(a1, -34.5, -58.5, EstadoInferencia.PENDIENTE, LocalDateTime.now());
        List<Telemetria> lista = List.of(t1);

        geoDetectorStrategy.fallbackProcess(lista, "corr-fallback", new RuntimeException("Timeout"));

        assertEquals(EstadoInferencia.INFERENCIA_PENDIENTE, t1.getEstadoInferenciaGeo());
        verify(telemetriaRepository).saveAll(anyList());
        verify(alertaRepository, never()).save(any());
    }
}
