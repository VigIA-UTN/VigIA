package dev.vigia.backend.config;

import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.service.ml.DetectorStrategy;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Cálculo periódico de cohesión y aislamiento social: por cada establecimiento
 * activo con telemetría nueva, manda a ml-geo las posiciones de la ventana
 * configurada (ver GeoDetectorStrategyImpl).
 */
@Configuration
public class GeoBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EstablecimientoRepository establecimientoRepository;
    private final TelemetriaRepository telemetriaRepository;
    private final DetectorStrategy geoDetectorStrategy;
    private final int ventanaHoras;

    public GeoBatchConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            EstablecimientoRepository establecimientoRepository,
            TelemetriaRepository telemetriaRepository,
            @Qualifier("geoDetectorStrategyImpl") DetectorStrategy geoDetectorStrategy,
            @Value("${vigia.geo.ventana-horas:24}") int ventanaHoras) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.establecimientoRepository = establecimientoRepository;
        this.telemetriaRepository = telemetriaRepository;
        this.geoDetectorStrategy = geoDetectorStrategy;
        this.ventanaHoras = ventanaHoras;
    }

    @Bean
    @SuppressWarnings("null")
    public RepositoryItemReader<Establecimiento> establecimientoItemReaderGeo() {
        return new RepositoryItemReaderBuilder<Establecimiento>()
                .name("establecimientoItemReaderGeo")
                .repository(establecimientoRepository)
                .methodName("findAll")
                .sorts(Collections.singletonMap("id", Sort.Direction.ASC))
                .build();
    }

    @Bean
    public ItemProcessor<Establecimiento, Establecimiento> geoProcessor() {
        return establecimiento -> {
            if (!establecimiento.isActivo()) {
                return establecimiento;
            }

            LocalDateTime hasta = LocalDateTime.now();
            LocalDateTime desde = hasta.minusHours(ventanaHoras);

            // Solo se consulta a ml-geo si llegó telemetría desde el último análisis.
            if (!telemetriaRepository.existsPendientesGeoByEstablecimiento(
                    establecimiento.getId(), EstadoInferencia.PENDIENTE, desde)) {
                return establecimiento;
            }

            List<Telemetria> ventana = telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(
                    establecimiento.getId(), desde, hasta, null);
            geoDetectorStrategy.process(ventana, UUID.randomUUID().toString());
            return establecimiento;
        };
    }

    @Bean
    public ItemWriter<Establecimiento> geoWriter() {
        return items -> {
            // El guardado del estado de telemetría, los registros de cohesión y las
            // alertas lo maneja GeoDetectorStrategyImpl.
        };
    }

    @Bean
    @SuppressWarnings("null")
    public Step geoStep() {
        return new StepBuilder("geoStep", jobRepository)
                .<Establecimiento, Establecimiento>chunk(10, transactionManager)
                .reader(establecimientoItemReaderGeo())
                .processor(geoProcessor())
                .writer(geoWriter())
                .build();
    }

    @Bean
    @SuppressWarnings("null")
    public Job geoJob() {
        return new JobBuilder("geoJob", jobRepository)
                .start(geoStep())
                .build();
    }
}
