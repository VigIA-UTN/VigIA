package dev.vigia.backend.service.ml;

import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.PerfilReferenciaRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {"spring.batch.job.enabled=false"})
class SaludDetectorStrategyImplTest {

    @MockBean
    private ConnectionFactory connectionFactory;

    @Autowired
    private SaludDetectorStrategyImpl saludDetectorStrategy;

    @MockBean
    private TelemetriaRepository telemetriaRepository;

    @MockBean
    private AlertaRepository alertaRepository;

    @MockBean
    private PerfilReferenciaRepository perfilReferenciaRepository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

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

    @Test
    @SuppressWarnings("null")
    void testFallbackProcess() {
        Telemetria t1 = new Telemetria();
        t1.setEstadoInferenciaSalud(EstadoInferencia.PENDIENTE);

        List<Telemetria> lista = Arrays.asList(t1);

        saludDetectorStrategy.fallbackProcess(lista, "12345", new RuntimeException("Timeout"));

        assertEquals(EstadoInferencia.INFERENCIA_PENDIENTE, t1.getEstadoInferenciaSalud());
        verify(telemetriaRepository).saveAll(anyList());
    }

    @Test
    @SuppressWarnings("null")
    void testProcess_animalEnCalibracion_noPredicionNiAlerta() {
        // RF03: un animal dado de alta hoy todavía está calibrando (los 7
        // días no pasaron), así que process() no debe intentar predecir
        // ni consultar/generar alertas -- solo marcar la telemetría
        // pendiente como completada, para reintentar cuando corresponda.
        Animal animal = new Animal();
        animal.setId(1L);
        animal.setFechaAlta(LocalDate.now());

        Telemetria t = telemetriaDeAnimal(animal);
        List<Telemetria> lista = Arrays.asList(t);

        saludDetectorStrategy.process(lista, "corr-calib-1");

        assertEquals(EstadoInferencia.COMPLETADO, t.getEstadoInferenciaSalud());
        verify(telemetriaRepository).saveAll(anyList());
        verify(alertaRepository, never()).save(any());
        verify(perfilReferenciaRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void testProcess_sinFechaAlta_noRompeYMarcaCompletado() {
        Animal animal = new Animal();
        animal.setId(2L);
        animal.setFechaAlta(null);

        Telemetria t = telemetriaDeAnimal(animal);
        List<Telemetria> lista = Arrays.asList(t);

        saludDetectorStrategy.process(lista, "corr-sin-alta");

        assertEquals(EstadoInferencia.COMPLETADO, t.getEstadoInferenciaSalud());
    }
}
