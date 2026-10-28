package dev.vigia.backend.repository;

import dev.vigia.backend.IntegracionBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Validación de bloqueos de Update y Delete (Inmutabilidad en BD)")
class InmutabilidadTablasIT extends IntegracionBase {

    @Autowired
    private DataSource dataSource;

    private Connection connection;

    private long usuarioDePrueba;
    private long animalDePrueba;
    private long alertaDePrueba;

    @BeforeEach
    void setUp() throws SQLException {
        connection = dataSource.getConnection();
        connection.setAutoCommit(true);

        usuarioDePrueba = insertarDevolviendoId(
                "INSERT INTO usuarios (email) VALUES ('inmutabilidad-' || gen_random_uuid() || '@vigia.test') RETURNING id");
        animalDePrueba = insertarDevolviendoId(
                "INSERT INTO animales (alias) VALUES ('Animal inmutabilidad') RETURNING id");
        alertaDePrueba = insertarAlerta("PENDIENTE");

        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery(
                    "SELECT tgname FROM pg_trigger WHERE NOT tgisinternal AND tgname IN (" +
                    "'trg_bloquear_modificacion_intervenciones', 'trg_bloquear_modificacion_diagnosticos', " +
                    "'trg_bloquear_modificacion_estados_salud', 'trg_bloquear_cambio_resolucion_alertas')");
            Set<String> triggers = new HashSet<>();
            while (rs.next()) {
                triggers.add(rs.getString(1));
            }
            assertThat(triggers).containsExactlyInAnyOrder(
                    "trg_bloquear_modificacion_intervenciones",
                    "trg_bloquear_modificacion_diagnosticos",
                    "trg_bloquear_modificacion_estados_salud",
                    "trg_bloquear_cambio_resolucion_alertas");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            try (Statement st = connection.createStatement()) {
                st.execute("RESET vigia.bypass_immutable_triggers");
            } catch (Exception ignored) {
            }
            connection.close();
        }
    }

    @Test
    @DisplayName("Intervenciones: INSERT permitido, UPDATE bloqueado con mensaje de acción prohibida")
    void testIntervencionesBloqueoUpdate() throws SQLException {
        long animalId = animalDePrueba;
        long usuarioId = usuarioDePrueba;

        long idGenerado;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO intervenciones (animal_id, interventor_id, descripcion, fecha_hora, tipo) " +
                        "VALUES (?, ?, 'Intervención de prueba test update', NOW(), 'TRATAMIENTO') RETURNING id")) {
            ps.setLong(1, animalId);
            ps.setLong(2, usuarioId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            idGenerado = rs.getLong(1);
        }

        // Intento de UPDATE debe fallar
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE intervenciones SET descripcion = 'Descripción modificada' WHERE id = ?")) {
            ps.setLong(1, idGenerado);
            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Acción prohibida")
                    .hasMessageContaining("intervenciones");
        }

        limpiarRegistro("intervenciones", idGenerado);
    }

    @Test
    @DisplayName("Intervenciones: INSERT permitido, DELETE bloqueado con mensaje de acción prohibida")
    void testIntervencionesBloqueoDelete() throws SQLException {
        long animalId = animalDePrueba;
        long usuarioId = usuarioDePrueba;

        long idGenerado;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO intervenciones (animal_id, interventor_id, descripcion, fecha_hora, tipo) " +
                        "VALUES (?, ?, 'Intervención de prueba test delete', NOW(), 'TRATAMIENTO') RETURNING id")) {
            ps.setLong(1, animalId);
            ps.setLong(2, usuarioId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            idGenerado = rs.getLong(1);
        }

        // Intento de DELETE debe fallar
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM intervenciones WHERE id = ?")) {
            ps.setLong(1, idGenerado);
            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Acción prohibida")
                    .hasMessageContaining("intervenciones");
        }

        limpiarRegistro("intervenciones", idGenerado);
    }

    @Test
    @DisplayName("Diagnósticos: INSERT permitido, UPDATE bloqueado con mensaje de acción prohibida")
    void testDiagnosticosBloqueoUpdate() throws SQLException {
        long animalId = animalDePrueba;
        long alertaId = alertaDePrueba;
        long veterinarioId = usuarioDePrueba;

        long idGenerado;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO diagnosticos (animal_id, alerta_id, veterinario_id, diagnostico, indicaciones, fecha) " +
                        "VALUES (?, ?, ?, 'Diagnóstico clínico de prueba', 'Indicaciones de prueba', NOW()) RETURNING id")) {
            ps.setLong(1, animalId);
            ps.setLong(2, alertaId);
            ps.setLong(3, veterinarioId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            idGenerado = rs.getLong(1);
        }

        // Intento de UPDATE debe fallar
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE diagnosticos SET diagnostico = 'Diagnóstico alterado' WHERE id = ?")) {
            ps.setLong(1, idGenerado);
            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Acción prohibida")
                    .hasMessageContaining("diagnosticos");
        }

        limpiarRegistro("diagnosticos", idGenerado);
    }

    @Test
    @DisplayName("Diagnósticos: INSERT permitido, DELETE bloqueado con mensaje de acción prohibida")
    void testDiagnosticosBloqueoDelete() throws SQLException {
        long animalId = animalDePrueba;
        long alertaId = alertaDePrueba;
        long veterinarioId = usuarioDePrueba;

        long idGenerado;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO diagnosticos (animal_id, alerta_id, veterinario_id, diagnostico, indicaciones, fecha) " +
                        "VALUES (?, ?, ?, 'Diagnóstico clínico para borrar', 'Indicaciones de prueba', NOW()) RETURNING id")) {
            ps.setLong(1, animalId);
            ps.setLong(2, alertaId);
            ps.setLong(3, veterinarioId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            idGenerado = rs.getLong(1);
        }

        // Intento de DELETE debe fallar
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM diagnosticos WHERE id = ?")) {
            ps.setLong(1, idGenerado);
            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Acción prohibida")
                    .hasMessageContaining("diagnosticos");
        }

        limpiarRegistro("diagnosticos", idGenerado);
    }

    @Test
    @DisplayName("Estados de salud: INSERT permitido, UPDATE bloqueado con mensaje de acción prohibida")
    void testEstadosSaludBloqueoUpdate() throws SQLException {
        long idGenerado = insertarEstadoSalud("Estado de salud de prueba test update");

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE estados_salud SET diagnostico_notas = 'Notas modificadas' WHERE estado_salud_id = ?")) {
            ps.setLong(1, idGenerado);
            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Acción prohibida")
                    .hasMessageContaining("estados_salud");
        }

        limpiarRegistro("estados_salud", "estado_salud_id", idGenerado);
    }

    @Test
    @DisplayName("Estados de salud: INSERT permitido, DELETE bloqueado con mensaje de acción prohibida")
    void testEstadosSaludBloqueoDelete() throws SQLException {
        long idGenerado = insertarEstadoSalud("Estado de salud de prueba test delete");

        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM estados_salud WHERE estado_salud_id = ?")) {
            ps.setLong(1, idGenerado);
            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Acción prohibida")
                    .hasMessageContaining("estados_salud");
        }

        limpiarRegistro("estados_salud", "estado_salud_id", idGenerado);
    }

    @Test
    @DisplayName("Bypass opcional: permite UPDATE y DELETE cuando la variable de sesión está activa")
    void testBypassPermiteModificaciones() throws SQLException {
        long animalId = animalDePrueba;
        long alertaId = alertaDePrueba;
        long veterinarioId = usuarioDePrueba;

        long idGenerado;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO diagnosticos (animal_id, alerta_id, veterinario_id, diagnostico, indicaciones, fecha) " +
                        "VALUES (?, ?, ?, 'Diagnóstico con bypass', 'Indicaciones de prueba', NOW()) RETURNING id")) {
            ps.setLong(1, animalId);
            ps.setLong(2, alertaId);
            ps.setLong(3, veterinarioId);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            idGenerado = rs.getLong(1);
        }

        try (Statement st = connection.createStatement()) {
            st.execute("SET vigia.bypass_immutable_triggers = 'true'");
        }

        // UPDATE con bypass debe funcionar sin error
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE diagnosticos SET diagnostico = 'Diagnóstico modificado vía bypass' WHERE id = ?")) {
            ps.setLong(1, idGenerado);
            int updated = ps.executeUpdate();
            assertThat(updated).isEqualTo(1);
        }

        // DELETE con bypass debe funcionar sin error
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM diagnosticos WHERE id = ?")) {
            ps.setLong(1, idGenerado);
            int deleted = ps.executeUpdate();
            assertThat(deleted).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Alertas: una alerta confirmada no puede cambiar su resolución")
    void testAlertaConfirmadaBloqueaCambioDeResolucion() throws SQLException {
        long alertaId = insertarAlerta("CONFIRMADA");

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE alertas SET estado = 'PENDIENTE' WHERE id = ?")) {
            ps.setLong(1, alertaId);
            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Acción prohibida")
                    .hasMessageContaining("resolución de la alerta");
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE alertas SET usuario_confirmacion_id = NULL, ts_confirmacion = NOW() WHERE id = ?")) {
            ps.setLong(1, alertaId);
            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Acción prohibida");
        }

        limpiarAlerta(alertaId);
    }

    @Test
    @DisplayName("Alertas: una alerta descartada no puede cambiar el dato sensorial de origen")
    void testAlertaDescartadaBloqueaCambioDeDatoOrigen() throws SQLException {
        long alertaId = insertarAlerta("DESCARTADA");

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE alertas SET confianza_modelo = 0.1, serie_comparativa_json = '{}' WHERE id = ?")) {
            ps.setLong(1, alertaId);
            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Acción prohibida");
        }

        limpiarAlerta(alertaId);
    }

    @Test
    @DisplayName("Alertas: una alerta pendiente se puede resolver y las columnas no protegidas siguen editables")
    void testAlertaPendientePermiteResolver() throws SQLException {
        long alertaId = insertarAlerta("PENDIENTE");

        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE alertas SET estado = 'CONFIRMADA', ts_confirmacion = NOW() WHERE id = ?")) {
            ps.setLong(1, alertaId);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }

        // Ya confirmada: la acción recomendada no forma parte de la resolución y se puede actualizar.
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE alertas SET accion_recomendada = 'Revisar' WHERE id = ?")) {
            ps.setLong(1, alertaId);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }

        limpiarAlerta(alertaId);
    }

    private long insertarDevolviendoId(String sql) throws SQLException {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private long insertarEstadoSalud(String notas) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO estados_salud (animal_id, estado, diagnostico_notas, origen, fecha_registro) " +
                        "VALUES (?, 'SANO', ?, 'MANUAL', NOW()) RETURNING estado_salud_id")) {
            ps.setLong(1, animalDePrueba);
            ps.setString(2, notas);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private long insertarAlerta(String estado) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO alertas (animal_id, tipo, ts_generacion, disparador, valor_umbral, estado, " +
                        "confianza_modelo, serie_comparativa_json) " +
                        "VALUES (?, 'SALUD', NOW(), 'SALUD', 0.5, ?, 0.9, '{\"serie\":[]}') RETURNING id")) {
            ps.setLong(1, animalDePrueba);
            ps.setString(2, estado);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private void limpiarAlerta(long id) {
        // El trigger de alertas solo bloquea UPDATE: el DELETE no necesita bypass.
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM alertas WHERE id = " + id);
        } catch (SQLException ignored) {
        }
    }

    private void limpiarRegistro(String tabla, long id) {
        limpiarRegistro(tabla, "id", id);
    }

    private void limpiarRegistro(String tabla, String columnaId, long id) {
        try (Statement st = connection.createStatement()) {
            st.execute("SET vigia.bypass_immutable_triggers = 'true'");
            st.execute("DELETE FROM " + tabla + " WHERE " + columnaId + " = " + id);
            st.execute("RESET vigia.bypass_immutable_triggers");
        } catch (SQLException ignored) {
        }
    }
}
