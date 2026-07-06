package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AuthController — integración JWT")
class AuthControllerTest {

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

    private static final String EMAIL = "productor@vigia.dev";
    private static final String PASSWORD = "secret123";

    @BeforeEach
    void setUp() {
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        authService.clearCacheForTests();

        Usuario usuario = new Usuario();
        usuario.setEmail(EMAIL);
        usuario.setPasswordHash(passwordEncoder.encode(PASSWORD));
        usuario.setNombre("Juan");
        usuario.setApellido("Test");
        usuario.setRol(Rol.PRODUCTOR);
        usuario.setActivo(true);
        usuario.setFechaAlta(LocalDate.now());
        usuarioRepository.save(usuario);
    }

    // ─── Criterio 1: Token generado correctamente ────────────────────────────

    @Test
    @DisplayName("Login exitoso → responde 200 con token, email y rol")
    @SuppressWarnings("null")
    void login_success_returnsTokenAndUserInfo() throws Exception {
        AuthRequest body = new AuthRequest(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.rol").value("PRODUCTOR"));
    }

    @Test
    @DisplayName("Credenciales incorrectas → responde 401")
    @SuppressWarnings("null")
    void login_wrongPassword_returns401() throws Exception {
        AuthRequest body = new AuthRequest(EMAIL, "wrong!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Email inexistente → responde 401")
    @SuppressWarnings("null")
    void login_unknownEmail_returns401() throws Exception {
        AuthRequest body = new AuthRequest("nadie@vigia.dev", PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // ─── Criterio 2: Token validado en endpoints protegidos ──────────────────

    @Test
    @DisplayName("Endpoint protegido sin token → responde 403")
    void protectedEndpoint_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/test-protegido"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Endpoint protegido con token válido → acepta la request (no 401/403)")
    @SuppressWarnings("null")
    void protectedEndpoint_withValidToken_isAccepted() throws Exception {
        // 1. Hacemos login para obtener el token
        AuthRequest loginBody = new AuthRequest(EMAIL, PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseJson).get("token").asText();

        assertThat(token).isNotBlank();

        // 2. Usamos el token en un endpoint protegido — Spring debería reconocerlo
        // (404 es OK: el endpoint no existe, pero demuestra que el filtro JWT lo dejó
        // pasar)
        mockMvc.perform(get("/api/test-protegido")
                .header("Authorization", "Bearer " + token))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("El JWT fue aceptado — no debería ser 401 ni 403")
                        .isNotIn(401, 403));
    }

    @Test
    @DisplayName("Bloqueo de cuenta tras 5 intentos fallidos (HTTP 423)")
    @SuppressWarnings("null")
    void login_lockoutAfter5FailedAttempts() throws Exception {
        AuthRequest body = new AuthRequest(EMAIL, "wrong!");

        // 5 intentos fallidos (devuelven 401)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());
        }

        // El 6to intento, incluso con clave correcta, debe devolver 423 LOCKED
        AuthRequest correctBody = new AuthRequest(EMAIL, PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(correctBody)))
                .andExpect(status().isLocked());
    }
}
