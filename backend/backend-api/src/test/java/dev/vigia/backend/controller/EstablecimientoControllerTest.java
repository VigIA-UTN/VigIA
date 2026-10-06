package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.SeveridadAlerta;
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
import org.springframework.test.web.servlet.ResultActions;
import java.time.LocalDate;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("EstablecimientoController — Tests")

class EstablecimientoControllerTest {

    @MockBean
    private ConnectionFactory connectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private dev.vigia.backend.repository.EstadoReproductivoRepository estadoReproductivoRepository;
    @Autowired
    private dev.vigia.backend.repository.AnimalRepository animalRepository;
    @Autowired
    private dev.vigia.backend.repository.RodeoLoteRepository rodeoLoteRepository;

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
    private String vetToken;
    private String camperoToken;
    private String otroProductorToken;
    private Long establecimientoId;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() throws Exception {
        estadoReproductivoRepository.deleteAll();
        animalRepository.deleteAll();
        rodeoLoteRepository.deleteAll();
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

        Usuario veterinario = crearUsuario("vet@vigia.dev", Rol.VETERINARIO);
        Usuario campero = crearUsuario("campero@vigia.dev", Rol.CAMPERO);
        crearUsuario("otro@vigia.dev", Rol.PRODUCTOR); // no pertenece al establecimiento

        // 3. Get tokens
        adminToken = getTokenFor("admin@vigia.dev", "admin123");
        userToken = getTokenFor("productor@vigia.dev", "prod123");
        vetToken = getTokenFor("vet@vigia.dev", "pass123");
        camperoToken = getTokenFor("campero@vigia.dev", "pass123");
        otroProductorToken = getTokenFor("otro@vigia.dev", "pass123");

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
        est.getEmpleados().add(new Empleado(est, veterinario));
        est.getEmpleados().add(new Empleado(est, campero));
        est = establecimientoRepository.save(est);
        establecimientoId = est.getId();
    }

    private Usuario crearUsuario(String email, Rol rol) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("pass123"));
        u.setNombre(email);
        u.setRol(rol);
        u.setActivo(true);
        return usuarioRepository.save(u);
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
    @DisplayName("POST /api/v1/establecimientos - Admin crea establecimiento con la configuración de anestro por defecto")
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
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.diasDeteccionAnestro").value(45))
                .andExpect(jsonPath("$.severidadAnestro").value("MEDIA"));
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

    // ── PATCH /{id}/configuracion-anestro ────────────────────────────────────

    private static final String CONFIG_VALIDA = "{\"diasDeteccionAnestro\":30,\"severidadAnestro\":\"ALTA\"}";

    private ResultActions patchConfig(String token, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/establecimientos/" + establecimientoId + "/configuracion-anestro")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("PATCH configuracion-anestro - Productor del establecimiento guarda los cambios")
    @SuppressWarnings("null")
    void configAnestro_asProductorDueno_persiste() throws Exception {
        patchConfig(userToken, CONFIG_VALIDA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasDeteccionAnestro").value(30))
                .andExpect(jsonPath("$.severidadAnestro").value("ALTA"))
                .andExpect(jsonPath("$.nombre").value("Establecimiento Test"));

        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();
        assertThat(est.getDiasDeteccionAnestro()).isEqualTo(30);
        assertThat(est.getSeveridadAnestro()).isEqualTo(SeveridadAlerta.ALTA);
    }

    @Test
    @DisplayName("PATCH configuracion-anestro - Veterinario del establecimiento guarda los cambios")
    void configAnestro_asVeterinarioDelEstablecimiento_ok() throws Exception {
        patchConfig(vetToken, CONFIG_VALIDA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH configuracion-anestro - Datos inválidos devuelven 400")
    void configAnestro_datosInvalidos_badRequest() throws Exception {
        patchConfig(userToken, "{\"diasDeteccionAnestro\":0,\"severidadAnestro\":\"ALTA\"}").andExpect(status().isBadRequest());
        patchConfig(userToken, "{\"diasDeteccionAnestro\":366,\"severidadAnestro\":\"ALTA\"}").andExpect(status().isBadRequest());
        patchConfig(userToken, "{\"diasDeteccionAnestro\":30}").andExpect(status().isBadRequest());
        patchConfig(userToken, "{\"diasDeteccionAnestro\":30,\"severidadAnestro\":\"URGENTE\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH configuracion-anestro - Productor ajeno recibe 403 y no se modifica nada")
    @SuppressWarnings("null")
    void configAnestro_asProductorAjeno_forbidden() throws Exception {
        patchConfig(otroProductorToken, CONFIG_VALIDA).andExpect(status().isForbidden());

        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();
        assertThat(est.getDiasDeteccionAnestro()).isEqualTo(45);
    }

    @Test
    @DisplayName("PATCH configuracion-anestro - Campero recibe 403 aunque pertenezca al establecimiento")
    void configAnestro_asCampero_forbidden() throws Exception {
        patchConfig(camperoToken, CONFIG_VALIDA).andExpect(status().isForbidden());
    }

    // ── PUT /{id}: chequeo de pertenencia ────────────────────────────────────

    private ResultActions putEstablecimiento(String token, String nombre) throws Exception {
        EstablecimientoRequest request = new EstablecimientoRequest();
        request.setNombre(nombre);
        request.setLatitud(-34.0);
        request.setLongitud(-58.0);
        return mockMvc.perform(put("/api/v1/establecimientos/" + establecimientoId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    @Test
    @DisplayName("PUT /api/v1/establecimientos/{id} - Productor ajeno recibe 403 y no se modifica nada")
    @SuppressWarnings("null")
    void updateEstablecimiento_asProductorAjeno_forbidden() throws Exception {
        putEstablecimiento(otroProductorToken, "Hackeado").andExpect(status().isForbidden());

        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();
        assertThat(est.getNombre()).isEqualTo("Establecimiento Test");
    }

    @Test
    @DisplayName("PUT /api/v1/establecimientos/{id} - Productor del establecimiento lo edita sin pisar la configuración de anestro")
    void updateEstablecimiento_asProductorDueno_ok() throws Exception {
        patchConfig(userToken, CONFIG_VALIDA).andExpect(status().isOk());

        putEstablecimiento(userToken, "Renombrado")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Renombrado"))
                .andExpect(jsonPath("$.diasDeteccionAnestro").value(30))
                .andExpect(jsonPath("$.severidadAnestro").value("ALTA"));
    }

    @Test
    @DisplayName("PUT /api/v1/establecimientos/{id} - Admin edita cualquier establecimiento")
    void updateEstablecimiento_asAdmin_ok() throws Exception {
        putEstablecimiento(adminToken, "Editado por admin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Editado por admin"));
    }

    @Test
    @DisplayName("PUT /api/v1/establecimientos/{id} - Veterinario sigue sin poder editar el establecimiento")
    void updateEstablecimiento_asVeterinario_forbidden() throws Exception {
        putEstablecimiento(vetToken, "No deberia").andExpect(status().isForbidden());
    }
}
