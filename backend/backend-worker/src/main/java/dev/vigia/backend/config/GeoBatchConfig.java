package dev.vigia.backend.config;

import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.RodeoLoteRepository;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Configuration
public class GeoBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final RodeoLoteRepository rodeoLoteRepository;
    private final TelemetriaRepository telemetriaRepository;
    private final DetectorStrategy geoDetectorStrategy;

    public GeoBatchConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RodeoLoteRepository rodeoLoteRepository,
            TelemetriaRepository telemetriaRepository,
            @Qualifier("geoDetectorStrategyImpl") DetectorStrategy geoDetectorStrategy) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.rodeoLoteRepository = rodeoLoteRepository;
        this.telemetriaRepository = telemetriaRepository;
        this.geoDetectorStrategy = geoDetectorStrategy;
    }

    @Bean
    @SuppressWarnings("null")
    public RepositoryItemReader<RodeoLote> rodeoItemReaderGeo() {
        return new RepositoryItemReaderBuilder<RodeoLote>()
                .name("rodeoItemReaderGeo")
                .repository(rodeoLoteRepository)
                .methodName("findByActivoTrue")
                .sorts(Collections.singletonMap("id", Sort.Direction.ASC))
                .build();
    }

    @Bean
    public ItemProcessor<RodeoLote, RodeoLote> geoProcessor() {
        return rodeoLote -> {
            LocalDateTime hace24h = LocalDateTime.now().minusHours(24);
            List<Telemetria> telemetriaPendiente = telemetriaRepository
                    .findPendientesGeoByRodeoLoteId(rodeoLote.getId(), EstadoInferencia.PENDIENTE, hace24h);

            if (!telemetriaPendiente.isEmpty()) {
                LocalDateTime hace7d = LocalDateTime.now().minusDays(7);
                List<Telemetria> todaLaTelemetria = telemetriaRepository
                        .findByRodeoLoteIdAndFechaHoraAfterOrderByFechaHoraAsc(rodeoLote.getId(), hace7d);
                String correlationId = UUID.randomUUID().toString();
                geoDetectorStrategy.process(todaLaTelemetria, correlationId);
            }
            return rodeoLote;
        };
    }

    @Bean
    public ItemWriter<RodeoLote> geoWriter() {
        return items -> {
            // El guardado del estado de telemetría y de las alertas lo maneja GeoDetectorStrategyImpl.
        };
    }

    @Bean
    @SuppressWarnings("null")
    public Step geoStep() {
        return new StepBuilder("geoStep", jobRepository)
                .<RodeoLote, RodeoLote>chunk(10, transactionManager)
                .reader(rodeoItemReaderGeo())
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
