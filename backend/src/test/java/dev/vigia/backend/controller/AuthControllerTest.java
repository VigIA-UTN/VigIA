package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.UsuarioRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración para la historia de usuario:
 * "Configurar Spring Security con JWT".
 *
 * Criterios de aceptación:
 *   ✅ Tokens generados correctamente al autenticar.
 *   ✅ Tokens validados en endpoints protegidos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AuthController — integración JWT")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String EMAIL    = "productor@vigia.dev";
    private static final String PASSWORD = "secret123";

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();

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
    void login_success_returnsTokenAndUserInfo() throws Exception {
        Map<String, String> body = Map.of("email", EMAIL, "password", PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.rol").value("PRODUCTOR"));
    }

    @Test
    @DisplayName("Credenciales incorrectas → responde 401")
    void login_wrongPassword_returns401() throws Exception {
        Map<String, String> body = Map.of("email", EMAIL, "password", "wrong!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Email inexistente → responde 401")
    void login_unknownEmail_returns401() throws Exception {
        Map<String, String> body = Map.of("email", "nadie@vigia.dev", "password", PASSWORD);

        mockMvc.perform(post("/api/auth/login")
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
    void protectedEndpoint_withValidToken_isAccepted() throws Exception {
        // 1. Hacemos login para obtener el token
        Map<String, String> loginBody = Map.of("email", EMAIL, "password", PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseJson).get("token").asText();

        assertThat(token).isNotBlank();

        // 2. Usamos el token en un endpoint protegido — Spring debería reconocerlo
        // (404 es OK: el endpoint no existe, pero demuestra que el filtro JWT lo dejó pasar)
        mockMvc.perform(get("/api/test-protegido")
                        .header("Authorization", "Bearer " + token))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus())
                                .as("El JWT fue aceptado — no debería ser 401 ni 403")
                                .isNotIn(401, 403)
                );
    }
}
