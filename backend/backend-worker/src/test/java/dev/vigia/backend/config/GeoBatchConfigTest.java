package dev.vigia.backend.config;

import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.service.ml.DetectorStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeoBatchConfig - Pruebas Unitarias")
class GeoBatchConfigTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private EstablecimientoRepository establecimientoRepository;

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
                establecimientoRepository,
                telemetriaRepository,
                geoDetectorStrategy,
                24);
    }

    private Establecimiento establecimiento(Long id) {
        Establecimiento est = new Establecimiento();
        est.setId(id);
        return est;
    }

    @Test
    @DisplayName("Sin telemetría nueva no consulta la ventana ni invoca a la strategy")
    void sinTelemetriaPendiente_noInvocaStrategy() throws Exception {
        Establecimiento est = establecimiento(5L);
        when(telemetriaRepository.existsPendientesGeoByEstablecimiento(
                eq(5L), eq(EstadoInferencia.PENDIENTE), any(LocalDateTime.class)))
                .thenReturn(false);

        Establecimiento result = geoBatchConfig.geoProcessor().process(est);

        assertSame(est, result);
        verify(telemetriaRepository, never()).findByEstablecimientoAndFechaHoraBetween(any(), any(), any(), any());
        verifyNoInteractions(geoDetectorStrategy);
    }

    @Test
    @DisplayName("Con telemetría nueva manda a la strategy toda la ventana configurada del establecimiento")
    void conTelemetriaPendiente_invocaStrategyConLaVentana() throws Exception {
        Establecimiento est = establecimiento(7L);
        Telemetria t1 = new Telemetria();
        Telemetria t2 = new Telemetria();

        when(telemetriaRepository.existsPendientesGeoByEstablecimiento(
                eq(7L), eq(EstadoInferencia.PENDIENTE), any(LocalDateTime.class)))
                .thenReturn(true);
        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(
                eq(7L), any(LocalDateTime.class), any(LocalDateTime.class), isNull()))
                .thenReturn(List.of(t1, t2));

        geoBatchConfig.geoProcessor().process(est);

        verify(geoDetectorStrategy).process(eq(List.of(t1, t2)), anyString());

        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> hasta = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(telemetriaRepository).findByEstablecimientoAndFechaHoraBetween(
                eq(7L), desde.capture(), hasta.capture(), isNull());
        assertEquals(Duration.ofHours(24), Duration.between(desde.getValue(), hasta.getValue()));
    }

    @Test
    @DisplayName("Bloquea el establecimiento antes de ver si hay telemetría pendiente (varias réplicas del worker)")
    void bloqueaElEstablecimientoAntesDeBuscarPendientes() throws Exception {
        Establecimiento est = establecimiento(5L);
        when(telemetriaRepository.existsPendientesGeoByEstablecimiento(
                eq(5L), eq(EstadoInferencia.PENDIENTE), any(LocalDateTime.class)))
                .thenReturn(false);

        geoBatchConfig.geoProcessor().process(est);

        InOrder orden = inOrder(establecimientoRepository, telemetriaRepository);
        orden.verify(establecimientoRepository).bloquear(5L);
        orden.verify(telemetriaRepository).existsPendientesGeoByEstablecimiento(
                eq(5L), eq(EstadoInferencia.PENDIENTE), any(LocalDateTime.class));
        verifyNoInteractions(geoDetectorStrategy);
    }

    @Test
    @DisplayName("Establecimiento inactivo: se omite sin consultar telemetría")
    void establecimientoInactivo_seOmite() throws Exception {
        Establecimiento est = establecimiento(9L);
        est.setActivo(false);

        ItemProcessor<Establecimiento, Establecimiento> processor = geoBatchConfig.geoProcessor();
        processor.process(est);

        verifyNoInteractions(telemetriaRepository, geoDetectorStrategy);
    }

    @Test
    void geoStepAndJob_seConstruyenCorrectamente() {
        assertNotNull(geoBatchConfig.establecimientoItemReaderGeo());

        Step step = geoBatchConfig.geoStep();
        assertEquals("geoStep", step.getName());

        Job job = geoBatchConfig.geoJob();
        assertEquals("geoJob", job.getName());
    }
}
