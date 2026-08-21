package dev.vigia.backend.config;

import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.CaravanaRepository;
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
public class CeloBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CaravanaRepository caravanaRepository;
    private final TelemetriaRepository telemetriaRepository;
    private final DetectorStrategy celoDetectorStrategy;

    public CeloBatchConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            CaravanaRepository caravanaRepository, TelemetriaRepository telemetriaRepository,
            @Qualifier("celoDetectorStrategyImpl") DetectorStrategy celoDetectorStrategy) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.caravanaRepository = caravanaRepository;
        this.telemetriaRepository = telemetriaRepository;
        this.celoDetectorStrategy = celoDetectorStrategy;
    }

    @Bean
    @SuppressWarnings("null")
    public RepositoryItemReader<Caravana> caravanaItemReader() {
        return new RepositoryItemReaderBuilder<Caravana>()
                .name("caravanaItemReader")
                .repository(caravanaRepository)
                .methodName("findByActivoTrue")
                .sorts(Collections.singletonMap("id", Sort.Direction.ASC))
                .build();
    }

    @Bean
    public ItemProcessor<Caravana, Caravana> celoProcessor() {
        return caravana -> {
            LocalDateTime hace24h = LocalDateTime.now().minusHours(24);
            List<Telemetria> telemetriaPendiente = telemetriaRepository
                    .findByCaravanaAndEstadoInferenciaCeloAndFechaHoraAfter(
                            caravana, EstadoInferencia.PENDIENTE, hace24h);

            if (!telemetriaPendiente.isEmpty()) {
                String correlationId = UUID.randomUUID().toString();
                List<Telemetria> todaLaTelemetria = telemetriaRepository
                        .findByCaravanaAndFechaHoraAfterOrderByFechaHoraAsc(caravana, hace24h);
                celoDetectorStrategy.process(todaLaTelemetria, correlationId);
            }
            return caravana;
        };
    }

    @Bean
    public ItemWriter<Caravana> celoWriter() {
        return items -> {
            // El guardado del estado de telemetría lo maneja CeloDetectorStrategyImpl.
            // Aquí podríamos persistir métricas adicionales de procesamiento si fuera
            // necesario.
        };
    }

    @Bean
    @SuppressWarnings("null")
    public Step celoStep() {
        return new StepBuilder("celoStep", jobRepository)
                .<Caravana, Caravana>chunk(10, transactionManager)
                .reader(caravanaItemReader())
                .processor(celoProcessor())
                .writer(celoWriter())
                .build();
    }

    @Bean
    @SuppressWarnings("null")
    public Job celoJob() {
        return new JobBuilder("celoJob", jobRepository)
                .start(celoStep())
                .build();
    }
}
