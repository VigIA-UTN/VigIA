package dev.vigia.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job celoJob;
    private final Job saludJob;
    private final Job geoJob;

    @Scheduled(fixedRateString = "${vigia.batch.celo-rate:60000}")
    @SuppressWarnings("null")
    public void runCeloJob() {
        try {
            log.info("Iniciando ejecución programada del Job de Celo");
            JobParameters parameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(celoJob, parameters);
        } catch (Exception e) {
            log.error("Error al ejecutar el Job de Celo: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRateString = "${vigia.batch.salud-rate:60000}")
    @SuppressWarnings("null")
    public void runSaludJob() {
        try {
            log.info("Iniciando ejecución programada del Job de Salud");
            JobParameters parameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(saludJob, parameters);
        } catch (Exception e) {
            log.error("Error al ejecutar el Job de Salud: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRateString = "${vigia.batch.geo-rate:900000}")
    @SuppressWarnings("null")
    public void runGeoJob() {
        try {
            log.info("Iniciando ejecución programada del Job de Geo");
            JobParameters parameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(geoJob, parameters);
        } catch (Exception e) {
            log.error("Error al ejecutar el Job de Geo: {}", e.getMessage(), e);
        }
    }
}
