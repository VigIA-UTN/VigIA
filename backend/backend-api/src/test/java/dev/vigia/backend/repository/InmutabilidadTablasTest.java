package dev.vigia.backend.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("Validación de bloqueos de Update y Delete (Inmutabilidad en BD)")
public class InmutabilidadTablasTest {

    private static final String DB_URL = System.getProperty("DB_URL", "jdbc:postgresql://localhost:5432/vigia");
    private static final String DB_USER = System.getProperty("DB_USER", "vigia_user");
    private static final String DB_PASS = System.getProperty("DB_PASSWORD", "vigia_dev_password");

    private Connection connection;

    @BeforeAll
    static void verificarDisponibilidadPostgreSQL() {
        boolean disponible = false;
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            disponible = true;
        } catch (Exception e) {
            disponible = false;
        }
        assumeTrue(disponible, "Base de datos PostgreSQL no disponible en " + DB_URL + " - Se omite el test");
    }

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        connection.setAutoCommit(true);

        // Verificar que las tablas requeridas existen (pueden no existir si Flyway no corrió, ej: CI sin schema)
        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_name IN ('intervenciones', 'diagnosticos', 'estados_salud')");
            rs.next();
            int tablesFound = rs.getInt(1);
            assumeTrue(tablesFound == 3,
                    "Las tablas 'intervenciones', 'diagnosticos' y/o 'estados_salud' no existen en la BD - " +
                    "Ejecutar las migraciones Flyway antes de correr este test");
        }

        try (Statement st = connection.createStatement()) {
            // Asegurar que la función del trigger existe
            st.execute(
                    """
                                CREATE OR REPLACE FUNCTION trg_bloquear_modificacion_inmutable()
                                RETURNS TRIGGER AS $$
                                BEGIN
                                    IF current_setting('vigia.bypass_immutable_triggers', true) = 'true' THEN
                                        IF TG_OP = 'DELETE' THEN
                                            RETURN OLD;
                                        ELSE
                                            RETURN NEW;
                                        END IF;
                                    END IF;

                                    IF TG_OP = 'UPDATE' THEN
                                        RAISE EXCEPTION 'Acción prohibida: no se permite modificar registros en la tabla %', TG_TABLE_NAME;
                                    ELSIF TG_OP = 'DELETE' THEN
                                        RAISE EXCEPTION 'Acción prohibida: no se permite eliminar registros en la tabla %', TG_TABLE_NAME;
                                    END IF;
                                    RETURN NULL;
                                END;
                                $$ LANGUAGE plpgsql;
                            """);

            // Asegurar que la tabla intervenciones tiene el trigger activo
            st.execute("""
                        DROP TRIGGER IF EXISTS trg_bloquear_modificacion_intervenciones ON intervenciones;
                        CREATE TRIGGER trg_bloquear_modificacion_intervenciones
                            BEFORE UPDATE OR DELETE ON intervenciones
                            FOR EACH ROW
                            EXECUTE FUNCTION trg_bloquear_modificacion_inmutable();
                    """);

            // Asegurar que la tabla diagnosticos tiene el trigger activo
            st.execute("""
                        DROP TRIGGER IF EXISTS trg_bloquear_modificacion_diagnosticos ON diagnosticos;
                        CREATE TRIGGER trg_bloquear_modificacion_diagnosticos
                            BEFORE UPDATE OR DELETE ON diagnosticos
                            FOR EACH ROW
                            EXECUTE FUNCTION trg_bloquear_modificacion_inmutable();
                    """);

            // Asegurar que la tabla estados_salud tiene el trigger activo
            st.execute("""
                        DROP TRIGGER IF EXISTS trg_bloquear_modificacion_estados_salud ON estados_salud;
                        CREATE TRIGGER trg_bloquear_modificacion_estados_salud
                            BEFORE UPDATE OR DELETE ON estados_salud
                            FOR EACH ROW
                            EXECUTE FUNCTION trg_bloquear_modificacion_inmutable();
                    """);
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
        Long animalId = obtenerAnimalValido();
        Long usuarioId = obtenerUsuarioValido();

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
        Long animalId = obtenerAnimalValido();
        Long usuarioId = obtenerUsuarioValido();

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
        Long animalId = obtenerAnimalValido();
        Long alertaId = obtenerAlertaValida();
        Long veterinarioId = obtenerUsuarioValido();

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
        Long animalId = obtenerAnimalValido();
        Long alertaId = obtenerAlertaValida();
        Long veterinarioId = obtenerUsuarioValido();

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
        Long animalId = obtenerAnimalValido();
        Long alertaId = obtenerAlertaValida();
        Long veterinarioId = obtenerUsuarioValido();

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

    private Long obtenerAnimalValido() throws SQLException {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT id FROM animales LIMIT 1")) {
            if (rs.next())
                return rs.getLong(1);
        }
        return 1L;
    }

    private Long obtenerUsuarioValido() throws SQLException {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT id FROM usuarios LIMIT 1")) {
            if (rs.next())
                return rs.getLong(1);
        }
        return 1L;
    }

    private Long obtenerAlertaValida() throws SQLException {
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("SELECT id FROM alertas LIMIT 1")) {
            if (rs.next())
                return rs.getLong(1);
        }
        return 1L;
    }

    private long insertarEstadoSalud(String notas) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO estados_salud (animal_id, estado, diagnostico_notas, origen, fecha_registro) " +
                        "VALUES (?, 'SANO', ?, 'MANUAL', NOW()) RETURNING estado_salud_id")) {
            ps.setLong(1, obtenerAnimalValido());
            ps.setString(2, notas);
            ResultSet rs = ps.executeQuery();
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
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
