package dev.vigia.backend.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

class BatchSchedulerTest {

    private final JobLauncher jobLauncher = mock(JobLauncher.class);
    private final Job celoJob = mock(Job.class);
    private final Job saludJob = mock(Job.class);
    private final Job geoJob = mock(Job.class);
    private final BatchScheduler scheduler = new BatchScheduler(jobLauncher, celoJob, saludJob, geoJob);

    @Test
    @SuppressWarnings("null")
    void saludYGeoEsperanQueTermineCelo_paraNoActualizarLaMismaTelemetriaALaVez() throws Exception {
        // En una hypertable de TimescaleDB, el segundo de dos UPDATE simultáneos sobre la misma fila espera y
        // después no actualiza nada (UPDATE 0): el chunk falla y el job se cae. Los tres escriben su
        // estado_inferencia_* en las mismas filas, así que no pueden correr a la vez.
        CountDownLatch celoCorriendo = new CountDownLatch(1);
        CountDownLatch terminarCelo = new CountDownLatch(1);
        when(jobLauncher.run(eq(celoJob), any(JobParameters.class))).thenAnswer(invocacion -> {
            celoCorriendo.countDown();
            terminarCelo.await(5, TimeUnit.SECONDS);
            return null;
        });
        ExecutorService hilos = Executors.newFixedThreadPool(3);
        try {
            hilos.submit(scheduler::runCeloJob);
            assertTrue(celoCorriendo.await(5, TimeUnit.SECONDS));

            hilos.submit(scheduler::runSaludJob);
            hilos.submit(scheduler::runGeoJob);
            verify(jobLauncher, after(500).never()).run(eq(saludJob), any(JobParameters.class));
            verify(jobLauncher, never()).run(eq(geoJob), any(JobParameters.class));

            terminarCelo.countDown();
            verify(jobLauncher, timeout(5000)).run(eq(saludJob), any(JobParameters.class));
            verify(jobLauncher, timeout(5000)).run(eq(geoJob), any(JobParameters.class));
        } finally {
            terminarCelo.countDown();
            hilos.shutdownNow();
        }
    }
}
