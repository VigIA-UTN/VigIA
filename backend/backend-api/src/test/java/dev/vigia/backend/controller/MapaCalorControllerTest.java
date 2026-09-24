package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.client.MlGeoClient;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.ml.geo.AlertaAnimalDTO;
import dev.vigia.backend.dto.ml.geo.CeldasSubutilizadasDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.dto.ml.geo.MatrizDensidadDTO;
import dev.vigia.backend.exception.MlGeoDegradadoException;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.AuthService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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

    /** Se mockea el cliente para no depender de un ml-geo levantado. */
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
                .andExpect(jsonPath("$.alertas[0].alerta_aislamiento").value(true))
                .andExpect(jsonPath("$.celdas_subutilizadas.lat_step").value(0.05))
                .andExpect(jsonPath("$.celdas_subutilizadas.celdas[0].lat").value(-34.5));
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
    @DisplayName("401: sin token no se expone el mapa")
    void sinToken() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(mlGeoClient);
    }

    // --- Helpers ---

    private GeoAnalyzeResponseDTO respuestaDeMlGeo() {
        return GeoAnalyzeResponseDTO.builder()
                .mapaCalor(List.of(MatrizDensidadDTO.builder().lat(-34.0).lng(-59.0).intensidad(0.75).build()))
                .alertas(List.of(AlertaAnimalDTO.builder()
                        .animalId("1")
                        .indiceCohesion(0.25)
                        .distanciaCentroideM(300.0)
                        .alertaAislamiento(true)
                        .build()))
                .celdasSubutilizadas(CeldasSubutilizadasDTO.builder()
                        .latStep(0.05)
                        .lngStep(0.04)
                        .celdas(List.of(CeldasSubutilizadasDTO.Celda.builder().lat(-34.5).lng(-59.5).build()))
                        .build())
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
