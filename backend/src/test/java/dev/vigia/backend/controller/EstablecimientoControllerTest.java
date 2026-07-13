package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("EstablecimientoController — Tests")
class EstablecimientoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EstablecimientoRepository establecimientoRepository;

    @Autowired
    private InvitacionRepository invitacionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    private String adminToken;
    private String userToken;
    private Long establecimientoId;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() throws Exception {
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.flush();
        authService.clearCacheForTests();

        // 1. Setup ADMIN user
        Usuario admin = new Usuario();
        admin.setEmail("admin@vigia.dev");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setNombre("Admin");
        admin.setRol(Rol.ADMINISTRADOR);
        admin.setActivo(true);
        usuarioRepository.save(admin);

        // 2. Setup PRODUCTOR user (non-admin)
        Usuario productor = new Usuario();
        productor.setEmail("productor@vigia.dev");
        productor.setPasswordHash(passwordEncoder.encode("prod123"));
        productor.setNombre("Productor");
        productor.setRol(Rol.PRODUCTOR);
        productor.setActivo(true);
        usuarioRepository.save(productor);

        // 3. Get tokens
        adminToken = getTokenFor("admin@vigia.dev", "admin123");
        userToken = getTokenFor("productor@vigia.dev", "prod123");

        // 4. Create an initial establishment
        Establecimiento est = new Establecimiento();
        est.setNombre("Establecimiento Test");
        est.setLatitud(-34.0);
        est.setLongitud(-58.0);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        est.setEmpleados(new ArrayList<>());
        Empleado vinculoProd = new Empleado(est, productor);
        est.getEmpleados().add(vinculoProd);
        est = establecimientoRepository.save(est);
        establecimientoId = est.getId();
    }

    @SuppressWarnings("null")
    private String getTokenFor(String email, String password) throws Exception {
        AuthRequest body = new AuthRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("POST /api/v1/establecimientos - Admin crea establecimiento exitosamente")
    @SuppressWarnings("null")
    void createEstablecimiento_asAdmin_success() throws Exception {
        EstablecimientoRequest request = new EstablecimientoRequest();
        request.setNombre("Nuevo Est");
        request.setLatitud(-35.0);
        request.setLongitud(-59.0);

        mockMvc.perform(post("/api/v1/establecimientos")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Nuevo Est"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/establecimientos - Admin lista establecimientos")
    void getAllEstablecimientos_asAdmin_success() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Establecimiento Test"));
    }

    @Test
    @DisplayName("DELETE /api/v1/establecimientos/{id} - Admin desactiva establecimiento")
    @SuppressWarnings("null")
    void desactivarEstablecimiento_asAdmin_success() throws Exception {
        mockMvc.perform(delete("/api/v1/establecimientos/" + establecimientoId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        // Verificar en BD que fue baja lógica
        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();
        assertThat(est.isActivo()).isFalse();
    }

    @Test
    @DisplayName("POST /api/v1/establecimientos - Usuario no-Admin recibe 403 Forbidden")
    @SuppressWarnings("null")
    void createEstablecimiento_asProductor_forbidden() throws Exception {
        EstablecimientoRequest request = new EstablecimientoRequest();
        request.setNombre("No Deberia Crear");
        request.setLatitud(-35.0);
        request.setLongitud(-59.0);

        mockMvc.perform(post("/api/v1/establecimientos")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/establecimientos - Productor lista solo sus establecimientos")
    void getAllEstablecimientos_asProductor_success() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Establecimiento Test"));
    }
}
