package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AnimalRequest;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.RodeoRepository;
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

import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("AnimalController — Tests")
class AnimalControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthService authService;

    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private RodeoRepository rodeoRepository;
    @Autowired
    private InvitacionRepository invitacionRepository;
    @Autowired
    private EstablecimientoRepository establecimientoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    private Long establecimientoId;
    private Long rodeoLoteId;

    private String productorToken;
    private String adminToken;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() throws Exception {
        // Orden correcto de borrado (hijos primero)
        animalRepository.deleteAll();
        rodeoRepository.deleteAll();
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        authService.clearCacheForTests();

        // Admin
        Usuario admin = new Usuario();
        admin.setEmail("admin@vigia.dev");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setNombre("Admin");
        admin.setRol(Rol.ADMINISTRADOR);
        admin.setActivo(true);
        usuarioRepository.save(admin);

        // Productor activo
        Usuario productor = new Usuario();
        productor.setEmail("productor@vigia.dev");
        productor.setPasswordHash(passwordEncoder.encode("prod123"));
        productor.setNombre("Productor");
        productor.setRol(Rol.PRODUCTOR);
        productor.setActivo(true);
        productor = usuarioRepository.save(productor);

        // Establecimiento con productor como empleado activo
        Establecimiento est = new Establecimiento();
        est.setNombre("Estancia Test");
        est.setLatitud(-34.0);
        est.setLongitud(-58.0);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        est.setEmpleados(new ArrayList<>());
        est.getEmpleados().add(new Empleado(est, productor));
        est = establecimientoRepository.save(est);
        establecimientoId = est.getId();

        // Rodeo Lote en el establecimiento
        RodeoLote rodeo = new RodeoLote(est);
        rodeo.setNombre("Rodeo Base");
        rodeo = rodeoRepository.save(rodeo);
        rodeoLoteId = rodeo.getId();

        // Tokens
        productorToken = getTokenFor("productor@vigia.dev", "prod123");
        adminToken = getTokenFor("admin@vigia.dev", "admin123");
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

    /** Crea un animal via API y devuelve su id. */
    @SuppressWarnings("null")
    private Long crearAnimal(String nombre, Long estId, Long rodeoId) throws Exception {
        AnimalRequest req = new AnimalRequest();
        req.setAlias(nombre);
        req.setEstablecimientoId(estId);
        req.setRodeoLoteId(rodeoId);
        req.setEstadoActual(EstadoAnimal.ACTIVO);
        req.setFechaNacimiento(LocalDate.of(2022, 1, 1));
        req.setRaza("Angus");

        MvcResult result = mockMvc.perform(post("/api/v1/animales/crear")
                .header("Authorization", "Bearer " + productorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // =========================================================================
    // POST /api/v1/animales/crear
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/animales/crear - Productor carga animal con rodeo exitosamente")
    @SuppressWarnings("null")
    void cargarAnimal_conRodeo_success() throws Exception {
        AnimalRequest req = new AnimalRequest();
        req.setAlias("Toro 1");
        req.setEstablecimientoId(establecimientoId);
        req.setRodeoLoteId(rodeoLoteId);
        req.setEstadoActual(EstadoAnimal.ACTIVO);
        req.setFechaNacimiento(LocalDate.of(2021, 5, 10));
        req.setRaza("Hereford");

        mockMvc.perform(post("/api/v1/animales/crear")
                .header("Authorization", "Bearer " + productorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alias").value("Toro 1"))
                .andExpect(jsonPath("$.raza").value("Hereford"))
                .andExpect(jsonPath("$.estadoActual").value("ACTIVO"));

        assertThat(animalRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/v1/animales/crear - Productor carga animal sin rodeo (rodeoLoteId null)")
    @SuppressWarnings("null")
    void cargarAnimal_sinRodeo_success() throws Exception {
        AnimalRequest req = new AnimalRequest();
        req.setAlias("Vaca Sin Rodeo");
        req.setEstablecimientoId(establecimientoId);
        req.setRodeoLoteId(null);
        req.setEstadoActual(EstadoAnimal.ACTIVO);
        req.setFechaNacimiento(LocalDate.of(2020, 3, 15));
        req.setRaza("Criolla");

        mockMvc.perform(post("/api/v1/animales/crear")
                .header("Authorization", "Bearer " + productorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alias").value("Vaca Sin Rodeo"));

        assertThat(animalRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/v1/animales/crear - Admin recibe 403 Forbidden")
    void cargarAnimal_asAdmin_forbidden() throws Exception {
        AnimalRequest req = new AnimalRequest();
        req.setAlias("Toro Prohibido");
        req.setEstablecimientoId(establecimientoId);
        req.setEstadoActual(EstadoAnimal.ACTIVO);

        mockMvc.perform(post("/api/v1/animales/crear")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        assertThat(animalRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST /api/v1/animales/crear - Sin autenticación recibe 403")
    void cargarAnimal_sinAuth_forbidden() throws Exception {
        AnimalRequest req = new AnimalRequest();
        req.setAlias("Toro Anon");
        req.setEstablecimientoId(establecimientoId);
        req.setEstadoActual(EstadoAnimal.ACTIVO);

        mockMvc.perform(post("/api/v1/animales/crear")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/animales/crear - Establecimiento inexistente retorna 400")
    void cargarAnimal_establecimientoInexistente_badRequest() throws Exception {
        AnimalRequest req = new AnimalRequest();
        req.setAlias("Toro");
        req.setEstablecimientoId(9999L);
        req.setEstadoActual(EstadoAnimal.ACTIVO);

        mockMvc.perform(post("/api/v1/animales/crear")
                .header("Authorization", "Bearer " + productorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/animales/crear - Productor no pertenece al establecimiento retorna 400")
    @SuppressWarnings("null")
    void cargarAnimal_productorNoPertenece_badRequest() throws Exception {
        // Crear otro establecimiento sin el productor
        Establecimiento otroEst = new Establecimiento();
        otroEst.setNombre("Otro Campo");
        otroEst.setLatitud(-35.0);
        otroEst.setLongitud(-59.0);
        otroEst.setActivo(true);
        otroEst.setFechaAlta(LocalDate.now());
        otroEst.setEmpleados(new ArrayList<>());
        otroEst = establecimientoRepository.save(otroEst);

        AnimalRequest req = new AnimalRequest();
        req.setAlias("Toro Ajeno");
        req.setEstablecimientoId(otroEst.getId());
        req.setEstadoActual(EstadoAnimal.ACTIVO);

        mockMvc.perform(post("/api/v1/animales/crear")
                .header("Authorization", "Bearer " + productorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/animales/crear - RodeoLote inexistente retorna 400")
    void cargarAnimal_rodeoInexistente_badRequest() throws Exception {
        AnimalRequest req = new AnimalRequest();
        req.setAlias("Toro");
        req.setEstablecimientoId(establecimientoId);
        req.setRodeoLoteId(9999L);
        req.setEstadoActual(EstadoAnimal.ACTIVO);

        mockMvc.perform(post("/api/v1/animales/crear")
                .header("Authorization", "Bearer " + productorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/v1/animales
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/animales - Admin lista todos los animales")
    @SuppressWarnings("null")
    void getAllAnimales_asAdmin_success() throws Exception {
        crearAnimal("Animal 1", establecimientoId, rodeoLoteId);
        crearAnimal("Animal 2", establecimientoId, rodeoLoteId);

        mockMvc.perform(get("/api/v1/animales")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/animales - Productor recibe 403 Forbidden")
    void getAllAnimales_asProductor_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/animales")
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/animales - Sin autenticación recibe 403")
    void getAllAnimales_sinAuth_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/animales"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // GET /api/v1/animales/{id}
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/animales/{id} - Productor obtiene su animal exitosamente")
    @SuppressWarnings("null")
    void getAnimalById_asProductor_success() throws Exception {
        Long animalId = crearAnimal("Toro Get", establecimientoId, rodeoLoteId);

        mockMvc.perform(get("/api/v1/animales/" + animalId)
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(animalId))
                .andExpect(jsonPath("$.alias").value("Toro Get"));
    }

    @Test
    @DisplayName("GET /api/v1/animales/{id} - Admin obtiene cualquier animal")
    @SuppressWarnings("null")
    void getAnimalById_asAdmin_success() throws Exception {
        Long animalId = crearAnimal("Toro Admin", establecimientoId, rodeoLoteId);

        mockMvc.perform(get("/api/v1/animales/" + animalId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(animalId));
    }

    @Test
    @DisplayName("GET /api/v1/animales/{id} - Animal inexistente retorna 400")
    void getAnimalById_inexistente_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/animales/9999")
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/animales/{id} - Sin autenticación recibe 403")
    void getAnimalById_sinAuth_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/animales/1"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // DELETE /api/v1/animales/{id}
    // =========================================================================

    @Test
    @DisplayName("DELETE /api/v1/animales/{id} - Productor desactiva su animal exitosamente")
    @SuppressWarnings("null")
    void desactivarAnimal_asProductor_success() throws Exception {
        Long animalId = crearAnimal("Toro Delete", establecimientoId, rodeoLoteId);

        mockMvc.perform(delete("/api/v1/animales/" + animalId)
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isOk());

        // Verificar baja lógica en BD
        assertThat(animalRepository.findById(animalId)).isPresent();
        assertThat(animalRepository.findById(animalId).get().getEstadoActual())
                .isEqualTo(EstadoAnimal.INACTIVO);
    }

    @Test
    @DisplayName("DELETE /api/v1/animales/{id} - Animal ya inactivo retorna 400")
    @SuppressWarnings("null")
    void desactivarAnimal_yaInactivo_badRequest() throws Exception {
        Long animalId = crearAnimal("Toro Inactivo", establecimientoId, rodeoLoteId);

        // Desactivar primera vez
        mockMvc.perform(delete("/api/v1/animales/" + animalId)
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isOk());

        // Intentar desactivar de nuevo → 400
        mockMvc.perform(delete("/api/v1/animales/" + animalId)
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/animales/{id} - Animal inexistente retorna 400")
    void desactivarAnimal_inexistente_badRequest() throws Exception {
        mockMvc.perform(delete("/api/v1/animales/9999")
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/animales/{id} - Admin recibe 403 Forbidden")
    void desactivarAnimal_asAdmin_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/animales/1")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/v1/animales/{id} - Productor ajeno al establecimiento recibe 400")
    @SuppressWarnings("null")
    void desactivarAnimal_productorAjeno_badRequest() throws Exception {
        Long animalId = crearAnimal("Toro Ajeno", establecimientoId, rodeoLoteId);

        // Crear segundo productor SIN vínculo con el establecimiento
        Usuario otroProductor = new Usuario();
        otroProductor.setEmail("otro@vigia.dev");
        otroProductor.setPasswordHash(passwordEncoder.encode("otro123"));
        otroProductor.setNombre("Otro");
        otroProductor.setRol(Rol.PRODUCTOR);
        otroProductor.setActivo(true);
        otroProductor = usuarioRepository.save(otroProductor);

        // Darle su propio establecimiento para que pueda autenticarse
        Establecimiento otroEst = new Establecimiento();
        otroEst.setNombre("Otro Campo");
        otroEst.setLatitud(-36.0);
        otroEst.setLongitud(-60.0);
        otroEst.setActivo(true);
        otroEst.setFechaAlta(LocalDate.now());
        otroEst.setEmpleados(new ArrayList<>());
        otroEst.getEmpleados().add(new Empleado(otroEst, otroProductor));
        establecimientoRepository.save(otroEst);

        String otroToken = getTokenFor("otro@vigia.dev", "otro123");

        mockMvc.perform(delete("/api/v1/animales/" + animalId)
                .header("Authorization", "Bearer " + otroToken))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/v1/animales/establecimiento/{id}
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/animales/establecimiento/{id} - Productor lista animales de su establecimiento")
    @SuppressWarnings("null")
    void getAnimalesByEstablecimiento_asProductor_success() throws Exception {
        crearAnimal("Animal Est 1", establecimientoId, rodeoLoteId);
        crearAnimal("Animal Est 2", establecimientoId, null);

        mockMvc.perform(get("/api/v1/animales/establecimiento/" + establecimientoId)
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/animales/establecimiento/{id} - Establecimiento inexistente retorna 400")
    void getAnimalesByEstablecimiento_inexistente_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/animales/establecimiento/9999")
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/animales/establecimiento/{id} - Productor ajeno al establecimiento recibe 400")
    @SuppressWarnings("null")
    void getAnimalesByEstablecimiento_productorAjeno_badRequest() throws Exception {
        // Crear segundo productor sin vínculo al establecimiento base
        Usuario otroProductor = new Usuario();
        otroProductor.setEmail("otro@vigia.dev");
        otroProductor.setPasswordHash(passwordEncoder.encode("otro123"));
        otroProductor.setNombre("Otro");
        otroProductor.setRol(Rol.PRODUCTOR);
        otroProductor.setActivo(true);
        otroProductor = usuarioRepository.save(otroProductor);

        Establecimiento otroEst = new Establecimiento();
        otroEst.setNombre("Otro Campo");
        otroEst.setLatitud(-36.0);
        otroEst.setLongitud(-60.0);
        otroEst.setActivo(true);
        otroEst.setFechaAlta(LocalDate.now());
        otroEst.setEmpleados(new ArrayList<>());
        otroEst.getEmpleados().add(new Empleado(otroEst, otroProductor));
        establecimientoRepository.save(otroEst);

        String otroToken = getTokenFor("otro@vigia.dev", "otro123");

        mockMvc.perform(get("/api/v1/animales/establecimiento/" + establecimientoId)
                .header("Authorization", "Bearer " + otroToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/animales/establecimiento/{id} - Admin recibe 403")
    void getAnimalesByEstablecimiento_asAdmin_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/animales/establecimiento/" + establecimientoId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // GET /api/v1/animales/rodeoLote/{id}
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/animales/rodeoLote/{id} - Productor lista animales del rodeo")
    @SuppressWarnings("null")
    void getAnimalesByRodeoLote_asProductor_success() throws Exception {
        crearAnimal("Animal Rodeo 1", establecimientoId, rodeoLoteId);
        crearAnimal("Animal Rodeo 2", establecimientoId, rodeoLoteId);

        mockMvc.perform(get("/api/v1/animales/rodeoLote/" + rodeoLoteId)
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/animales/rodeoLote/{id} - RodeoLote inexistente retorna 400")
    void getAnimalesByRodeoLote_inexistente_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/animales/rodeoLote/9999")
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/animales/rodeoLote/{id} - Productor ajeno al establecimiento del rodeo recibe 400")
    @SuppressWarnings("null")
    void getAnimalesByRodeoLote_productorAjeno_badRequest() throws Exception {
        Usuario otroProductor = new Usuario();
        otroProductor.setEmail("otro@vigia.dev");
        otroProductor.setPasswordHash(passwordEncoder.encode("otro123"));
        otroProductor.setNombre("Otro");
        otroProductor.setRol(Rol.PRODUCTOR);
        otroProductor.setActivo(true);
        otroProductor = usuarioRepository.save(otroProductor);

        Establecimiento otroEst = new Establecimiento();
        otroEst.setNombre("Otro Campo");
        otroEst.setLatitud(-36.0);
        otroEst.setLongitud(-60.0);
        otroEst.setActivo(true);
        otroEst.setFechaAlta(LocalDate.now());
        otroEst.setEmpleados(new ArrayList<>());
        otroEst.getEmpleados().add(new Empleado(otroEst, otroProductor));
        establecimientoRepository.save(otroEst);

        String otroToken = getTokenFor("otro@vigia.dev", "otro123");

        mockMvc.perform(get("/api/v1/animales/rodeoLote/" + rodeoLoteId)
                .header("Authorization", "Bearer " + otroToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/animales/rodeoLote/{id} - Admin recibe 403")
    void getAnimalesByRodeoLote_asAdmin_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/animales/rodeoLote/" + rodeoLoteId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }
}
