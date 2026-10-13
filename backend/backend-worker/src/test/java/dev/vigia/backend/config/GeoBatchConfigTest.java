package dev.vigia.backend.config;

import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.service.ml.DetectorStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeoBatchConfig - Pruebas Unitarias")
class GeoBatchConfigTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private RodeoLoteRepository rodeoLoteRepository;

    @Mock
    private TelemetriaRepository telemetriaRepository;

    @Mock
    private DetectorStrategy geoDetectorStrategy;

    private GeoBatchConfig geoBatchConfig;

    @BeforeEach
    void setUp() {
        geoBatchConfig = new GeoBatchConfig(
                jobRepository,
                transactionManager,
                rodeoLoteRepository,
                telemetriaRepository,
                geoDetectorStrategy);
    }

    @Test
    void testRodeoItemReaderGeo_construyeReaderCorrectamente() {
        RepositoryItemReader<RodeoLote> reader = geoBatchConfig.rodeoItemReaderGeo();
        assertNotNull(reader);
    }

    @Test
    void testGeoProcessor_sinTelemetriaPendiente_noInvocaStrategy() throws Exception {
        RodeoLote rodeo = new RodeoLote();
        rodeo.setId(5L);

        when(telemetriaRepository.findPendientesGeoByRodeoLoteId(
                eq(5L), eq(EstadoInferencia.PENDIENTE), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        ItemProcessor<RodeoLote, RodeoLote> processor = geoBatchConfig.geoProcessor();
        RodeoLote result = processor.process(rodeo);

        assertEquals(rodeo, result);
        verify(geoDetectorStrategy, never()).process(anyList(), anyString());
    }

    @Test
    void testGeoProcessor_conTelemetriaPendiente_invocaStrategy() throws Exception {
        RodeoLote rodeo = new RodeoLote();
        rodeo.setId(7L);

        Telemetria tPendiente = new Telemetria();
        tPendiente.setId(101L);

        Telemetria tHistorial = new Telemetria();
        tHistorial.setId(102L);

        when(telemetriaRepository.findPendientesGeoByRodeoLoteId(
                eq(7L), eq(EstadoInferencia.PENDIENTE), any(LocalDateTime.class)))
                .thenReturn(List.of(tPendiente));

        when(telemetriaRepository.findByRodeoLoteIdAndFechaHoraAfterOrderByFechaHoraAsc(
                eq(7L), any(LocalDateTime.class)))
                .thenReturn(List.of(tPendiente, tHistorial));

        ItemProcessor<RodeoLote, RodeoLote> processor = geoBatchConfig.geoProcessor();
        RodeoLote result = processor.process(rodeo);

        assertEquals(rodeo, result);
        verify(geoDetectorStrategy).process(eq(List.of(tPendiente, tHistorial)), anyString());
    }

    @Test
    void testGeoStepAndJob_seConstruyenCorrectamente() {
        Step step = geoBatchConfig.geoStep();
        assertNotNull(step);
        assertEquals("geoStep", step.getName());

        Job job = geoBatchConfig.geoJob();
        assertNotNull(job);
        assertEquals("geoJob", job.getName());
    }
}
