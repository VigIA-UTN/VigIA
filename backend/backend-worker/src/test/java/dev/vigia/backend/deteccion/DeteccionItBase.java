package dev.vigia.backend.deteccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.IntegracionBase;
import dev.vigia.backend.fixture.EscenarioFixture;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base de los IT de los jobs de detección (regresión E2): cada test parte del escenario de {@code fixtures/escenario}
 * recién cargado y con los microservicios de ML simulados ({@link MlSimulado}). En el escenario toda la telemetría
 * está con las inferencias en COMPLETADO, así que un job solo procesa lo que el test deja pendiente.
 * <p>
 * Los nombres de los tests llevan la HU y el número de caso de la hoja "Ciclo 1" del documento de Casos de Prueba
 * (por ejemplo {@code H0008-01}), para poder cruzar el resultado de la corrida con la planilla.
 */
abstract class DeteccionItBase extends IntegracionBase {

    protected static final String EST_A = "Fixture A";
    protected static final String EST_B = "Fixture B";

    private static final Set<String> PIPELINES = Set.of("celo", "salud", "geo");

    protected static final MlSimulado ML = new MlSimulado();

    @DynamicPropertySource
    static void urlsDeMl(DynamicPropertyRegistry registry) {
        registry.add("vigia.ml.celo-url", ML::url);
        registry.add("vigia.ml.salud-url", ML::url);
        registry.add("vigia.ml.geo-url", ML::url);
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JobLauncher jobLauncher;

    @BeforeEach
    void prepararEscenario() {
        EscenarioFixture.cargar(dataSource);
        ML.reiniciar();
    }

    /** Una corrida del job, como la que lanza BatchScheduler; tiene que terminar COMPLETED. */
    protected void correr(Job job) throws Exception {
        JobExecution ejecucion = jobLauncher.run(job, new JobParametersBuilder()
                .addLong("time", System.nanoTime())
                .toJobParameters());
        assertThat(ejecucion.getStatus()).as("estado del %s", job.getName()).isEqualTo(BatchStatus.COMPLETED);
    }

    // ── Datos del escenario ──────────────────────────────────────────────────

    protected Long idAnimal(String establecimiento, String identificador) {
        return jdbcTemplate.queryForObject("SELECT a.id FROM animales a JOIN establecimientos e "
                + "ON e.id = a.establecimiento_id WHERE e.nombre = ? AND a.id_caravana_oficial = ?",
                Long.class, establecimiento, identificador);
    }

    protected Long idCaravana(Long animalId) {
        return jdbcTemplate.queryForObject("SELECT caravana_id FROM animales WHERE id = ?", Long.class, animalId);
    }

    // ── Telemetría ───────────────────────────────────────────────────────────

    /** La última lectura del animal queda pendiente para el pipeline (celo, salud o geo), como recién llegada. */
    protected void marcarUltimaLecturaPendiente(Long animalId, String pipeline) {
        Long caravana = idCaravana(animalId);
        jdbcTemplate.update("UPDATE telemetria SET " + columnaInferencia(pipeline) + " = 'PENDIENTE' "
                + "WHERE caravana_id = ? AND fecha_hora = (SELECT max(fecha_hora) FROM telemetria WHERE caravana_id = ?)",
                caravana, caravana);
    }

    /**
     * Una lectura nueva del animal en este momento, pendiente para los tres pipelines: misma posición y moveState que
     * la anterior.
     */
    protected void insertarLecturaNueva(Long animalId) {
        Long caravana = idCaravana(animalId);
        jdbcTemplate.update("INSERT INTO telemetria (fecha_hora, caravana_id, latitud, longitud, move_state, bateria, "
                + "estado_inferencia_celo, estado_inferencia_salud, estado_inferencia_geo) "
                + "SELECT ?, caravana_id, latitud, longitud, move_state, bateria, 'PENDIENTE', 'PENDIENTE', 'PENDIENTE' "
                + "FROM telemetria WHERE caravana_id = ? ORDER BY fecha_hora DESC LIMIT 1",
                Timestamp.valueOf(LocalDateTime.now()), caravana);
    }

    /** Lecturas del animal que el pipeline todavía no dio por evaluadas (PENDIENTE o INFERENCIA_PENDIENTE). */
    protected int lecturasSinEvaluar(Long animalId, String pipeline) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM telemetria WHERE caravana_id = ? AND "
                + columnaInferencia(pipeline) + " <> 'COMPLETADO'", Integer.class, idCaravana(animalId));
    }

    // ── Alertas ──────────────────────────────────────────────────────────────

    /** Alertas del animal con ese disparador, de la más vieja a la más nueva. */
    protected List<Map<String, Object>> alertas(Long animalId, String disparador) {
        return jdbcTemplate.queryForList("SELECT * FROM alertas WHERE animal_id = ? AND disparador = ? ORDER BY id",
                animalId, disparador);
    }

    // ── JSON ────────────────────────────────────────────────────────────────

    protected JsonNode json(String texto) throws Exception {
        return objectMapper.readTree(texto);
    }

    private static String columnaInferencia(String pipeline) {
        if (!PIPELINES.contains(pipeline)) {
            throw new IllegalArgumentException("Pipeline desconocido: " + pipeline);
        }
        return "estado_inferencia_" + pipeline;
    }
}
