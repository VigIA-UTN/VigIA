package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("UsuarioController — Tests (Listado y Baja de Productores)")
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private dev.vigia.backend.repository.EstablecimientoRepository establecimientoRepository;

    @Autowired
    private dev.vigia.backend.repository.InvitacionRepository invitacionRepository;

    @Autowired
    private dev.vigia.backend.service.AuthService authService;

    private String adminToken;
    private String productorToken;
    private Long productorId;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() throws Exception {
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        authService.clearCacheForTests();

        // 1. Admin
        Usuario admin = new Usuario();
        admin.setEmail("admin@vigia.dev");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setNombre("Super");
        admin.setRol(Rol.ADMINISTRADOR);
        admin.setActivo(true);
        usuarioRepository.save(admin);

        // 2. Productor Activo
        Usuario productor = new Usuario();
        productor.setEmail("productor@vigia.dev");
        productor.setPasswordHash(passwordEncoder.encode("prod123"));
        productor.setNombre("Juan");
        productor.setRol(Rol.PRODUCTOR);
        productor.setActivo(true);
        productor = usuarioRepository.save(productor);
        productorId = productor.getId();

        // 3. Productor Inactivo (no deberia listarse)
        Usuario productorInactivo = new Usuario();
        productorInactivo.setEmail("inactivo@vigia.dev");
        productorInactivo.setPasswordHash(passwordEncoder.encode("prod123"));
        productorInactivo.setNombre("Pedro");
        productorInactivo.setRol(Rol.PRODUCTOR);
        productorInactivo.setActivo(false);
        usuarioRepository.save(productorInactivo);

        adminToken = getTokenFor("admin@vigia.dev", "admin123");
        productorToken = getTokenFor("productor@vigia.dev", "prod123");
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
    @DisplayName("GET /api/v1/usuarios/productores - Admin ve lista de productores activos")
    void getProductores_asAdmin_success() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/productores")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Juan"))
                .andExpect(jsonPath("$[0].activo").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/usuarios/productores - Productor recibe 403 Forbidden")
    void getProductores_asProductor_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/productores")
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/v1/usuarios/productores/{id} - Admin da de baja lógica a productor")
    @SuppressWarnings("null")
    void desactivarProductor_asAdmin_success() throws Exception {
        mockMvc.perform(delete("/api/v1/usuarios/productores/" + productorId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Usuario user = usuarioRepository.findById(productorId).orElseThrow();
        assertThat(user.isActivo()).isFalse();
    }

    @Test
    @DisplayName("DELETE /api/v1/usuarios/productores/{id} - Productor no puede desactivar y recibe 403")
    void desactivarProductor_asProductor_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/usuarios/productores/" + productorId)
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isForbidden());
    }
}
