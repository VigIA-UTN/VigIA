package dev.vigia.backend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Migraciones de Flyway sobre TimescaleDB")
class MigracionesIT extends IntegracionBase {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Todas las migraciones se aplican sin errores y no queda ninguna pendiente")
    void todasLasMigracionesAplicadas() {
        MigrationInfoService info = flyway.info();

        assertThat(info.pending()).isEmpty();
        assertThat(info.all())
                .isNotEmpty()
                .allSatisfy(m -> assertThat(m.getState().isFailed())
                        .as("migración %s en estado %s", m.getVersion(), m.getState())
                        .isFalse());
        // La última migración del directorio es la versión actual de la base.
        MigrationInfo ultima = info.all()[info.all().length - 1];
        assertThat(info.current().getVersion()).isEqualTo(ultima.getVersion());
    }

    @Test
    @DisplayName("telemetria es una hypertable de TimescaleDB con política de retención (V22)")
    void telemetriaEsHypertableConRetencion() {
        Integer hypertables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM timescaledb_information.hypertables WHERE hypertable_name = 'telemetria'",
                Integer.class);
        assertThat(hypertables).isEqualTo(1);

        Integer retenciones = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM timescaledb_information.jobs " +
                        "WHERE proc_name = 'policy_retention' AND hypertable_name = 'telemetria'",
                Integer.class);
        assertThat(retenciones).isEqualTo(1);
    }

    @Test
    @DisplayName("PostGIS está instalado (V3)")
    void postgisInstalado() {
        Integer postgis = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'postgis'", Integer.class);
        assertThat(postgis).isEqualTo(1);
    }
}
