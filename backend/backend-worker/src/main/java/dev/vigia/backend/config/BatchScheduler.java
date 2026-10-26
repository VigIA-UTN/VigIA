package dev.vigia.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Celo, salud y geo corren de a uno (métodos synchronized: comparten el lock de este bean), aunque el scheduler tenga
 * un hilo por tarea. Los tres escriben su estado_inferencia_* en las mismas filas de telemetría, y en una hypertable
 * de TimescaleDB el segundo de dos UPDATE simultáneos sobre una fila espera y después no actualiza nada (UPDATE 0): el
 * chunk falla y el job se cae. Lanzarlos a la vez también choca en las tablas de Spring Batch. Anestro y el archivador
 * no tocan telemetría y corren en sus propios hilos, sin esperar a estos.
 */
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
    public synchronized void runCeloJob() {
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
    public synchronized void runSaludJob() {
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
    public synchronized void runGeoJob() {
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
