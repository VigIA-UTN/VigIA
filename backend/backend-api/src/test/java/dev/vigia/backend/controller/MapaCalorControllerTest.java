package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.client.MlGeoClient;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.ml.geo.AlertaAnimalDTO;
import dev.vigia.backend.dto.ml.geo.CoordenadaGPSDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.dto.ml.geo.MatrizDensidadDTO;
import dev.vigia.backend.exception.MlGeoDegradadoException;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.AuthService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("MapaCalorController Tests")
class MapaCalorControllerTest {

    private static final String ENDPOINT = "/api/v1/mapa-calor";

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private MlGeoClient mlGeoClient;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthService authService;
    @Autowired
    private EstablecimientoRepository establecimientoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private RodeoLoteRepository rodeoLoteRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private CaravanaRepository caravanaRepository;
    @Autowired
    private TelemetriaRepository telemetriaRepository;

    private Long establecimientoId;
    private Long establecimientoAjenoId;
    private String productorToken;

    @SuppressWarnings("null")
    @BeforeEach
    void setUp() throws Exception {
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.flush();
        authService.clearCacheForTests();

        Usuario productor = crearUsuario("productor@vigia.dev", "prod123", Rol.PRODUCTOR);

        Establecimiento est = crearEstablecimiento("Estancia Test", -34.0, -59.0);
        est.setEmpleados(new ArrayList<>());
        est.getEmpleados().add(new Empleado(est, productor));
        establecimientoId = establecimientoRepository.save(est).getId();

        establecimientoAjenoId = establecimientoRepository.save(crearEstablecimiento("Estancia Ajena", -35.0, -60.0))
                .getId();

        productorToken = getTokenFor("productor@vigia.dev", "prod123");
    }

    @Test
    @DisplayName("200: devuelve el análisis de ml-geo tal como llega")
    void devuelveElAnalisis() throws Exception {
        when(mlGeoClient.analyze(any())).thenReturn(respuestaDeMlGeo());

        mockMvc.perform(get(ENDPOINT)
                .param("establecimientoId", establecimientoId.toString())
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapa_calor[0].intensidad").value(0.75))
                .andExpect(jsonPath("$.alertas[0].indice_cohesion").value(0.25))
                .andExpect(jsonPath("$.alertas[0].distancia_centroide_m").value(300.0))
                .andExpect(jsonPath("$.alertas[0].alerta_aislamiento").value(true));
    }

    @Test
    @DisplayName("206: con ml-geo degradado responde listas vacías en vez de romper el mapa")
    void degradaA206() throws Exception {
        when(mlGeoClient.analyze(any())).thenThrow(new MlGeoDegradadoException("ml-geo caído", new RuntimeException()));

        mockMvc.perform(get(ENDPOINT)
                .param("establecimientoId", establecimientoId.toString())
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isPartialContent())
                .andExpect(jsonPath("$.mapa_calor").isEmpty())
                .andExpect(jsonPath("$.alertas").isEmpty());
    }

    @Test
    @DisplayName("403: un productor no puede ver el mapa de un establecimiento ajeno")
    void establecimientoAjeno() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                .param("establecimientoId", establecimientoAjenoId.toString())
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isForbidden());

        verifyNoInteractions(mlGeoClient);
    }

    @Test
    @DisplayName("400: animalId y rodeoId son mutuamente excluyentes")
    void animalYRodeoJuntos() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                .param("establecimientoId", establecimientoId.toString())
                .param("animalId", "1")
                .param("rodeoId", "5")
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(mlGeoClient);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Filtro por rodeo: a ml-geo solo llega la telemetría de los animales del rodeo")
    void filtroPorRodeoFiltraLaTelemetria() throws Exception {
        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();
        RodeoLote rodeo = new RodeoLote(est);
        rodeo.setNombre("Rodeo Norte");
        rodeo = rodeoLoteRepository.save(rodeo);
        Animal delRodeo = crearAnimalConTelemetria(est, rodeo, "DEV-RODEO");
        crearAnimalConTelemetria(est, null, "DEV-SUELTO");
        when(mlGeoClient.analyze(any())).thenReturn(respuestaDeMlGeo());

        mockMvc.perform(get(ENDPOINT)
                .param("establecimientoId", establecimientoId.toString())
                .param("rodeoId", rodeo.getId().toString())
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isOk());

        assertThat(historialEnviadoAMlGeo()).extracting(CoordenadaGPSDTO::getAnimalId)
                .containsExactly(delRodeo.getId().toString());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Filtro por animal: envía la telemetría del rodeo (o establecimiento) y setea el animal_id")
    void filtroPorAnimalFiltraLaTelemetria() throws Exception {
        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();
        Animal elegido = crearAnimalConTelemetria(est, null, "DEV-ELEGIDO");
        Animal otro = crearAnimalConTelemetria(est, null, "DEV-OTRO");
        when(mlGeoClient.analyze(any())).thenReturn(respuestaDeMlGeo());

        mockMvc.perform(get(ENDPOINT)
                .param("establecimientoId", establecimientoId.toString())
                .param("animalId", elegido.getId().toString())
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isOk());

        ArgumentCaptor<GeoAnalyzeRequestDTO> captor = ArgumentCaptor.forClass(GeoAnalyzeRequestDTO.class);
        verify(mlGeoClient).analyze(captor.capture());
        GeoAnalyzeRequestDTO request = captor.getValue();

        assertThat(request.getHistorial7d()).extracting(CoordenadaGPSDTO::getAnimalId)
                .containsExactlyInAnyOrder(elegido.getId().toString(), otro.getId().toString());
        assertThat(request.getAnimalId()).isEqualTo(elegido.getId().toString());
    }

    @Test
    @DisplayName("400: desde posterior a hasta")
    void rangoInvertido() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                .param("establecimientoId", establecimientoId.toString())
                .param("desde", "2026-09-20T00:00:00")
                .param("hasta", "2026-09-10T00:00:00")
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(mlGeoClient);
    }

    @Test
    @DisplayName("401: sin token no se expone el mapa")
    void sinToken() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(mlGeoClient);
    }

    // --- Helpers ---

    /**
     * Animal con caravana y un punto de telemetría de hace una hora (dentro del
     * default de 7 días).
     */
    private Animal crearAnimalConTelemetria(Establecimiento est, RodeoLote rodeo, String devEui) {
        Caravana caravana = new Caravana();
        caravana.setDevEui(devEui);
        caravana.setEstablecimiento(est);
        caravana = caravanaRepository.save(caravana);

        Animal animal = new Animal();
        animal.setEstablecimiento(est);
        animal.setRodeoLote(rodeo);
        animal.setCaravana(caravana);
        animal = animalRepository.save(animal);
        caravana.setAnimal(animal); // lado inverso: el service navega telemetría → caravana → animal

        Telemetria t = new Telemetria();
        t.setCaravana(caravana);
        t.setLatitud(-34.0);
        t.setLongitud(-59.0);
        t.setFechaHora(LocalDateTime.now().minusHours(1));
        telemetriaRepository.save(t);
        return animal;
    }

    private List<CoordenadaGPSDTO> historialEnviadoAMlGeo() {
        ArgumentCaptor<GeoAnalyzeRequestDTO> captor = ArgumentCaptor.forClass(GeoAnalyzeRequestDTO.class);
        verify(mlGeoClient).analyze(captor.capture());
        return captor.getValue().getHistorial7d();
    }

    private GeoAnalyzeResponseDTO respuestaDeMlGeo() {
        return GeoAnalyzeResponseDTO.builder()
                .mapaCalor(List.of(MatrizDensidadDTO.builder().lat(-34.0).lng(-59.0).intensidad(0.75).build()))
                .alertas(List.of(AlertaAnimalDTO.builder()
                        .animalId("1")
                        .indiceCohesion(0.25)
                        .distanciaCentroideM(300.0)
                        .alertaAislamiento(true)
                        .build()))
                .build();
    }

    private Usuario crearUsuario(String email, String password, Rol rol) {
        Usuario u = new Usuario();
        u.setNombre("Test");
        u.setApellido("User");
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRol(rol);
        u.setActivo(true);
        return usuarioRepository.save(u);
    }

    private Establecimiento crearEstablecimiento(String nombre, double lat, double lng) {
        Establecimiento est = new Establecimiento();
        est.setNombre(nombre);
        est.setLatitud(lat);
        est.setLongitud(lng);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        return est;
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
