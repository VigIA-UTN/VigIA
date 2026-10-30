package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.IntegracionBase;
import dev.vigia.backend.fixture.EscenarioFixture;
import dev.vigia.backend.fixture.EscenarioFixture.Credenciales;
import dev.vigia.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de los IT de seguridad (autenticación, usuarios, establecimientos, invitaciones, RBAC y aislamiento por
 * establecimiento). Cada test parte del escenario de {@code fixtures/escenario} recién cargado: dos establecimientos
 * (Fixture A y Fixture B) con un usuario por rol y un productor en los dos.
 * <p>
 * Todo lo que crean los tests usa emails {@code @fixture.vigia.test} y establecimientos {@code Fixture ...}, así el
 * {@code limpiar.sql} del escenario lo borra en la próxima carga y no queda basura en la base compartida entre IT.
 * <p>
 * Los nombres de los tests llevan la HU y el número de caso de la hoja "Ciclo 1" del documento de Casos de Prueba
 * (por ejemplo {@code H0002-03}), para poder cruzar el resultado de la corrida con la planilla.
 */
@AutoConfigureMockMvc
abstract class SeguridadItBase extends IntegracionBase {

    protected static final String EST_A = "Fixture A";
    protected static final String EST_B = "Fixture B";

    // Cumple las reglas de AuthServiceImpl.validatePassword: 8 a 32, mayúscula, minúscula y número.
    protected static final String PASSWORD_VALIDA = "ClaveSegura123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AuthService authService;

    protected Credenciales credenciales;

    // Última clase que cargó el escenario; ver recargarEscenarioEnCadaTest().
    private static Class<?> claseConEscenarioCargado;

    @BeforeEach
    void prepararEscenario() {
        if (recargarEscenarioEnCadaTest() || claseConEscenarioCargado != getClass()) {
            EscenarioFixture.cargar(dataSource);
            claseConEscenarioCargado = getClass();
        }
        // El bloqueo por intentos fallidos vive en memoria del bean, que comparten todos los IT del módulo.
        authService.clearCacheForTests();
        credenciales = EscenarioFixture.credenciales();
    }

    /**
     * Por defecto cada test parte del escenario recién cargado. Una clase con muchos casos que no modifican datos (la
     * matriz de permisos) puede devolver {@code false} y cargarlo una sola vez: recargarlo en cada caso multiplica el
     * tiempo de la corrida sin cambiar el resultado.
     */
    protected boolean recargarEscenarioEnCadaTest() {
        return true;
    }

    // ── Login ────────────────────────────────────────────────────────────────

    protected String email(String clave) {
        return credenciales.usuario(clave).email();
    }

    protected MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andReturn();
    }

    /** JWT de un usuario del escenario (por su clave en credenciales.json). */
    protected String token(String clave) throws Exception {
        return tokenDe(email(clave), credenciales.password());
    }

    protected String tokenDe(String email, String password) throws Exception {
        MvcResult resultado = login(email, password);
        if (resultado.getResponse().getStatus() != 200) {
            throw new IllegalStateException("No se pudo iniciar sesión con " + email + ": "
                    + resultado.getResponse().getStatus() + " " + resultado.getResponse().getContentAsString());
        }
        return leer(resultado).get("token").asText();
    }

    protected String bearer(String clave) throws Exception {
        return "Bearer " + token(clave);
    }

    // ── Datos del escenario ──────────────────────────────────────────────────

    protected Long idEstablecimiento(String nombre) {
        return jdbcTemplate.queryForObject("SELECT id FROM establecimientos WHERE nombre = ?", Long.class, nombre);
    }

    protected Long idUsuario(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE email = ?", Long.class, email);
    }

    protected List<Long> idsAnimales(String establecimiento) {
        return jdbcTemplate.queryForList("SELECT a.id FROM animales a JOIN establecimientos e "
                + "ON e.id = a.establecimiento_id WHERE e.nombre = ? ORDER BY a.id", Long.class, establecimiento);
    }

    protected List<Long> idsAlertas(String establecimiento) {
        return jdbcTemplate.queryForList("SELECT al.id FROM alertas al JOIN animales a ON a.id = al.animal_id "
                + "JOIN establecimientos e ON e.id = a.establecimiento_id WHERE e.nombre = ? ORDER BY al.id",
                Long.class, establecimiento);
    }

    protected Long idRodeo(String establecimiento) {
        return jdbcTemplate.queryForObject("SELECT min(l.id) FROM rodeo_lote l JOIN establecimientos e "
                + "ON e.id = l.establecimiento_id WHERE e.nombre = ?", Long.class, establecimiento);
    }

    protected Long idPerimetro(String establecimiento) {
        return jdbcTemplate.queryForObject("SELECT min(p.id) FROM perimetro p JOIN establecimientos e "
                + "ON e.id = p.establecimiento_id WHERE e.nombre = ?", Long.class, establecimiento);
    }

    protected Long idCaravanaLibre(String establecimiento) {
        return jdbcTemplate.queryForObject("SELECT min(k.id) FROM caravanas k JOIN establecimientos e "
                + "ON e.id = k.establecimiento_id WHERE e.nombre = ? AND k.estado_sensor = 'DISPONIBLE'",
                Long.class, establecimiento);
    }

    protected boolean esEmpleadoActivo(String email, String establecimiento) {
        Integer cantidad = jdbcTemplate.queryForObject("SELECT count(*) FROM empleado em "
                + "JOIN usuarios u ON u.id = em.usuario_id JOIN establecimientos e ON e.id = em.establecimiento_id "
                + "WHERE u.email = ? AND e.nombre = ? AND em.activo = 1", Integer.class, email, establecimiento);
        return cantidad != null && cantidad > 0;
    }

    // ── Invitaciones y usuarios armados por JDBC ─────────────────────────────

    /** Email único dentro del dominio que limpia el escenario. */
    protected static String emailUnico() {
        return "it-" + UUID.randomUUID() + "@fixture.vigia.test";
    }

    /**
     * Inserta una invitación directamente, para preparar estados que por la API llevan días (vencida) o pasos previos
     * (usada). Devuelve el token.
     */
    protected String crearInvitacion(String tipo, String rol, String emailDestino, String establecimiento,
            String estado, LocalDateTime vence) {
        String token = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO invitaciones (email_destino, rol, fecha_creacion, fecha_expiracion, "
                        + "creador_id, establecimiento_id, token, tipo_invitacion, estado_invitacion) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                emailDestino, rol, Timestamp.valueOf(LocalDateTime.now().minusDays(1)), Timestamp.valueOf(vence),
                idUsuario(email(tipo.equals("PRODUCTOR") ? "admin" : "productorA")),
                establecimiento == null ? null : idEstablecimiento(establecimiento),
                token, tipo, estado);
        return token;
    }

    protected Long idInvitacion(String token) {
        return jdbcTemplate.queryForObject("SELECT id FROM invitaciones WHERE token = ?", Long.class, token);
    }

    protected String estadoInvitacion(String token) {
        return jdbcTemplate.queryForObject("SELECT estado_invitacion FROM invitaciones WHERE token = ?",
                String.class, token);
    }

    /** Crea un usuario con contraseña local, sin establecimientos. */
    protected void crearUsuario(String email, String rol, String proveedorAuth) {
        // Mismo hash bcrypt que usa escenario.sql: la contraseña es la de credenciales.json.
        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM usuarios WHERE email = ?",
                String.class, email("admin"));
        jdbcTemplate.update("INSERT INTO usuarios (nombre, apellido, email, password_hash, rol, activo, fecha_alta, "
                + "proveedor_auth) VALUES ('Usuario', 'IT', ?, ?, ?, 1, current_date, ?)",
                email, hash, rol, proveedorAuth);
    }

    // ── JSON ────────────────────────────────────────────────────────────────

    protected String json(Object valor) throws Exception {
        return objectMapper.writeValueAsString(valor);
    }

    protected JsonNode leer(MvcResult resultado) throws Exception {
        // Sin charset en el Content-Type, MockMvc decodifica en ISO-8859-1 y rompe los acentos.
        return objectMapper.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
