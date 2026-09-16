package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.EstadoSaludRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.AuthService;
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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SensorController — Tests")
class SensorControllerTest {

    @MockBean
    private ConnectionFactory connectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EstablecimientoRepository establecimientoRepository;

    @Autowired
    private CaravanaRepository caravanaRepository;

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private RodeoLoteRepository rodeoLoteRepository;

    @Autowired
    private InvitacionRepository invitacionRepository;

    @Autowired
    private EstadoReproductivoRepository estadoReproductivoRepository;

    @Autowired
    private EstadoSaludRepository estadoSaludRepository;

    @Autowired
    private AlertaRepository alertaRepository;

    @Autowired
    private IntervencionRepository intervencionRepository;

    @Autowired
    private TelemetriaRepository telemetriaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    private String adminToken;
    private String productorToken;
    private Long establecimientoId;
    private Long otroEstablecimientoId;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() throws Exception {
        // Limpieza completa en el mismo orden que DataInitializer, para no chocar con
        // filas que puedan haber quedado de otras clases de test que comparten el
        // mismo contexto/DB H2 dentro de la misma corrida de la suite.
        telemetriaRepository.deleteAll();
        intervencionRepository.deleteAll();
        alertaRepository.deleteAll();
        estadoReproductivoRepository.deleteAll();
        estadoSaludRepository.deleteAll();
        invitacionRepository.deleteAll();
        animalRepository.deleteAll();
        caravanaRepository.deleteAll();
        rodeoLoteRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.flush();
        authService.clearCacheForTests();

        Usuario admin = new Usuario();
        admin.setEmail("admin@vigia.dev");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setNombre("Admin");
        admin.setRol(Rol.ADMINISTRADOR);
        admin.setActivo(true);
        usuarioRepository.save(admin);

        Usuario productor = new Usuario();
        productor.setEmail("productor@vigia.dev");
        productor.setPasswordHash(passwordEncoder.encode("prod123"));
        productor.setNombre("Productor");
        productor.setRol(Rol.PRODUCTOR);
        productor.setActivo(true);
        usuarioRepository.save(productor);

        adminToken = getTokenFor("admin@vigia.dev", "admin123");
        productorToken = getTokenFor("productor@vigia.dev", "prod123");

        Establecimiento est = new Establecimiento();
        est.setNombre("Establecimiento Test");
        est.setLatitud(-34.0);
        est.setLongitud(-58.0);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        est = establecimientoRepository.save(est);
        establecimientoId = est.getId();

        Establecimiento otroEst = new Establecimiento();
        otroEst.setNombre("Otro Establecimiento");
        otroEst.setLatitud(-33.0);
        otroEst.setLongitud(-57.0);
        otroEst.setActivo(true);
        otroEst.setFechaAlta(LocalDate.now());
        otroEst = establecimientoRepository.save(otroEst);
        otroEstablecimientoId = otroEst.getId();
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
    @DisplayName("POST /api/v1/sensores - Admin registra un sensor DISPONIBLE, sin animal")
    void crearSensor_asAdmin_success() throws Exception {
        Map<String, Object> body = Map.of(
                "dev_eui", "AA-BB-CC-DD-01",
                "alias", "Collar Norte",
                "establecimientoId", establecimientoId);

        mockMvc.perform(post("/api/v1/sensores")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.devEui").value("AA-BB-CC-DD-01"))
                .andExpect(jsonPath("$.alias").value("Collar Norte"))
                .andExpect(jsonPath("$.estadoSensor").value("DISPONIBLE"))
                .andExpect(jsonPath("$.establecimientoId").value(establecimientoId))
                .andExpect(jsonPath("$.animalId").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/sensores - Rechaza dev_eui duplicado con 409")
    void crearSensor_devEuiDuplicado_conflict() throws Exception {
        Map<String, Object> body = Map.of(
                "dev_eui", "AA-BB-CC-DD-02",
                "establecimientoId", establecimientoId);

        mockMvc.perform(post("/api/v1/sensores")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/sensores")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/v1/sensores - Usuario no-Admin recibe 403 Forbidden")
    void crearSensor_asProductor_forbidden() throws Exception {
        Map<String, Object> body = Map.of(
                "dev_eui", "AA-BB-CC-DD-03",
                "establecimientoId", establecimientoId);

        mockMvc.perform(post("/api/v1/sensores")
                .header("Authorization", "Bearer " + productorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/sensores - Establecimiento inexistente responde 404")
    void crearSensor_establecimientoInexistente_notFound() throws Exception {
        Map<String, Object> body = Map.of(
                "dev_eui", "AA-BB-CC-DD-04",
                "establecimientoId", 999999L);

        mockMvc.perform(post("/api/v1/sensores")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/sensores - Lista los sensores del establecimiento, con el animal vinculado si corresponde")
    void listarSensores_incluyeDisponibleYVinculado() throws Exception {
        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();

        Caravana disponible = new Caravana();
        disponible.setDevEui("SENSOR-DISPONIBLE");
        disponible.setEstablecimiento(est);
        disponible.setEstadoSensor(EstadoSensor.DISPONIBLE);
        disponible.setActivo(false);
        caravanaRepository.save(disponible);

        Caravana vinculada = new Caravana();
        vinculada.setDevEui("SENSOR-VINCULADO");
        vinculada.setEstablecimiento(est);
        vinculada.setEstadoSensor(EstadoSensor.VINCULADO);
        vinculada.setActivo(true);
        vinculada = caravanaRepository.save(vinculada);

        Animal animal = new Animal(est, null);
        animal.setAlias("Lola");
        animal.setEstadoActual(dev.vigia.backend.model.EstadoAnimal.ACTIVO);
        animal.setCaravana(vinculada);
        animalRepository.save(animal);

        // Sensor de otro establecimiento: no debe aparecer en el listado.
        Establecimiento otroEst = establecimientoRepository.findById(otroEstablecimientoId).orElseThrow();
        Caravana deOtroEstablecimiento = new Caravana();
        deOtroEstablecimiento.setDevEui("SENSOR-OTRO-EST");
        deOtroEstablecimiento.setEstablecimiento(otroEst);
        deOtroEstablecimiento.setEstadoSensor(EstadoSensor.DISPONIBLE);
        caravanaRepository.save(deOtroEstablecimiento);

        mockMvc.perform(get("/api/v1/sensores")
                .param("establecimientoId", establecimientoId.toString())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.devEui == 'SENSOR-DISPONIBLE')].estadoSensor").value("DISPONIBLE"))
                .andExpect(jsonPath("$[?(@.devEui == 'SENSOR-VINCULADO')].estadoSensor").value("VINCULADO"))
                .andExpect(jsonPath("$[?(@.devEui == 'SENSOR-VINCULADO')].animalAlias").value("Lola"));
    }
}
