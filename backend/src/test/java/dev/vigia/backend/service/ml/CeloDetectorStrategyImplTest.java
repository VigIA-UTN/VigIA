package dev.vigia.backend.service.ml;

import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.TelemetriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

@SpringBootTest
class CeloDetectorStrategyImplTest {

    @Autowired
    private CeloDetectorStrategyImpl celoDetectorStrategy;

    @MockBean
    private TelemetriaRepository telemetriaRepository;

    @Test
    @SuppressWarnings("null")
    void testFallbackProcess() {
        Telemetria t1 = new Telemetria();
        t1.setEstadoInferencia(EstadoInferencia.PENDIENTE);

        List<Telemetria> lista = Arrays.asList(t1);

        celoDetectorStrategy.fallbackProcess(lista, "12345", new RuntimeException("Timeout"));

        assertEquals(EstadoInferencia.INFERENCIA_PENDIENTE, t1.getEstadoInferencia());
        verify(telemetriaRepository).saveAll(anyList());
    }
}
