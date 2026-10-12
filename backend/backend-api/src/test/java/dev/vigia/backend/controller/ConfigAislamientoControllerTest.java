package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.ConfigAislamientoRequest;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.AuthService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración para los endpoints de configuración de aislamiento:
 *   GET  /api/v1/establecimientos/{id}/aislamiento
 *   PUT  /api/v1/establecimientos/{id}/aislamiento
 *
 * Criterios de Aceptación verificados:
 *  - CA1: Cada establecimiento tiene su umbral de aislamiento; si no se configuró,
 *         se usa el valor por defecto de ml-geo (100 m).
 *  - CA2: Solo el Productor y el Veterinario del establecimiento pueden consultarlo y editarlo.
 *         El resto recibe HTTP 403.
 *  - CA3: El valor se valida dentro del rango permitido (10.0 m a 1000.0 m).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Configuración de Umbral de Aislamiento — Tests de Integración")
class ConfigAislamientoControllerTest {

    // ── Mocks de infraestructura ──────────────────────────────────────────────

    @MockBean
    private ConnectionFactory connectionFactory;

    // ── Dependencias ──────────────────────────────────────────────────────────

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EstablecimientoRepository establecimientoRepository;
    @Autowired private InvitacionRepository invitacionRepository;
    @Autowired private dev.vigia.backend.repository.AnimalRepository animalRepository;
    @Autowired private dev.vigia.backend.repository.RodeoLoteRepository rodeoLoteRepository;
    @Autowired private dev.vigia.backend.repository.EstadoReproductivoRepository estadoReproductivoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthService authService;

    // ── Estado del test ───────────────────────────────────────────────────────

    private Long establecimientoId;

    private String tokenProductor;
    private String tokenVeterinario;
    private String tokenCampero;
    private String tokenAdmin;
    /** Productor de otro establecimiento (no tiene acceso al establecimiento principal). */
    private String tokenProductorAjeno;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() throws Exception {
        // Limpieza ordenada para evitar FK violations
        estadoReproductivoRepository.deleteAll();
        animalRepository.deleteAll();
        rodeoLoteRepository.deleteAll();
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.flush();
        authService.clearCacheForTests();

        // 1. Usuarios
        Usuario productor   = crearUsuario("productor@vigia.dev",   "prod123",  Rol.PRODUCTOR);
        Usuario veterinario = crearUsuario("vet@vigia.dev",         "vet123",   Rol.VETERINARIO);
        Usuario campero     = crearUsuario("campero@vigia.dev",     "camp123",  Rol.CAMPERO);
        @SuppressWarnings("unused")
        Usuario admin       = crearUsuario("admin@vigia.dev",       "admin123", Rol.ADMINISTRADOR);
        crearUsuario("ajeno@vigia.dev", "ajeno123", Rol.PRODUCTOR);

        // 2. Establecimiento principal con productor + veterinario + campero vinculados
        Establecimiento est = new Establecimiento();
        est.setNombre("Establecimiento Principal");
        est.setLatitud(-34.0);
        est.setLongitud(-58.0);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        est.setEmpleados(new ArrayList<>());
        est.getEmpleados().add(new Empleado(est, productor));
        est.getEmpleados().add(new Empleado(est, veterinario));
        est.getEmpleados().add(new Empleado(est, campero));
        est = establecimientoRepository.save(est);
        establecimientoId = est.getId();

        // 3. Establecimiento ajeno (sin relación con el establecimiento principal)
        Establecimiento estAjeno = new Establecimiento();
        estAjeno.setNombre("Establecimiento Ajeno");
        estAjeno.setLatitud(-35.0);
        estAjeno.setLongitud(-59.0);
        estAjeno.setActivo(true);
        estAjeno.setFechaAlta(LocalDate.now());
        estAjeno.setEmpleados(new ArrayList<>());
        establecimientoRepository.save(estAjeno);

        // 4. Tokens
        tokenProductor      = obtenerToken("productor@vigia.dev",   "prod123");
        tokenVeterinario    = obtenerToken("vet@vigia.dev",         "vet123");
        tokenCampero        = obtenerToken("campero@vigia.dev",     "camp123");
        tokenAdmin          = obtenerToken("admin@vigia.dev",       "admin123");
        tokenProductorAjeno = obtenerToken("ajeno@vigia.dev",       "ajeno123");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Usuario crearUsuario(String email, String password, Rol rol) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setNombre(rol.name());
        u.setRol(rol);
        u.setActivo(true);
        return usuarioRepository.save(u);
    }

    @SuppressWarnings("null")
    private String obtenerToken(String email, String password) throws Exception {
        AuthRequest body = new AuthRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private ConfigAislamientoRequest crearRequest(Double umbral) {
        return new ConfigAislamientoRequest(umbral);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CA1 – Defaults y persistencia
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA1 — GET aislamiento: default 100.0 m al crear establecimiento")
    void getConfigAislamiento_defaultCorrecto_100m() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralAislamientoM").value(100.0))
                .andExpect(jsonPath("$.establecimientoId").value(establecimientoId))
                .andExpect(jsonPath("$.nombreEstablecimiento").value("Establecimiento Principal"));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA1 — GET aislamiento: si umbralAislamientoM es null en BD, devuelve 100.0 m por defecto")
    void getConfigAislamiento_cuandoNull_devuelveDefault100m() throws Exception {
        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();
        est.setUmbralAislamientoM(null);
        establecimientoRepository.save(est);

        mockMvc.perform(get("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralAislamientoM").value(100.0));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA1 — PUT aislamiento: actualiza y persiste el umbral correctamente")
    void updateConfigAislamiento_persisteNuevoValor() throws Exception {
        ConfigAislamientoRequest req = crearRequest(150.0);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralAislamientoM").value(150.0))
                .andExpect(jsonPath("$.establecimientoId").value(establecimientoId));

        // Verificar que la consulta posterior devuelve el valor actualizado
        mockMvc.perform(get("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralAislamientoM").value(150.0));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CA2 – Control de acceso
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — GET aislamiento: PRODUCTOR del establecimiento puede consultar → 200")
    void getConfigAislamiento_productorDelEstablecimiento_ok() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor))
                .andExpect(status().isOk());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — GET aislamiento: VETERINARIO del establecimiento puede consultar → 200")
    void getConfigAislamiento_veterinarioDelEstablecimiento_ok() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenVeterinario))
                .andExpect(status().isOk());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — GET aislamiento: CAMPERO recibe 403 (no puede consultar)")
    void getConfigAislamiento_campero_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenCampero))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — GET aislamiento: ADMINISTRADOR recibe 403 (no es del establecimiento)")
    void getConfigAislamiento_administrador_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — GET aislamiento: PRODUCTOR ajeno recibe 403")
    void getConfigAislamiento_productorAjeno_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductorAjeno))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — PUT aislamiento: PRODUCTOR del establecimiento puede editar → 200")
    void updateConfigAislamiento_productorDelEstablecimiento_ok() throws Exception {
        ConfigAislamientoRequest req = crearRequest(120.0);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralAislamientoM").value(120.0));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — PUT aislamiento: VETERINARIO del establecimiento puede editar → 200")
    void updateConfigAislamiento_veterinarioDelEstablecimiento_ok() throws Exception {
        ConfigAislamientoRequest req = crearRequest(80.0);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenVeterinario)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralAislamientoM").value(80.0));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — PUT aislamiento: CAMPERO recibe 403 (no puede editar)")
    void updateConfigAislamiento_campero_forbidden() throws Exception {
        ConfigAislamientoRequest req = crearRequest(80.0);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenCampero)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — PUT aislamiento: ADMINISTRADOR recibe 403 (no es del establecimiento)")
    void updateConfigAislamiento_administrador_forbidden() throws Exception {
        ConfigAislamientoRequest req = crearRequest(80.0);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — PUT aislamiento: PRODUCTOR ajeno recibe 403")
    void updateConfigAislamiento_productorAjeno_forbidden() throws Exception {
        ConfigAislamientoRequest req = crearRequest(80.0);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductorAjeno)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CA3 – Validación de rangos
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT aislamiento: valor null → 400 Bad Request")
    void updateConfigAislamiento_umbralNull_badRequest() throws Exception {
        ConfigAislamientoRequest req = new ConfigAislamientoRequest(null);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT aislamiento: valor menor al mínimo (< 10.0 m) → 400 Bad Request")
    void updateConfigAislamiento_umbralMenorAlMinimo_badRequest() throws Exception {
        ConfigAislamientoRequest req = crearRequest(9.9);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT aislamiento: valor 0.0 m → 400 Bad Request")
    void updateConfigAislamiento_umbralCero_badRequest() throws Exception {
        ConfigAislamientoRequest req = crearRequest(0.0);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT aislamiento: valor negativo → 400 Bad Request")
    void updateConfigAislamiento_umbralNegativo_badRequest() throws Exception {
        ConfigAislamientoRequest req = crearRequest(-50.0);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT aislamiento: valor mayor al máximo (> 1000.0 m) → 400 Bad Request")
    void updateConfigAislamiento_umbralSuperaMaximo_badRequest() throws Exception {
        ConfigAislamientoRequest req = crearRequest(1000.1);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT aislamiento: valor límite inferior 10.0 m → 200 OK")
    void updateConfigAislamiento_umbralMinimoValido_ok() throws Exception {
        ConfigAislamientoRequest req = crearRequest(10.0);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralAislamientoM").value(10.0));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT aislamiento: valor límite superior 1000.0 m → 200 OK")
    void updateConfigAislamiento_umbralMaximoValido_ok() throws Exception {
        ConfigAislamientoRequest req = crearRequest(1000.0);

        mockMvc.perform(put("/api/v1/establecimientos/{id}/aislamiento", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralAislamientoM").value(1000.0));
    }
}
