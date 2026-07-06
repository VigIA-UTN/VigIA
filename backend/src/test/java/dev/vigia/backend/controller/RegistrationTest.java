package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.RegisterRequest;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Registro de Usuarios")
class RegistrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private InvitacionRepository invitacionRepository;

    @Autowired
    private EstablecimientoRepository establecimientoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Invitacion validInvitacion;
    private Establecimiento establecimiento;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() {
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();

        establecimiento = new Establecimiento();
        establecimiento.setNombre("Establecimiento Base");
        establecimiento.setLatitud(0.0);
        establecimiento.setLongitud(0.0);
        establecimiento.setActivo(true);
        establecimiento.setFechaAlta(LocalDate.now());
        establecimiento = establecimientoRepository.save(establecimiento);

        validInvitacion = new Invitacion();
        validInvitacion.setEmailDestino("nuevo@vigia.dev");
        validInvitacion.setRol(Rol.VETERINARIO);
        validInvitacion.setFechaCreacion(LocalDateTime.now());
        validInvitacion.setFechaExpiracion(LocalDateTime.now().plusDays(7));
        validInvitacion.setEstablecimiento(establecimiento);
        validInvitacion.setToken("TOKEN123");
        validInvitacion.setTipoInvitacion(TipoInvitacion.EMPLEADO);
        validInvitacion.setEstadoInvitacion(EstadoInvitacion.PENDIENTE);
        validInvitacion = invitacionRepository.save(validInvitacion);
    }

    @Test
    @DisplayName("Registro exitoso con invitación EMPLEADO (no usa email del request)")
    @SuppressWarnings("null")
    void register_success_empleado() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Juan");
        request.setApellido("Perez");
        request.setPassword("Password123!");
        request.setToken("TOKEN123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("nuevo@vigia.dev"))
                .andExpect(jsonPath("$.rol").value("VETERINARIO"));

        // Verify DB updates
        Usuario user = usuarioRepository.findByEmail("nuevo@vigia.dev").orElseThrow();
        assertThat(user.getRol()).isEqualTo(Rol.VETERINARIO);

        Invitacion inv = invitacionRepository.findByToken("TOKEN123").orElseThrow();
        assertThat(inv.getEstadoInvitacion()).isEqualTo(EstadoInvitacion.USADA);
    }

    @Test
    @DisplayName("Registro exitoso con invitación PRODUCTOR (valida email del request)")
    @SuppressWarnings("null")
    void register_success_productor() throws Exception {
        validInvitacion.setTipoInvitacion(TipoInvitacion.PRODUCTOR);
        validInvitacion.setRol(Rol.PRODUCTOR);
        invitacionRepository.save(validInvitacion);

        RegisterRequest request = new RegisterRequest();
        request.setNombre("Matias");
        request.setApellido("Productor");
        request.setEmail("nuevo@vigia.dev");
        request.setPassword("Password123!");
        request.setToken("TOKEN123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value("nuevo@vigia.dev"))
                .andExpect(jsonPath("$.rol").value("PRODUCTOR"));
    }

    @Test
    @DisplayName("Falla si la contraseña es débil (sin mayúscula)")
    @SuppressWarnings("null")
    void register_weakPassword_noUpper() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("nuevo@vigia.dev");
        request.setPassword("password123!");
        request.setToken("TOKEN123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Falla si el token no existe")
    @SuppressWarnings("null")
    void register_invalidToken() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("nuevo@vigia.dev");
        request.setPassword("Password123!");
        request.setToken("INVALID");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Falla si el token expiró")
    @SuppressWarnings("null")
    void register_expiredToken() throws Exception {
        validInvitacion.setFechaExpiracion(LocalDateTime.now().minusDays(1));
        invitacionRepository.save(validInvitacion);

        RegisterRequest request = new RegisterRequest();
        request.setEmail("nuevo@vigia.dev");
        request.setPassword("Password123!");
        request.setToken("TOKEN123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
