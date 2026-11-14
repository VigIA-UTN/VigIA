package dev.vigia.backend.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * El escenario conocido de {@code fixtures/escenario} (ver su README) para los IT: dos establecimientos con un usuario
 * por rol, alertas de cada tipo y telemetría. Corre los mismos scripts que el comando de psql, así que lo que ve un IT
 * es lo mismo que se carga en el stack de Docker.
 * <p>
 * Las credenciales salen de {@code credenciales.json}; los tests no escriben emails ni contraseñas.
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

    /** Borra el escenario y lo que se haya creado encima; el resto de la base no cambia. */
    public static void limpiar(DataSource dataSource) {
        ejecutar(dataSource, "limpiar.sql");
    }

    public static Credenciales credenciales() {
        try {
            return new ObjectMapper().readValue(DIRECTORIO.resolve("credenciales.json").toFile(), Credenciales.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record Credenciales(String password, List<UsuarioFixture> usuarios) {

        public UsuarioFixture usuario(String clave) {
            return usuarios.stream()
                    .filter(u -> u.clave().equals(clave))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No hay usuario '" + clave + "' en credenciales.json"));
        }
    }

    public record UsuarioFixture(String clave, String email, String rol, List<String> establecimientos) {
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

    // failsafe corre con el módulo como directorio de trabajo (backend/backend-api); se busca hacia arriba para no
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
