package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.CoordenadaDTO;
import dev.vigia.backend.dto.PerimetroRequest;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.*;
import dev.vigia.backend.service.AuthService;
import org.junit.jupiter.api.AfterEach;
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
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("PerimetroController — Tests")
class PerimetroControllerTest {

        @MockBean
        private ConnectionFactory connectionFactory;

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UsuarioRepository usuarioRepository;

        @Autowired
        private EstablecimientoRepository establecimientoRepository;

        @Autowired
        private RodeoLoteRepository rodeoLoteRepository;

        @Autowired
        private PerimetroRepository perimetroRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private AuthService authService;

        private Long establecimientoId;
        private Long loteId;
        private String productorToken;

        @BeforeEach
        @SuppressWarnings("null")
        void setUp() throws Exception {
                perimetroRepository.deleteAll();
                rodeoLoteRepository.deleteAll();
                establecimientoRepository.deleteAll();
                usuarioRepository.deleteAll();
                usuarioRepository.flush();
                authService.clearCacheForTests();

                // 1. Productor
                Usuario productor = new Usuario();
                productor.setEmail("productor@vigia.dev");
                productor.setPasswordHash(passwordEncoder.encode("prod123"));
                productor.setNombre("Productor");
                productor.setRol(Rol.PRODUCTOR);
                productor.setActivo(true);
                productor = usuarioRepository.save(productor);

                // 2. Establecimiento
                Establecimiento establecimiento = new Establecimiento();
                establecimiento.setNombre("Establecimiento Test");
                establecimiento.setLatitud(-34.0);
                establecimiento.setLongitud(-58.0);
                establecimiento.setActivo(true);
                establecimiento.setFechaAlta(LocalDate.now());
                establecimiento.setEmpleados(new ArrayList<>());
                Empleado vinculo = new Empleado(establecimiento, productor);
                establecimiento.getEmpleados().add(vinculo);
                establecimiento = establecimientoRepository.save(establecimiento);
                establecimientoId = establecimiento.getId();

                // 3. RodeoLote
                RodeoLote lote = new RodeoLote(establecimiento);
                lote.setNombre("Lote Alpha");
                lote = rodeoLoteRepository.save(lote);
                loteId = lote.getId();

                // 4. Token
                AuthRequest body = new AuthRequest("productor@vigia.dev", "prod123");
                MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                                .andExpect(status().isOk())
                                .andReturn();
                productorToken = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        }

        @AfterEach
        void tearDown() {
                perimetroRepository.deleteAll();
        }

        private List<CoordenadaDTO> getCoordenadasValidas() {
                return List.of(
                                new CoordenadaDTO(-34.0, -58.0),
                                new CoordenadaDTO(-34.0, -57.0),
                                new CoordenadaDTO(-35.0, -57.0),
                                new CoordenadaDTO(-35.0, -58.0));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("POST /api/v1/perimetros - Productor crea perímetro exitosamente")
        void crearPerimetro_success() throws Exception {
                PerimetroRequest request = PerimetroRequest.builder()
                                .establecimientoId(establecimientoId)
                                .nombre("Perímetro Norte")
                                .descripcion("Potrero norte con acceso a aguada")
                                .loteIds(List.of(loteId))
                                .coordenadas(getCoordenadasValidas())
                                .build();

                mockMvc.perform(post("/api/v1/perimetros")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").isNumber())
                                .andExpect(jsonPath("$.nombre").value("Perímetro Norte"))
                                .andExpect(jsonPath("$.activo").value(true))
                                .andExpect(jsonPath("$.loteIds[0]").value(loteId))
                                .andExpect(jsonPath("$.coordenadas").isArray());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("POST /api/v1/perimetros - Rechaza creación si faltan coordenadas (menos de 3)")
        void crearPerimetro_invalido_menosDe3Puntos() throws Exception {
                PerimetroRequest request = PerimetroRequest.builder()
                                .establecimientoId(establecimientoId)
                                .nombre("Perímetro Invalido")
                                .coordenadas(List.of(
                                                new CoordenadaDTO(-34.0, -58.0),
                                                new CoordenadaDTO(-34.0, -57.0)))
                                .build();

                mockMvc.perform(post("/api/v1/perimetros")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("GET /api/v1/perimetros - Listar perímetros de un establecimiento")
        void listarPerimetros_success() throws Exception {
                // Crear uno primero
                PerimetroRequest request = PerimetroRequest.builder()
                                .establecimientoId(establecimientoId)
                                .nombre("Perímetro 1")
                                .coordenadas(getCoordenadasValidas())
                                .build();

                mockMvc.perform(post("/api/v1/perimetros")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get("/api/v1/perimetros")
                                .param("establecimientoId", establecimientoId.toString())
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].nombre").value("Perímetro 1"));
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("PATCH /api/v1/perimetros/{id}/desactivar y activar - Cambia estado")
        void activarDesactivar_success() throws Exception {
                PerimetroRequest request = PerimetroRequest.builder()
                                .establecimientoId(establecimientoId)
                                .nombre("Perímetro Temporal")
                                .coordenadas(getCoordenadasValidas())
                                .build();

                MvcResult result = mockMvc.perform(post("/api/v1/perimetros")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn();

                long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

                // Desactivar
                mockMvc.perform(patch("/api/v1/perimetros/" + id + "/desactivar")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk());

                // Verificar
                mockMvc.perform(get("/api/v1/perimetros/" + id)
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.activo").value(false));

                // Activar
                mockMvc.perform(patch("/api/v1/perimetros/" + id + "/activar")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk());

                // Verificar activo
                mockMvc.perform(get("/api/v1/perimetros/" + id)
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.activo").value(true));
        }
}
