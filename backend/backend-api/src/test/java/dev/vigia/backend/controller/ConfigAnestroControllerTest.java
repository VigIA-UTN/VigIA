package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.ConfigAnestroRequest;
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
 * Tests de integración para los endpoints de configuración de anestro:
 *   GET  /api/v1/establecimientos/{id}/anestro
 *   PUT  /api/v1/establecimientos/{id}/anestro
 *
 * Criterios de Aceptación verificados:
 *  - CA1: Cada establecimiento tiene sus parámetros; defaults: 45 días / MEDIA.
 *  - CA2: Solo PRODUCTOR y VETERINARIO del establecimiento pueden consultar y editar.
 *         El resto recibe HTTP 403.
 *  - CA3: Los valores se validan; rangos inválidos y severidades desconocidas → 400.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Configuración de Anestro — Tests de Integración")
class ConfigAnestroControllerTest {

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
        Usuario productor  = crearUsuario("productor@vigia.dev",  "prod123",  Rol.PRODUCTOR);
        Usuario veterinario = crearUsuario("vet@vigia.dev",        "vet123",   Rol.VETERINARIO);
        Usuario campero    = crearUsuario("campero@vigia.dev",    "camp123",  Rol.CAMPERO);
        //Usuario admin      = crearUsuario("admin@vigia.dev",      "admin123", Rol.ADMINISTRADOR);
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
        tokenProductor      = obtenerToken("productor@vigia.dev",  "prod123");
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

    private ConfigAnestroRequest crearRequest(int dias, String severidad) {
        ConfigAnestroRequest req = new ConfigAnestroRequest();
        req.setDiasDeteccionAnestro(dias);
        req.setSeveridadAnestro(severidad);
        return req;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CA1 – Defaults y lectura correcta
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA1 — GET anestro: defaults 45 días / MEDIA al crear establecimiento")
    void getConfigAnestro_defaultsCorrectos() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasDeteccionAnestro").value(45))
                .andExpect(jsonPath("$.severidadAnestro").value("MEDIA"))
                .andExpect(jsonPath("$.establecimientoId").value(establecimientoId));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA1 — PUT anestro: actualiza y persiste los parámetros correctamente")
    void updateConfigAnestro_persisteValores() throws Exception {
        ConfigAnestroRequest req = crearRequest(60, "ALTA");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasDeteccionAnestro").value(60))
                .andExpect(jsonPath("$.severidadAnestro").value("ALTA"));

        // Verificar que la consulta posterior devuelve los valores actualizados
        mockMvc.perform(get("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasDeteccionAnestro").value(60))
                .andExpect(jsonPath("$.severidadAnestro").value("ALTA"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CA2 – Control de acceso
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — GET anestro: PRODUCTOR del establecimiento puede consultar → 200")
    void getConfigAnestro_productorDelEstablecimiento_ok() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor))
                .andExpect(status().isOk());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — GET anestro: VETERINARIO del establecimiento puede consultar → 200")
    void getConfigAnestro_veterinarioDelEstablecimiento_ok() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenVeterinario))
                .andExpect(status().isOk());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — GET anestro: CAMPERO recibe 403 (no puede consultar)")
    void getConfigAnestro_campero_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenCampero))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — GET anestro: ADMINISTRADOR recibe 403 (no es del establecimiento)")
    void getConfigAnestro_administrador_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — GET anestro: PRODUCTOR ajeno (no vinculado) recibe 403")
    void getConfigAnestro_productorAjeno_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductorAjeno))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — PUT anestro: PRODUCTOR del establecimiento puede editar → 200")
    void updateConfigAnestro_productorDelEstablecimiento_ok() throws Exception {
        ConfigAnestroRequest req = crearRequest(30, "BAJA");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasDeteccionAnestro").value(30))
                .andExpect(jsonPath("$.severidadAnestro").value("BAJA"));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — PUT anestro: VETERINARIO del establecimiento puede editar → 200")
    void updateConfigAnestro_veterinarioDelEstablecimiento_ok() throws Exception {
        ConfigAnestroRequest req = crearRequest(90, "ALTA");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenVeterinario)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasDeteccionAnestro").value(90))
                .andExpect(jsonPath("$.severidadAnestro").value("ALTA"));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — PUT anestro: CAMPERO recibe 403 (no puede editar)")
    void updateConfigAnestro_campero_forbidden() throws Exception {
        ConfigAnestroRequest req = crearRequest(30, "BAJA");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenCampero)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — PUT anestro: ADMINISTRADOR recibe 403 (no es del establecimiento)")
    void updateConfigAnestro_administrador_forbidden() throws Exception {
        ConfigAnestroRequest req = crearRequest(30, "BAJA");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA2 — PUT anestro: PRODUCTOR ajeno (no vinculado) recibe 403")
    void updateConfigAnestro_productorAjeno_forbidden() throws Exception {
        ConfigAnestroRequest req = crearRequest(30, "BAJA");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductorAjeno)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CA3 – Validación de rangos y prioridades
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT anestro: días = 0 (fuera de rango mínimo) → 400")
    void updateConfigAnestro_diasCero_badRequest() throws Exception {
        ConfigAnestroRequest req = crearRequest(0, "MEDIA");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT anestro: días = -1 (valor negativo) → 400")
    void updateConfigAnestro_diasNegativos_badRequest() throws Exception {
        ConfigAnestroRequest req = crearRequest(-1, "MEDIA");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT anestro: días = 366 (supera máximo 365) → 400")
    void updateConfigAnestro_diasSuperanMaximo_badRequest() throws Exception {
        ConfigAnestroRequest req = crearRequest(366, "MEDIA");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT anestro: severidad desconocida 'EXTREMA' → 400")
    void updateConfigAnestro_severidadInvalida_badRequest() throws Exception {
        ConfigAnestroRequest req = crearRequest(45, "EXTREMA");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT anestro: días = 1 (límite inferior válido) → 200")
    void updateConfigAnestro_diasMinimo_ok() throws Exception {
        ConfigAnestroRequest req = crearRequest(1, "BAJA");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasDeteccionAnestro").value(1));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT anestro: días = 365 (límite superior válido) → 200")
    void updateConfigAnestro_diasMaximo_ok() throws Exception {
        ConfigAnestroRequest req = crearRequest(365, "ALTA");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasDeteccionAnestro").value(365));
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("CA3 — PUT anestro: severidad en minúscula ('alta') es aceptada → 200")
    void updateConfigAnestro_severidadMinuscula_ok() throws Exception {
        ConfigAnestroRequest req = crearRequest(45, "alta");

        mockMvc.perform(put("/api/v1/establecimientos/{id}/anestro", establecimientoId)
                .header("Authorization", "Bearer " + tokenProductor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severidadAnestro").value("ALTA"));
    }
}
