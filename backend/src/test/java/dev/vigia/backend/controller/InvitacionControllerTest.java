package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.InvitacionEmpleadoRequest;
import dev.vigia.backend.dto.InvitacionProductorRequest;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("InvitacionController — Tests")
class InvitacionControllerTest {

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
    private dev.vigia.backend.service.AuthService authService;

    @MockBean
    private EmailService emailService;

    private String adminToken;
    private String userToken;
    private Long establecimientoId;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() throws Exception {
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
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
        est.setUsuarios(new ArrayList<>());
        est.getUsuarios().add(productor);
        est = establecimientoRepository.save(est);
        establecimientoId = est.getId();
        
        // Mock email service
        doNothing().when(emailService).enviarInvitacionProductor(anyString(), anyString(), anyString());
        doNothing().when(emailService).enviarInvitacionEmpleado(anyString(), anyString(), anyString(), anyString());
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
    @DisplayName("POST /api/v1/invitaciones/productor - Admin crea invitacion exitosamente")
    @SuppressWarnings("null")
    void crearInvitacion_asAdmin_success() throws Exception {
        InvitacionProductorRequest request = new InvitacionProductorRequest();
        request.setEmail("nuevo_productor@test.com");
        request.setEstablecimientoId(establecimientoId);

        mockMvc.perform(post("/api/v1/invitaciones/productor")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailDestino").value("nuevo_productor@test.com"))
                .andExpect(jsonPath("$.rol").value(Rol.PRODUCTOR.name()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(invitacionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/v1/invitaciones/productor - Falla si el establecimiento no existe")
    @SuppressWarnings("null")
    void crearInvitacion_establecimientoNotFound() throws Exception {
        InvitacionProductorRequest request = new InvitacionProductorRequest();
        request.setEmail("nuevo_productor@test.com");
        request.setEstablecimientoId(999L); // ID inexistente

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mockMvc.perform(post("/api/v1/invitaciones/productor")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))))
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("POST /api/v1/invitaciones/productor - Usuario no-Admin recibe 403 Forbidden")
    @SuppressWarnings("null")
    void crearInvitacion_asProductor_forbidden() throws Exception {
        InvitacionProductorRequest request = new InvitacionProductorRequest();
        request.setEmail("nuevo_productor@test.com");
        request.setEstablecimientoId(establecimientoId);

        mockMvc.perform(post("/api/v1/invitaciones/productor")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
                
        assertThat(invitacionRepository.findAll()).isEmpty();
    }
    
    @Test
    @DisplayName("POST /api/v1/invitaciones/productor - Falla validacion por email invalido")
    @SuppressWarnings("null")
    void crearInvitacion_invalidEmail() throws Exception {
        InvitacionProductorRequest request = new InvitacionProductorRequest();
        request.setEmail("email-invalido");
        request.setEstablecimientoId(establecimientoId);

        mockMvc.perform(post("/api/v1/invitaciones/productor")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/invitaciones/empleado - Productor crea invitacion exitosamente")
    @SuppressWarnings("null")
    void crearInvitacionEmpleado_asProductor_success() throws Exception {
        InvitacionEmpleadoRequest request = new InvitacionEmpleadoRequest();
        request.setEmail("nuevo_empleado@test.com");
        request.setEstablecimientoId(establecimientoId);
        request.setRol(Rol.VETERINARIO);

        mockMvc.perform(post("/api/v1/invitaciones/empleado")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailDestino").value("nuevo_empleado@test.com"))
                .andExpect(jsonPath("$.rol").value(Rol.VETERINARIO.name()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(invitacionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/v1/invitaciones/empleado - Falla si productor no pertenece al establecimiento")
    @SuppressWarnings("null")
    void crearInvitacionEmpleado_wrongEstablecimiento() throws Exception {
        // Crear un establecimiento donde el productor no está
        Establecimiento est2 = new Establecimiento();
        est2.setNombre("Establecimiento Otro");
        est2.setLatitud(-34.0);
        est2.setLongitud(-58.0);
        est2.setActivo(true);
        est2 = establecimientoRepository.save(est2);

        InvitacionEmpleadoRequest request = new InvitacionEmpleadoRequest();
        request.setEmail("nuevo_empleado@test.com");
        request.setEstablecimientoId(est2.getId());
        request.setRol(Rol.VETERINARIO);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mockMvc.perform(post("/api/v1/invitaciones/empleado")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))))
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El productor no pertenece a este establecimiento");
    }

    @Test
    @DisplayName("POST /api/v1/invitaciones/empleado - Falla si rol es invalido para empleado")
    @SuppressWarnings("null")
    void crearInvitacionEmpleado_invalidRole() throws Exception {
        InvitacionEmpleadoRequest request = new InvitacionEmpleadoRequest();
        request.setEmail("nuevo_empleado@test.com");
        request.setEstablecimientoId(establecimientoId);
        request.setRol(Rol.ADMINISTRADOR);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mockMvc.perform(post("/api/v1/invitaciones/empleado")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))))
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El rol especificado no es válido");
    }
}
