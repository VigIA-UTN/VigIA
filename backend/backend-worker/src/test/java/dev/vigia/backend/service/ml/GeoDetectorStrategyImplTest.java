package dev.vigia.backend.service.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.client.MlGeoClient;
import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.dto.ml.geo.AlertaAnimalDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.model.*;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeoDetectorStrategyImpl - Pruebas Unitarias")
class GeoDetectorStrategyImplTest {

    private static final String URL_ANALYZE = "http://localhost:8003/api/internal/ml/geo/analyze";

    @Mock
    private TelemetriaRepository telemetriaRepository;

    @Mock
    private AlertaRepository alertaRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private RegistroCohesionRepository registroCohesionRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockRestServiceServer mockServer;
    private GeoDetectorStrategyImpl geoDetectorStrategy;

    private Establecimiento establecimiento;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8003");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        geoDetectorStrategy = new GeoDetectorStrategyImpl(
                new MlGeoClient(builder.build()),
                telemetriaRepository,
                alertaRepository,
                animalRepository,
                registroCohesionRepository,
                rabbitTemplate,
                6);

        establecimiento = new Establecimiento();
        establecimiento.setId(1L);
        establecimiento.setUmbralAislamientoM(120.0);
    }

    private Animal animal(Long id, RodeoLote rodeo) {
        Animal a = new Animal();
        a.setId(id);
        a.setEstablecimiento(establecimiento);
        a.setRodeoLote(rodeo);
        return a;
    }

    private RodeoLote rodeo(Long id) {
        RodeoLote r = new RodeoLote();
        r.setId(id);
        return r;
    }

    private Telemetria telemetria(Animal animal, Double lat, Double lng, LocalDateTime fechaHora) {
        Caravana caravana = new Caravana();
        caravana.setAnimal(animal);
        caravana.setEstablecimiento(establecimiento);

        Telemetria t = new Telemetria();
        t.setCaravana(caravana);
        t.setLatitud(lat);
        t.setLongitud(lng);
        t.setEstadoInferenciaGeo(EstadoInferencia.PENDIENTE);
        t.setFechaHora(fechaHora);
        return t;
    }

    private AlertaAnimalDTO resultado(String animalId, double indice, double distancia, boolean aislado, double tiempoFuera) {
        return AlertaAnimalDTO.builder()
                .animalId(animalId)
                .indiceCohesion(indice)
                .distanciaCentroideM(distancia)
                .alertaAislamiento(aislado)
                .tiempoFueraGrupoS(tiempoFuera)
                .build();
    }

    private String respuesta(AlertaAnimalDTO... alertas) throws Exception {
        return objectMapper.writeValueAsString(GeoAnalyzeResponseDTO.builder()
                .mapaCalor(Collections.emptyList())
                .alertas(List.of(alertas))
                .build());
    }

    @Test
    void listaNulaOVacia_noHaceNada() {
        geoDetectorStrategy.process(null, "corr-null");
        geoDetectorStrategy.process(Collections.emptyList(), "corr-empty");

        mockServer.verify();
        verifyNoInteractions(telemetriaRepository, alertaRepository, registroCohesionRepository, rabbitTemplate);
    }

    @Test
    void sinCoordenadasValidas_marcaCompletadosYNoLlamaMicroservicio() {
        Telemetria t = telemetria(animal(1L, null), null, null, LocalDateTime.now());

        geoDetectorStrategy.process(List.of(t), "corr-sin-coord");

        mockServer.verify();
        assertEquals(EstadoInferencia.COMPLETADO, t.getEstadoInferenciaGeo());
        verify(telemetriaRepository).saveAll(anyList());
        verifyNoInteractions(registroCohesionRepository, alertaRepository);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Sin aislamiento: envía el umbral del establecimiento, guarda un RegistroCohesion por animal y no alerta")
    void sinAislamiento_guardaRegistrosYNoGeneraAlerta() throws Exception {
        RodeoLote r = rodeo(100L);
        Telemetria t1 = telemetria(animal(10L, r), -34.500, -58.500, LocalDateTime.now().minusMinutes(10));
        Telemetria t2 = telemetria(animal(20L, r), -34.501, -58.501, LocalDateTime.now().minusMinutes(5));

        mockServer.expect(requestTo(URL_ANALYZE))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Correlation-ID", "corr-ok"))
                .andExpect(jsonPath("$.umbral_aislamiento_m").value(120.0))
                .andExpect(jsonPath("$.historial_7d.length()").value(2))
                .andRespond(withSuccess(respuesta(
                        resultado("10", 0.95, 15.0, false, 0.0),
                        resultado("20", 0.90, 20.0, false, 0.0)), MediaType.APPLICATION_JSON));

        geoDetectorStrategy.process(List.of(t1, t2), "corr-ok");

        mockServer.verify();
        ArgumentCaptor<RegistroCohesion> registros = ArgumentCaptor.forClass(RegistroCohesion.class);
        verify(registroCohesionRepository, times(2)).save(registros.capture());
        RegistroCohesion r10 = registros.getAllValues().get(0);
        assertEquals(10L, r10.getAnimal().getId());
        assertSame(establecimiento, r10.getEstablecimiento());
        assertEquals(0.95, r10.getIndiceCohesion());
        assertEquals(0L, r10.getTiempoFueraGrupoS());
        assertEquals(15.0, r10.getDistanciaCentroideM());
        assertNotNull(r10.getFecha());

        assertEquals(EstadoInferencia.COMPLETADO, t1.getEstadoInferenciaGeo());
        assertEquals(EstadoInferencia.COMPLETADO, t2.getEstadoInferenciaGeo());
        verify(alertaRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Sin umbral configurado se envía el default de 100 m")
    void sinUmbralConfigurado_usaDefault() throws Exception {
        establecimiento.setUmbralAislamientoM(null);
        Telemetria t = telemetria(animal(10L, null), -34.5, -58.5, LocalDateTime.now());

        mockServer.expect(requestTo(URL_ANALYZE))
                .andExpect(jsonPath("$.umbral_aislamiento_m").value(100.0))
                .andRespond(withSuccess(respuesta(resultado("10", 1.0, 0.0, false, 0.0)), MediaType.APPLICATION_JSON));

        geoDetectorStrategy.process(List.of(t), "corr-default");

        mockServer.verify();
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Animal aislado: genera alerta AISLAMIENTO_SOCIAL con su última posición y la publica")
    void animalAislado_generaAlertaAislamientoSocialYPublicaRabbit() throws Exception {
        RodeoLote r = rodeo(100L);
        Animal a1 = animal(10L, r);
        Animal a2 = animal(20L, r);
        LocalDateTime ahora = LocalDateTime.now();
        Telemetria t1 = telemetria(a1, -34.500, -58.500, ahora.minusMinutes(10));
        Telemetria t2Vieja = telemetria(a2, -34.540, -58.540, ahora.minusMinutes(20));
        Telemetria t2 = telemetria(a2, -34.550, -58.550, ahora.minusMinutes(5));

        when(alertaRepository.save(any(Alerta.class))).thenAnswer(inv -> {
            Alerta a = inv.getArgument(0);
            a.setId(777L);
            return a;
        });

        mockServer.expect(requestTo(URL_ANALYZE))
                .andRespond(withSuccess(respuesta(
                        resultado("10", 0.95, 15.0, false, 0.0),
                        resultado("20", 0.20, 400.0, true, 1800.0)), MediaType.APPLICATION_JSON));

        geoDetectorStrategy.process(List.of(t1, t2Vieja, t2), "corr-alerta");

        mockServer.verify();

        ArgumentCaptor<Alerta> alertaCaptor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository).save(alertaCaptor.capture());
        Alerta alerta = alertaCaptor.getValue();
        assertSame(a2, alerta.getAnimal());
        assertEquals(DisparadorAlerta.AISLAMIENTO_SOCIAL, alerta.getDisparador());
        assertEquals("AISLAMIENTO_SOCIAL", alerta.getTipo());
        assertEquals(SeveridadAlerta.ALTA, alerta.getSeveridad());
        assertEquals(EstadoAlerta.PENDIENTE, alerta.getEstado());
        assertEquals(120.0, alerta.getValorUmbral());
        assertEquals(400.0, alerta.getValorDetectado());
        assertEquals(-34.550, alerta.getLatitud());
        assertEquals(-58.550, alerta.getLongitud());
        assertNull(alerta.getConfianzaModelo());

        ArgumentCaptor<AlertaEventDTO> evento = ArgumentCaptor.forClass(AlertaEventDTO.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.ALERTAS_EXCHANGE), eq(""), evento.capture());
        assertEquals(777L, evento.getValue().getId());
        assertEquals(20L, evento.getValue().getAnimalId());
        assertEquals(DisparadorAlerta.AISLAMIENTO_SOCIAL, evento.getValue().getDisparador());

        ArgumentCaptor<RegistroCohesion> registros = ArgumentCaptor.forClass(RegistroCohesion.class);
        verify(registroCohesionRepository, times(2)).save(registros.capture());
        assertEquals(1800L, registros.getAllValues().get(1).getTiempoFueraGrupoS());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Alerta de aislamiento pendiente: actualiza posición y distancia sin duplicar ni publicar")
    void alertaPendiente_actualizaPosicionYNoDuplica() throws Exception {
        Animal a1 = animal(10L, null);
        Telemetria t1 = telemetria(a1, -34.600, -58.600, LocalDateTime.now());

        Alerta existente = new Alerta();
        existente.setId(888L);
        existente.setAnimal(a1);
        existente.setDisparador(DisparadorAlerta.AISLAMIENTO_SOCIAL);
        existente.setEstado(EstadoAlerta.PENDIENTE);
        existente.setLatitud(-34.500);
        existente.setLongitud(-58.500);
        when(alertaRepository.alertasAnimalActivas(10L)).thenReturn(List.of(existente));

        mockServer.expect(requestTo(URL_ANALYZE))
                .andRespond(withSuccess(respuesta(resultado("10", 0.15, 550.0, true, 3600.0)),
                        MediaType.APPLICATION_JSON));

        geoDetectorStrategy.process(List.of(t1), "corr-update");

        mockServer.verify();
        verify(alertaRepository).save(existente);
        assertEquals(-34.600, existente.getLatitud());
        assertEquals(-58.600, existente.getLongitud());
        assertEquals(550.0, existente.getValorDetectado());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Alerta de aislamiento resuelta dentro del cooldown: no vuelve a alertar")
    void alertaResueltaEnCooldown_noGeneraNueva() throws Exception {
        Animal a1 = animal(10L, null);
        Telemetria t1 = telemetria(a1, -34.6, -58.6, LocalDateTime.now());

        Alerta descartada = new Alerta();
        descartada.setEstado(EstadoAlerta.DESCARTADA);
        when(alertaRepository.findFirstByAnimalAndDisparadorAndTsGeneracionAfterOrderByTsGeneracionDesc(
                eq(a1), eq(DisparadorAlerta.AISLAMIENTO_SOCIAL), any(LocalDateTime.class)))
                .thenReturn(Optional.of(descartada));

        mockServer.expect(requestTo(URL_ANALYZE))
                .andRespond(withSuccess(respuesta(resultado("10", 0.15, 550.0, true, 3600.0)),
                        MediaType.APPLICATION_JSON));

        geoDetectorStrategy.process(List.of(t1), "corr-cooldown");

        verify(alertaRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Cada rodeo se analiza contra su propio grupo: una llamada a ml-geo por rodeo")
    void dosRodeos_unaLlamadaPorRodeo() throws Exception {
        RodeoLote norte = rodeo(1L);
        RodeoLote sur = rodeo(2L);
        LocalDateTime ahora = LocalDateTime.now();
        Telemetria n1 = telemetria(animal(10L, norte), -34.50, -58.50, ahora);
        Telemetria n2 = telemetria(animal(11L, norte), -34.50, -58.50, ahora);
        Telemetria s1 = telemetria(animal(20L, sur), -34.90, -58.90, ahora);

        mockServer.expect(requestTo(URL_ANALYZE))
                .andExpect(jsonPath("$.historial_7d[*].animal_id").value(org.hamcrest.Matchers.containsInAnyOrder("10", "11")))
                .andRespond(withSuccess(respuesta(
                        resultado("10", 1.0, 0.0, false, 0.0),
                        resultado("11", 1.0, 0.0, false, 0.0)), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(URL_ANALYZE))
                .andExpect(jsonPath("$.historial_7d[*].animal_id").value(org.hamcrest.Matchers.contains("20")))
                .andRespond(withSuccess(respuesta(resultado("20", 1.0, 0.0, false, 0.0)), MediaType.APPLICATION_JSON));

        geoDetectorStrategy.process(List.of(n1, s1, n2), "corr-rodeos");

        mockServer.verify();
        verify(registroCohesionRepository, times(3)).save(any());
        verify(alertaRepository, never()).save(any());
    }

    @Test
    @DisplayName("ml-geo caído: marca la telemetría como INFERENCIA_PENDIENTE sin propagar la excepción")
    void mlGeoCaido_marcaInferenciaPendiente() {
        Telemetria t1 = telemetria(animal(10L, null), -34.5, -58.5, LocalDateTime.now());

        mockServer.expect(requestTo(URL_ANALYZE)).andRespond(withServerError());

        assertDoesNotThrow(() -> geoDetectorStrategy.process(List.of(t1), "corr-caido"));

        mockServer.verify();
        assertEquals(EstadoInferencia.INFERENCIA_PENDIENTE, t1.getEstadoInferenciaGeo());
        verify(telemetriaRepository).saveAll(anyList());
        verifyNoInteractions(registroCohesionRepository, rabbitTemplate);
        verify(alertaRepository, never()).save(any());
    }
}
