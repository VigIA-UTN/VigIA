package dev.vigia.backend.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.IntegracionBase;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.fixture.EscenarioFixture.Credenciales;
import dev.vigia.backend.fixture.EscenarioFixture.UsuarioFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("Escenario de pruebas de fixtures/escenario")
class EscenarioFixtureIT extends IntegracionBase {

    // Los timestamps de la base son hora local de Buenos Aires (como el stack de Docker), no la de la JVM.
    private static final String AHORA = "(now() AT TIME ZONE 'America/Argentina/Buenos_Aires')";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cargarEscenario() {
        EscenarioFixture.cargar(dataSource);
    }

    @Test
    @DisplayName("Cada usuario tiene los establecimientos que dice credenciales.json; el productor de ambos, A y B")
    void usuariosYEstablecimientos() {
        Credenciales credenciales = EscenarioFixture.credenciales();
        assertThat(credenciales.usuarios())
                .extracting(UsuarioFixture::rol)
                .contains("ADMINISTRADOR", "PRODUCTOR", "VETERINARIO", "CAMPERO");

        for (UsuarioFixture usuario : credenciales.usuarios()) {
            List<String> establecimientos = jdbcTemplate.queryForList(
                    "SELECT e.nombre FROM empleado em JOIN usuarios u ON u.id = em.usuario_id "
                            + "JOIN establecimientos e ON e.id = em.establecimiento_id WHERE u.email = ?",
                    String.class, usuario.email());
            String rol = jdbcTemplate.queryForObject("SELECT rol FROM usuarios WHERE email = ?", String.class,
                    usuario.email());

            assertThat(rol).as("rol de %s", usuario.email()).isEqualTo(usuario.rol());
            assertThat(establecimientos).as("establecimientos de %s", usuario.email())
                    .containsExactlyInAnyOrderElementsOf(usuario.establecimientos());
        }
        assertThat(credenciales.usuario("productorAmbos").establecimientos())
                .containsExactlyInAnyOrder("Fixture A", "Fixture B");
    }

    @ParameterizedTest(name = "Fixture {0}")
    @ValueSource(strings = { "A", "B" })
    @DisplayName("Rodeos, perímetro, caravanas y una alerta pendiente de cada tipo por establecimiento")
    void contenidoPorEstablecimiento(String letra) {
        String est = "Fixture " + letra;

        assertThat(contar("SELECT count(*) FROM rodeo_lote l JOIN establecimientos e ON e.id = l.establecimiento_id "
                + "WHERE e.nombre = ?", est)).isEqualTo(2);
        assertThat(contar("SELECT count(*) FROM perimetro_lote pl JOIN perimetro p ON p.id = pl.perimetro_id "
                + "JOIN establecimientos e ON e.id = p.establecimiento_id WHERE e.nombre = ?", est)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM animales a JOIN establecimientos e ON e.id = a.establecimiento_id "
                + "WHERE e.nombre = ?", est)).isEqualTo(6);

        Map<String, Object> caravanas = jdbcTemplate.queryForMap(
                "SELECT count(*) FILTER (WHERE k.estado_sensor = 'VINCULADO') AS vinculadas, "
                        + "count(*) FILTER (WHERE k.estado_sensor = 'DISPONIBLE') AS libres "
                        + "FROM caravanas k JOIN establecimientos e ON e.id = k.establecimiento_id WHERE e.nombre = ?",
                est);
        assertThat(caravanas).containsEntry("vinculadas", 6L).containsEntry("libres", 2L);

        List<Map<String, Object>> alertas = jdbcTemplate.queryForList(
                "SELECT al.disparador, count(*) AS cantidad FROM alertas al JOIN animales a ON a.id = al.animal_id "
                        + "JOIN establecimientos e ON e.id = a.establecimiento_id "
                        + "WHERE e.nombre = ? AND al.estado = 'PENDIENTE' GROUP BY al.disparador",
                est);
        assertThat(alertas).extracting(f -> f.get("disparador") + "=" + f.get("cantidad"))
                .containsExactlyInAnyOrder("CELO=2", "SALUD=1", "FUERA_DE_PERIMETRO=1", "AISLAMIENTO_SOCIAL=1");

        assertThat(contar("SELECT count(*) FROM alerta_animales_aislados x JOIN animales a ON a.id = x.animal_id "
                + "JOIN establecimientos e ON e.id = a.establecimiento_id "
                + "WHERE e.nombre = ? AND x.ts_regreso IS NULL", est)).isEqualTo(1);
    }

    @Test
    @DisplayName("Telemetría: 7 días de calibración en la 101 y la 106 sin datos hace más de 2 h")
    void telemetria() {
        assertThat(contar("SELECT count(*) FROM telemetria t JOIN caravanas k ON k.id = t.caravana_id "
                + "WHERE k.codigo = ? AND t.fecha_hora <= " + AHORA + " - interval '7 days'", "FX-A-01"))
                .isPositive();

        List<String> desactualizadas = jdbcTemplate.queryForList(
                "SELECT codigo FROM caravanas WHERE codigo LIKE 'FX-%' AND estado_sensor = 'VINCULADO' "
                        + "AND ultima_senial < " + AHORA + " - interval '2 hours'",
                String.class);
        assertThat(desactualizadas).containsExactlyInAnyOrder("FX-A-06", "FX-B-06");

        Integer alertaConDatoViejo = contar("SELECT count(*) FROM alertas al JOIN animales a ON a.id = al.animal_id "
                + "JOIN caravanas k ON k.id = a.caravana_id WHERE k.codigo = ? AND al.disparador = 'CELO' "
                + "AND al.estado = 'PENDIENTE'", "FX-A-06");
        assertThat(alertaConDatoViejo).isEqualTo(1);
    }

    @Test
    @DisplayName("Todos los usuarios de credenciales.json inician sesión con su rol")
    void loginDeCadaUsuario() throws Exception {
        Credenciales credenciales = EscenarioFixture.credenciales();
        for (UsuarioFixture usuario : credenciales.usuarios()) {
            AuthRequest request = new AuthRequest();
            request.setEmail(usuario.email());
            request.setPassword(credenciales.password());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rol").value(usuario.rol()));
        }
    }

    @Test
    @DisplayName("Cargar de nuevo deja exactamente lo mismo")
    void cargaRepetible() {
        Map<String, Object> antes = resumen();
        EscenarioFixture.cargar(dataSource);
        assertThat(resumen()).isEqualTo(antes);
    }

    @Test
    @DisplayName("limpiar.sql borra el escenario y nada más")
    void limpiarSoloBorraElEscenario() {
        String ajeno = "ajeno-" + UUID.randomUUID() + "@vigia.test";
        jdbcTemplate.update("INSERT INTO usuarios (email, rol) VALUES (?, 'PRODUCTOR')", ajeno);
        try {
            EscenarioFixture.limpiar(dataSource);

            assertThat(resumen()).allSatisfy((tabla, cantidad) -> assertThat(cantidad).as(tabla).isEqualTo(0L));
            assertThat(contar("SELECT count(*) FROM usuarios WHERE email = ?", ajeno)).isEqualTo(1);
        } finally {
            jdbcTemplate.update("DELETE FROM usuarios WHERE email = ?", ajeno);
        }
    }

    private Map<String, Object> resumen() {
        return jdbcTemplate.queryForMap("SELECT "
                + "(SELECT count(*) FROM usuarios WHERE email LIKE '%@fixture.vigia.test') AS usuarios, "
                + "(SELECT count(*) FROM establecimientos WHERE nombre LIKE 'Fixture %') AS establecimientos, "
                + "(SELECT count(*) FROM caravanas WHERE codigo LIKE 'FX-%') AS caravanas, "
                + "(SELECT count(*) FROM alertas al JOIN animales a ON a.id = al.animal_id "
                + "   JOIN caravanas k ON k.id = a.caravana_id WHERE k.codigo LIKE 'FX-%') AS alertas, "
                + "(SELECT count(*) FROM telemetria t JOIN caravanas k ON k.id = t.caravana_id "
                + "   WHERE k.codigo LIKE 'FX-%') AS telemetria");
    }

    private Integer contar(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }
}
