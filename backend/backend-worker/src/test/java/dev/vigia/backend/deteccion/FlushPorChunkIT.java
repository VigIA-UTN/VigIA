package dev.vigia.backend.deteccion;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.Job;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RNF01 a 25.000 animales (defecto del Ciclo 1, fila 433): un chunk de celo o de salud junta la telemetría de 24 h de
 * 10 caravanas (~2.900 entidades). Con el flush automático de Hibernate, cada consulta de la caravana siguiente
 * revisaba todas esas entidades y mandaba los UPDATE de la anterior: ~2,9 ms por caravana y pasada, un 14 % de la
 * pasada de salud. Las consultas de cada caravana no leen lo que escribieron las otras, así que alcanza con un flush
 * por chunk, al commitear.
 */
@DisplayName("RNF01 - Un flush por chunk en los jobs de celo y salud — IT")
class FlushPorChunkIT extends SaludItBase {

    @Autowired
    @Qualifier("celoJob")
    private Job celoJob;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Celo: dos caravanas con lecturas pendientes en el mismo chunk, sin flush entre ellas")
    void celo() throws Exception {
        ML.responder(MlSimulado.CELO_PREDICT,
                "{\"evento_celo\": false, \"confianza_modelo\": 0.12, \"ventana_optima_horas\": 0}");
        Long a101 = idAnimal(EST_A, "A-101");
        Long a102 = idAnimal(EST_A, "A-102"); // con alerta de celo reciente: se marca sin llamar a ml-celo
        marcarUltimaLecturaPendiente(a101, "celo");
        marcarUltimaLecturaPendiente(a102, "celo");

        long flushes = flushes(celoJob);

        assertThat(lecturasSinEvaluar(a101, "celo") + lecturasSinEvaluar(a102, "celo")).isZero();
        assertThat(flushes).isLessThanOrEqualTo(commits("celoStep"));
    }

    @Test
    @DisplayName("Salud: dos caravanas con lecturas pendientes en el mismo chunk, sin flush entre ellas")
    void salud() throws Exception {
        ML.responder(MlSimulado.SALUD_PREDICT, SIN_DESVIO);
        Long a101 = idAnimal(EST_A, "A-101");
        Long a103 = idAnimal(EST_A, "A-103"); // con alerta sanitaria reciente: se marca sin llamar a ml-salud
        cargarPerfil(a101);
        marcarUltimaLecturaPendiente(a101, "salud");
        marcarUltimaLecturaPendiente(a103, "salud");

        long flushes = flushes(saludJob);

        assertThat(lecturasSinEvaluar(a101, "salud") + lecturasSinEvaluar(a103, "salud")).isZero();
        assertThat(flushes).isLessThanOrEqualTo(commits("saludStep"));
    }

    private long flushes(Job job) throws Exception {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
        estadisticas.clear();
        try {
            correr(job);
            return estadisticas.getFlushCount();
        } finally {
            estadisticas.setStatisticsEnabled(false);
        }
    }

    private long commits(String step) {
        return jdbcTemplate.queryForObject("SELECT commit_count FROM batch_step_execution WHERE step_name = ? "
                + "ORDER BY step_execution_id DESC LIMIT 1", Long.class, step);
    }
}
