package dev.vigia.backend.service.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.ml.SaludPredictResponseDTO;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.PerfilReferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.PerfilReferenciaRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
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
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    private MockRestServiceServer mockServer;
    private SaludDetectorStrategyImpl saludDetectorStrategy;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        saludDetectorStrategy = new SaludDetectorStrategyImpl(
                builder,
                "http://localhost:8000",
                telemetriaRepository,
                alertaRepository,
                perfilReferenciaRepository,
                rabbitTemplate,
                objectMapper,
                7,
                30);
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
            t.setMoveState(3);
            t.setLatitud(-34.0);
            t.setLongitud(-60.0);
            t.setFechaHora(base.plusMinutes(5L * i));
            lista.add(t);
        }
        return lista;
    }

    @SuppressWarnings("null")
    @Test
    void testFallbackProcess() {
        Telemetria t1 = new Telemetria();
        t1.setEstadoInferenciaSalud(EstadoInferencia.PENDIENTE);

        List<Telemetria> lista = List.of(t1);

        saludDetectorStrategy.fallbackProcess(lista, "12345", new RuntimeException("Timeout"));

        assertEquals(EstadoInferencia.INFERENCIA_PENDIENTE, t1.getEstadoInferenciaSalud());
        verify(telemetriaRepository).saveAll(anyList());
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_animalEnCalibracion_noPredicionNiAlerta() {
        Animal animal = new Animal();
        animal.setId(1L);
        animal.setFechaAlta(LocalDate.now());

        Telemetria t = telemetriaDeAnimal(animal);
        List<Telemetria> lista = List.of(t);

        saludDetectorStrategy.process(lista, "corr-calib-1");

        assertEquals(EstadoInferencia.COMPLETADO, t.getEstadoInferenciaSalud());
        verify(telemetriaRepository).saveAll(anyList());
        verify(alertaRepository, never()).save(any());
        verify(perfilReferenciaRepository, never()).save(any());

        // No interaction with mock server means no HTTP calls were made
    }

    @SuppressWarnings("null")
    @Test
    void testProcess_sinFechaAlta_noRompeYMarcaCompletado() {
        Animal animal = new Animal();
        animal.setId(2L);
        animal.setFechaAlta(null);

        Telemetria t = telemetriaDeAnimal(animal);
        List<Telemetria> lista = List.of(t);

        saludDetectorStrategy.process(lista, "corr-sin-alta");

        assertEquals(EstadoInferencia.COMPLETADO, t.getEstadoInferenciaSalud());
        verify(telemetriaRepository).saveAll(anyList());
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
        verify(rabbitTemplate).convertAndSend(anyString(), eq(""), any(dev.vigia.backend.dto.ml.AlertaEventDTO.class));

        // La telemetría procesada se marca como COMPLETADA
        assertEquals(EstadoInferencia.COMPLETADO, lista.get(0).getEstadoInferenciaSalud());
        verify(telemetriaRepository).saveAll(anyList());
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
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));

        assertEquals(EstadoInferencia.COMPLETADO, lista.get(0).getEstadoInferenciaSalud());
        verify(telemetriaRepository).saveAll(anyList());
    }
}
