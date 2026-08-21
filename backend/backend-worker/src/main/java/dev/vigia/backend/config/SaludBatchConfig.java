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

/**
 * Job de batch para el modelo de salud. Estructuralmente igual a
 * {@link CeloBatchConfig} (mismo patrón de reader/processor/writer/step/
 * job), pero la ventana de telemetría que se junta y se pasa al
 * detector es la MISMA independientemente de si el animal está
 * calibrando, prediciendo, o recalibrando -- esa decisión la toma
 * {@code SaludDetectorStrategyImpl} internamente, no este config. Acá
 * solo se decide CUÁNDO vale la pena llamar al detector (hay
 * telemetría pendiente en las últimas 24hs), igual que celo.
 */
@Configuration
public class SaludBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CaravanaRepository caravanaRepository;
    private final TelemetriaRepository telemetriaRepository;
    private final DetectorStrategy saludDetectorStrategy;

    public SaludBatchConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            CaravanaRepository caravanaRepository, TelemetriaRepository telemetriaRepository,
            @Qualifier("saludDetectorStrategyImpl") DetectorStrategy saludDetectorStrategy) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.caravanaRepository = caravanaRepository;
        this.telemetriaRepository = telemetriaRepository;
        this.saludDetectorStrategy = saludDetectorStrategy;
    }

    @Bean
    @SuppressWarnings("null")
    public RepositoryItemReader<Caravana> caravanaItemReaderSalud() {
        return new RepositoryItemReaderBuilder<Caravana>()
                .name("caravanaItemReaderSalud")
                .repository(caravanaRepository)
                .methodName("findByActivoTrue")
                .sorts(Collections.singletonMap("id", Sort.Direction.ASC))
                .build();
    }

    @Bean
    public ItemProcessor<Caravana, Caravana> saludProcessor() {
        return caravana -> {
            LocalDateTime hace24h = LocalDateTime.now().minusHours(24);
            List<Telemetria> telemetriaPendiente = telemetriaRepository
                    .findByCaravanaAndEstadoInferenciaAndFechaHoraAfter(
                            caravana, EstadoInferencia.PENDIENTE, hace24h);

            if (!telemetriaPendiente.isEmpty()) {
                String correlationId = UUID.randomUUID().toString();
                List<Telemetria> todaLaTelemetria = telemetriaRepository
                        .findByCaravanaAndFechaHoraAfterOrderByFechaHoraAsc(caravana, hace24h);
                saludDetectorStrategy.process(todaLaTelemetria, correlationId);
            }
            return caravana;
        };
    }

    @Bean
    public ItemWriter<Caravana> saludWriter() {
        return items -> {
            // El guardado del estado de telemetría y del perfil lo maneja
            // SaludDetectorStrategyImpl.
        };
    }

    @Bean
    @SuppressWarnings("null")
    public Step saludStep() {
        return new StepBuilder("saludStep", jobRepository)
                .<Caravana, Caravana>chunk(10, transactionManager)
                .reader(caravanaItemReaderSalud())
                .processor(saludProcessor())
                .writer(saludWriter())
                .build();
    }

    @Bean
    @SuppressWarnings("null")
    public Job saludJob() {
        return new JobBuilder("saludJob", jobRepository)
                .start(saludStep())
                .build();
    }
}
