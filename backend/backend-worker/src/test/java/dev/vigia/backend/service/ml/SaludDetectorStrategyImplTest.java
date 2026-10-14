package dev.vigia.backend.service.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.client.MlSaludClient;
import dev.vigia.backend.dto.SerieComparativaSnapshot;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
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
