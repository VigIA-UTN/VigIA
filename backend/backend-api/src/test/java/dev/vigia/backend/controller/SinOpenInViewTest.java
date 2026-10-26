package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import dev.vigia.backend.client.MlGeoClient;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.EstadoReproductivoRequest;
import dev.vigia.backend.dto.ml.geo.CoordenadaGPSDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.model.OrigenEstado;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.RegistroPesoRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rutas que leen asociaciones lazy fuera del controller. Las clases de test con @Transactional no detectan
 * si les falta una transacción: la del test mantiene viva la sesión de Hibernate durante el request. Esta
 * clase no es @Transactional, así que cada request corre como en producción (sin Open Session in View).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Rutas que leen asociaciones lazy, sin Open Session in View")
class SinOpenInViewTest {

    @MockBean
    private ConnectionFactory connectionFactory;
    @MockBean
    private MlGeoClient mlGeoClient;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthService authService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EstablecimientoRepository establecimientoRepository;
    @Autowired
    private InvitacionRepository invitacionRepository;
    @Autowired
    private RodeoLoteRepository rodeoLoteRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private CaravanaRepository caravanaRepository;
    @Autowired
    private TelemetriaRepository telemetriaRepository;
    @Autowired
    private AlertaRepository alertaRepository;
    @Autowired
    private IntervencionRepository intervencionRepository;
    @Autowired
    private EstadoReproductivoRepository estadoReproductivoRepository;
    @Autowired
    private RegistroPesoRepository registroPesoRepository;

    private String token;
    private Long establecimientoId;
    private Animal animal;

    /** Sin rollback: se limpia antes y después, para no dejarle datos a la clase de test que sigue. */
    @AfterEach
    void limpiar() {
        intervencionRepository.deleteAll();
        alertaRepository.deleteAll();
        estadoReproductivoRepository.deleteAll();
        registroPesoRepository.deleteAll();
        telemetriaRepository.deleteAll();
        animalRepository.deleteAll();
        caravanaRepository.deleteAll();
        rodeoLoteRepository.deleteAll();
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        authService.clearCacheForTests();
    }

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() throws Exception {
        limpiar();

        Usuario productor = new Usuario();
        productor.setEmail("productor@vigia.dev");
        productor.setPasswordHash(passwordEncoder.encode("prod123"));
        productor.setNombre("Productor");
        productor.setRol(Rol.PRODUCTOR);
        productor.setActivo(true);
        usuarioRepository.save(productor);

        Establecimiento est = new Establecimiento();
        est.setNombre("Establecimiento Test");
        est.setLatitud(-34.0);
        est.setLongitud(-58.0);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        est.setEmpleados(new ArrayList<>());
        est.getEmpleados().add(new Empleado(est, productor));
        est = establecimientoRepository.save(est);
        establecimientoId = est.getId();

        RodeoLote rodeo = new RodeoLote(est);
        rodeo.setNombre("Rodeo 1");
        rodeo = rodeoLoteRepository.save(rodeo);

        Caravana caravana = new Caravana();
        caravana.setCodigo("CAR-OSIV-001");
        caravana.setDevEui("DEV-OSIV-001");
        caravana.setEstablecimiento(est);
        caravana.setUltimaSenial(LocalDateTime.now().minusMinutes(30));
        caravana = caravanaRepository.save(caravana);

        animal = new Animal(est, rodeo);
        animal.setAlias("Lola");
        animal.setEstadoActual(EstadoAnimal.ACTIVO);
        animal.setCaravana(caravana);
        animal = animalRepository.save(animal);

        token = login("productor@vigia.dev", "prod123");
    }

    @SuppressWarnings("null")
    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    /** Celo PENDIENTE: el listado lee la última señal de la caravana del animal (dato desactualizado). */
    private Alerta crearCeloPendiente() {
        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setDisparador(DisparadorAlerta.CELO);
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setTsGeneracion(LocalDateTime.now());
        alerta.setValorUmbral(80.0);
        alerta.setValorDetectado(95.0);
        return alertaRepository.save(alerta);
    }

    @Test
    @DisplayName("GET /alertas - celo pendiente: devuelve la última señal de la caravana del animal")
    void listarAlertas_celoPendiente_devuelveUltimaSenialDeLaCaravana() throws Exception {
        crearCeloPendiente();

        mockMvc.perform(get("/api/v1/alertas")
                .param("establecimientoId", establecimientoId.toString())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].caravanaUltimaSenial").isNotEmpty());
    }

    @Test
    @DisplayName("GET /alertas/celo - celo pendiente: devuelve la última señal de la caravana del animal")
    void listarAlertasCelo_celoPendiente_devuelveUltimaSenialDeLaCaravana() throws Exception {
        crearCeloPendiente();

        mockMvc.perform(get("/api/v1/alertas/celo")
                .param("establecimientoId", establecimientoId.toString())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].caravanaUltimaSenial").isNotEmpty());
    }

    @Test
    @DisplayName("GET /alertas/stream - reconexión con lastEventId: reenvía la alerta perdida")
    void streamAlertas_reconexion_reenviaLaAlertaPerdida() throws Exception {
        Alerta yaRecibida = crearCeloPendiente();
        Alerta perdida = crearCeloPendiente();

        MvcResult result = mockMvc.perform(get("/api/v1/alertas/stream")
                .param("establecimientoId", establecimientoId.toString())
                .param("lastEventId", yaRecibida.getId().toString())
                .header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("id:" + perdida.getId());
        // Cierra el stream: si no, el request queda abierto hasta el timeout del emitter.
        result.getRequest().getAsyncContext().complete();
    }

    /** El bug original: con Open Session in View, cada stream abierto retenía una conexión hasta 30 min. */
    @Test
    @DisplayName("GET /alertas/stream - el stream abierto no retiene una conexión a la base")
    void streamAlertas_abierto_noRetieneConexion() throws Exception {
        HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        int activasAntes = pool.getActiveConnections();

        MvcResult result = mockMvc.perform(get("/api/v1/alertas/stream")
                .param("establecimientoId", establecimientoId.toString())
                .header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(pool.getActiveConnections()).isEqualTo(activasAntes);
        result.getRequest().getAsyncContext().complete();
    }

    @Test
    @DisplayName("DELETE /animales/{id} - animal con caravana: lo desactiva y libera la caravana")
    @SuppressWarnings("null")
    void desactivarAnimal_conCaravana_liberaLaCaravana() throws Exception {
        Long caravanaId = animal.getCaravana().getId();

        mockMvc.perform(delete("/api/v1/animales/" + animal.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Animal desactivado = animalRepository.findById(animal.getId()).orElseThrow();
        assertThat(desactivado.getEstadoActual()).isEqualTo(EstadoAnimal.INACTIVO);
        assertThat(desactivado.getCaravana()).isNull();
        Caravana liberada = caravanaRepository.findById(caravanaId).orElseThrow();
        assertThat(liberada.isActivo()).isFalse();
        assertThat(liberada.getEstadoSensor()).isEqualTo(EstadoSensor.DISPONIBLE);
    }

    @Test
    @DisplayName("POST /animales/{id}/estado-reproductivo - celo manual: registra el estado y crea la alerta")
    void registrarEstadoReproductivo_celo_creaLaAlertaManual() throws Exception {
        EstadoReproductivoRequest request = new EstadoReproductivoRequest();
        request.setEstado(TipoReproductivo.CELO);

        mockMvc.perform(post("/api/v1/animales/" + animal.getId() + "/estado-reproductivo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CELO"));

        assertThat(alertaRepository.findAll())
                .extracting(Alerta::getTipo)
                .containsExactly("Celo Registrado Manualmente");
    }

    @Test
    @DisplayName("PUT /animales/{id}/estado-reproductivo/{estadoId} - estado existente: lo actualiza")
    void editarEstadoReproductivo_existente_loActualiza() throws Exception {
        EstadoReproductivo existente = new EstadoReproductivo();
        existente.setAnimal(animal);
        existente.setEstado(TipoReproductivo.VACIA);
        existente.setOrigen(OrigenEstado.MANUAL);
        existente.setFechaRegistro(LocalDateTime.now());
        existente = estadoReproductivoRepository.save(existente);
        EstadoReproductivoRequest request = new EstadoReproductivoRequest();
        request.setEstado(TipoReproductivo.INSEMINADA);
        request.setFechaUltimaInseminacion(LocalDate.now().minusDays(1));

        mockMvc.perform(put("/api/v1/animales/" + animal.getId() + "/estado-reproductivo/" + existente.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INSEMINADA"));
    }

    private void crearTelemetria(Caravana caravana) {
        Telemetria t = new Telemetria();
        t.setCaravana(caravana);
        t.setLatitud(-34.0);
        t.setLongitud(-58.0);
        t.setFechaHora(LocalDateTime.now().minusHours(1));
        telemetriaRepository.save(t);
    }

    /** Dos puntos del animal del fixture y uno de una caravana sin animal, que no tiene que llegar a ml-geo. */
    private void crearTelemetriaDelEstablecimiento() {
        crearTelemetria(animal.getCaravana());
        crearTelemetria(animal.getCaravana());
        Caravana sinAnimal = new Caravana();
        sinAnimal.setDevEui("DEV-OSIV-SIN-ANIMAL");
        sinAnimal.setEstablecimiento(animal.getEstablecimiento());
        crearTelemetria(caravanaRepository.save(sinAnimal));
        when(mlGeoClient.analyze(any())).thenReturn(GeoAnalyzeResponseDTO.builder()
                .mapaCalor(new ArrayList<>())
                .alertas(new ArrayList<>())
                .build());
    }

    private List<String> animalesEnviadosAMlGeo() {
        ArgumentCaptor<GeoAnalyzeRequestDTO> captor = ArgumentCaptor.forClass(GeoAnalyzeRequestDTO.class);
        verify(mlGeoClient).analyze(captor.capture());
        return captor.getValue().getHistorial7d().stream().map(CoordenadaGPSDTO::getAnimalId).toList();
    }

    @Test
    @DisplayName("GET /mapa-calor - todo el establecimiento: envía a ml-geo los puntos de los animales con caravana")
    void mapaCalor_establecimiento_enviaLosPuntosDeLosAnimales() throws Exception {
        crearTelemetriaDelEstablecimiento();

        mockMvc.perform(get("/api/v1/mapa-calor")
                .param("establecimientoId", establecimientoId.toString())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(animalesEnviadosAMlGeo())
                .containsExactly(animal.getId().toString(), animal.getId().toString());
    }

    @Test
    @DisplayName("GET /mapa-calor?animalId - filtro por animal: envía a ml-geo los puntos de su rodeo")
    void mapaCalor_filtroPorAnimal_enviaLosPuntosDelRodeo() throws Exception {
        crearTelemetriaDelEstablecimiento();

        mockMvc.perform(get("/api/v1/mapa-calor")
                .param("establecimientoId", establecimientoId.toString())
                .param("animalId", animal.getId().toString())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(animalesEnviadosAMlGeo())
                .containsExactly(animal.getId().toString(), animal.getId().toString());
    }
}
