package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.AuthService;
import jakarta.transaction.Transactional;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("AlertaController Tests")

class AlertaControllerTest {

    @MockBean
    private ConnectionFactory connectionFactory;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthService authService;

    @Autowired
    private AlertaRepository alertaRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private RodeoLoteRepository rodeoLoteRepository;
    @Autowired
    private EstablecimientoRepository establecimientoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private InvitacionRepository invitacionRepository;
    @Autowired
    private EstadoReproductivoRepository estadoReproductivoRepository;

    private Long alertaId;
    private Long alertaOtroEstId;
    private Long establecimientoId;

    private String productorToken;
    private String veterinarioToken;
    private String camperoToken;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() throws Exception {
        // Limpieza en orden (hijos primero)
        alertaRepository.deleteAll();
        estadoReproductivoRepository.deleteAll();
        animalRepository.deleteAll();
        rodeoLoteRepository.deleteAll();
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.flush();
        authService.clearCacheForTests();

        // --- Usuarios ---
        Usuario productor = crearUsuario("productor@vigia.dev", "prod123", Rol.PRODUCTOR);
        Usuario veterinario = crearUsuario("veterinario@vigia.dev", "vet123", Rol.VETERINARIO);
        Usuario campero = crearUsuario("campero@vigia.dev", "camp123", Rol.CAMPERO);

        // --- Establecimiento principal ---
        Establecimiento est = new Establecimiento();
        est.setNombre("Estancia Test");
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

        // Animal en el establecimiento principal
        RodeoLote rodeo = new RodeoLote(est);
        rodeo.setNombre("Rodeo Base");
        rodeo = rodeoLoteRepository.save(rodeo);

        Animal animal = new Animal();
        animal.setIdCaravanaOficial("CAR-001");
        animal.setEstablecimiento(est);
        animal.setRodeoLote(rodeo);
        animal.setEstadoActual(EstadoAnimal.ACTIVO);
        animal.setFechaNacimiento(LocalDate.of(2020, 1, 1));
        animal = animalRepository.save(animal);

        // Alerta PENDIENTE en el establecimiento principal
        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setDisparador(DisparadorAlerta.CELO);
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setTsGeneracion(LocalDateTime.now());
        alerta.setValorUmbral(80.0);
        alerta.setValorDetectado(95.0);
        alerta = alertaRepository.save(alerta);
        alertaId = alerta.getId();

        // --- Establecimiento ajeno (para test de acceso denegado) ---
        Establecimiento otroEst = new Establecimiento();
        otroEst.setNombre("Estancia Ajena");
        otroEst.setLatitud(-35.0);
        otroEst.setLongitud(-59.0);
        otroEst.setActivo(true);
        otroEst.setFechaAlta(LocalDate.now());
        otroEst.setEmpleados(new ArrayList<>());
        otroEst = establecimientoRepository.save(otroEst);

        RodeoLote rodeoAjeno = new RodeoLote(otroEst);
        rodeoAjeno.setNombre("Rodeo Ajeno");
        rodeoAjeno = rodeoLoteRepository.save(rodeoAjeno);

        Animal animalAjeno = new Animal();
        animalAjeno.setIdCaravanaOficial("CAR-002");
        animalAjeno.setEstablecimiento(otroEst);
        animalAjeno.setRodeoLote(rodeoAjeno);
        animalAjeno.setEstadoActual(EstadoAnimal.ACTIVO);
        animalAjeno.setFechaNacimiento(LocalDate.of(2020, 1, 1));
        animalAjeno = animalRepository.save(animalAjeno);

        Alerta alertaAjena = new Alerta();
        alertaAjena.setAnimal(animalAjeno);
        alertaAjena.setDisparador(DisparadorAlerta.CELO);
        alertaAjena.setEstado(EstadoAlerta.PENDIENTE);
        alertaAjena.setTsGeneracion(LocalDateTime.now());
        alertaAjena.setValorUmbral(80.0);
        alertaAjena.setValorDetectado(95.0);
        alertaAjena = alertaRepository.save(alertaAjena);
        alertaOtroEstId = alertaAjena.getId();

        // --- Tokens ---
        productorToken = getTokenFor("productor@vigia.dev", "prod123");
        veterinarioToken = getTokenFor("veterinario@vigia.dev", "vet123");
        camperoToken = getTokenFor("campero@vigia.dev", "camp123");
    }

    // =========================================================================
    // Casos de éxito
    // =========================================================================

    @Test
    @DisplayName("Productor confirma alerta PENDIENTE → 200 con estado CONFIRMADA")
    @SuppressWarnings("null")
    void confirmarAlerta_comoProductor_returns200() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertaId))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("Veterinario descarta alerta PENDIENTE → 200 con estado DESCARTADA")
    @SuppressWarnings("null")
    void descartarAlerta_comoVeterinario_returns200() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + veterinarioToken)
                .param("estado", "DESCARTADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertaId))
                .andExpect(jsonPath("$.estado").value("DESCARTADA"));
    }

    @Test
    @DisplayName("Veterinario confirma alerta PENDIENTE → 200 con estado CONFIRMADA")
    @SuppressWarnings("null")
    void confirmarAlerta_comoVeterinario_returns200() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + veterinarioToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    // =========================================================================
    // Control de acceso por rol
    // =========================================================================

    @Test
    @DisplayName("Campero intenta actualizar alerta → 403 Forbidden")
    @SuppressWarnings("null")
    void actualizarAlerta_comoCampero_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + camperoToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sin token → 403 Forbidden")
    @SuppressWarnings("null")
    void actualizarAlerta_sinToken_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Validaciones de negocio
    // =========================================================================

    @Test
    @DisplayName("Productor intenta actualizar alerta de otro establecimiento → 403")
    @SuppressWarnings("null")
    void actualizarAlerta_deOtroEstablecimiento_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaOtroEstId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Alerta inexistente → 400")
    @SuppressWarnings("null")
    void actualizarAlerta_idInexistente_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", 999999L)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Parámetro estado inválido → 400")
    @SuppressWarnings("null")
    void actualizarAlerta_estadoInvalido_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "ESTADO_INEXISTENTE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Confirmar alerta ya CONFIRMADA → 400 (transición inválida)")
    @SuppressWarnings("null")
    void confirmarAlerta_yaConfirmada_returns400() throws Exception {
        // Primera confirmación
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isOk());

        // Intento de segunda confirmación
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Confirmar alerta ya DESCARTADA → 400 (transición inválida)")
    @SuppressWarnings("null")
    void confirmarAlerta_yaDescartada_returns400() throws Exception {
        // Primero descartar
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "DESCARTADA"))
                .andExpect(status().isOk());

        // Luego intentar confirmar
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Falta el parámetro estado → 400")
    @SuppressWarnings("null")
    void actualizarAlerta_sinParametroEstado_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/v1/alertas/{id} (Detalle)
    // =========================================================================

    @Test
    @DisplayName("Detalle alerta existente → 200 OK")
    @SuppressWarnings("null")
    void obtenerDetalleAlerta_exitoso_returns200() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/v1/alertas/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertaId));
    }

    @Test
    @DisplayName("Detalle alerta inexistente → 404 Not Found")
    @SuppressWarnings("null")
    void obtenerDetalleAlerta_inexistente_returns404() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/alertas/{id}", 999999L)
                        .header("Authorization", "Bearer " + productorToken)
                        .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Detalle alerta de otro establecimiento → 403 Forbidden")
    @SuppressWarnings("null")
    void obtenerDetalleAlerta_accesoCruzado_returns403() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/v1/alertas/{id}", alertaOtroEstId)
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Usuario crearUsuario(String email, String password, Rol rol) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setNombre(rol.name().toLowerCase());
        u.setRol(rol);
        u.setActivo(true);
        u.setFechaAlta(LocalDate.now());
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
}
