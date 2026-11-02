package dev.vigia.backend.fixture;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * El escenario conocido de {@code fixtures/escenario} (ver su README) para los IT del worker: los mismos scripts que
 * carga el {@code EscenarioFixture} de backend-api, así los jobs corren sobre los mismos animales, caravanas y alertas
 * que ven los IT de la API y el stack de Docker. Los usuarios no hacen falta acá: el worker no tiene login.
 */
public final class EscenarioFixture {

    private static final Path DIRECTORIO = buscarDirectorio();

    private EscenarioFixture() {
    }

    /** Borra el escenario anterior (y lo que se haya creado encima) y lo vuelve a cargar. */
    public static void cargar(DataSource dataSource) {
        ejecutar(dataSource, "limpiar.sql");
        ejecutar(dataSource, "escenario.sql");
    }

    // Los scripts abren y cierran su propia transacción (BEGIN/COMMIT, como con psql). Si uno falla a mitad, la conexión
    // vuelve al pool con la transacción abortada: se hace ROLLBACK antes de devolverla.
    private static void ejecutar(DataSource dataSource, String script) {
        EncodedResource recurso = new EncodedResource(
                new FileSystemResource(DIRECTORIO.resolve(script)), StandardCharsets.UTF_8);
        try (Connection conexion = dataSource.getConnection()) {
            try {
                ScriptUtils.executeSqlScript(conexion, recurso);
            } catch (RuntimeException e) {
                try (Statement st = conexion.createStatement()) {
                    st.execute("ROLLBACK");
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo correr " + script, e);
        }
    }

    // failsafe corre con el módulo como directorio de trabajo (backend/backend-worker); se busca hacia arriba para no
    // depender de eso.
    private static Path buscarDirectorio() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidato = dir.resolve("fixtures").resolve("escenario");
            if (Files.isDirectory(candidato)) {
                return candidato;
            }
        }
        throw new IllegalStateException("No se encontró fixtures/escenario desde " + Path.of("").toAbsolutePath());
    }
}
