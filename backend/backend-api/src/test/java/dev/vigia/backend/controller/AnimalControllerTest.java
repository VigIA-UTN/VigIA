package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AnimalRequest;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.EstadoReproductivoRequest;
import dev.vigia.backend.dto.VincularCaravanaRequest;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.EstadoSalud;
import dev.vigia.backend.model.Intervencion;
import dev.vigia.backend.model.PerfilReferencia;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.TipoEstadoSalud;
import dev.vigia.backend.model.TipoIntervencion;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.EstadoSaludRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.PerfilReferenciaRepository;
import dev.vigia.backend.repository.RegistroCohesionRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AlertaAnimalAisladoRepository;
import dev.vigia.backend.model.AlertaAnimalAislado;
import com.jayway.jsonpath.JsonPath;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.service.AuthService;
import jakarta.persistence.EntityManager;
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
import org.springframework.mock.web.MockMultipartFile;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("AnimalController — Tests")

class AnimalControllerTest {

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
        private AnimalRepository animalRepository;
        @Autowired
        private RodeoLoteRepository rodeoLoteRepository;
        @Autowired
        private InvitacionRepository invitacionRepository;
        @Autowired
        private EstablecimientoRepository establecimientoRepository;
        @Autowired
        private UsuarioRepository usuarioRepository;
        @Autowired
        private EstadoReproductivoRepository estadoReproductivoRepository;
        @Autowired
        private IntervencionRepository intervencionRepository;
        @Autowired
        private CaravanaRepository caravanaRepository;
        @Autowired
        private TelemetriaRepository telemetriaRepository;
        @Autowired
        private EntityManager entityManager;
        @Autowired
        private AlertaRepository alertaRepository;
        @Autowired
        private AlertaAnimalAisladoRepository alertaAnimalAisladoRepository;
        @Autowired
        private PerfilReferenciaRepository perfilReferenciaRepository;
        @Autowired
        private EstadoSaludRepository estadoSaludRepository;
        @Autowired
        private RegistroCohesionRepository registroCohesionRepository;

        private Long establecimientoId;
        private Long rodeoLoteId;

        private String productorToken;
        private String adminToken;
        private String veterinarioToken;
        private String camperoToken;

        @BeforeEach
        @SuppressWarnings("null")
        void setUp() throws Exception {
                // Orden correcto de borrado (hijos primero)
                intervencionRepository.deleteAll();
                alertaRepository.deleteAll();
                telemetriaRepository.deleteAll();
                estadoReproductivoRepository.deleteAll();
                estadoSaludRepository.deleteAll();
                perfilReferenciaRepository.deleteAll();
                registroCohesionRepository.deleteAll();
                animalRepository.deleteAll();
                caravanaRepository.deleteAll();
                rodeoLoteRepository.deleteAll();
                invitacionRepository.deleteAll();
                establecimientoRepository.deleteAll();
                usuarioRepository.deleteAll();
                usuarioRepository.flush();
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

                // Veterinario activo
                Usuario veterinario = new Usuario();
                veterinario.setEmail("veterinario@vigia.dev");
                veterinario.setPasswordHash(passwordEncoder.encode("vet123"));
                veterinario.setNombre("Veterinario");
                veterinario.setRol(Rol.VETERINARIO);
                veterinario.setActivo(true);
                veterinario = usuarioRepository.save(veterinario);

                // Campero activo
                Usuario campero = new Usuario();
                campero.setEmail("campero@vigia.dev");
                campero.setPasswordHash(passwordEncoder.encode("camp123"));
                campero.setNombre("Campero");
                campero.setRol(Rol.CAMPERO);
                campero.setActivo(true);
                campero = usuarioRepository.save(campero);

                // Establecimiento con todos los roles operativos como empleados activos
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

                // RodeoLote en el establecimiento
                RodeoLote rodeoLote = new RodeoLote(est);
                rodeoLote.setNombre("Rodeo Base");
                rodeoLote = rodeoLoteRepository.save(rodeoLote);
                rodeoLoteId = rodeoLote.getId();

                // Tokens
                productorToken = getTokenFor("productor@vigia.dev", "prod123");
                adminToken = getTokenFor("admin@vigia.dev", "admin123");
                veterinarioToken = getTokenFor("veterinario@vigia.dev", "vet123");
                camperoToken = getTokenFor("campero@vigia.dev", "camp123");
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

        @SuppressWarnings("null")
        private Long crearAnimal(String nombre, Long estId, Long rodeoId) throws Exception {
                return crearAnimalConToken(nombre, estId, rodeoId, productorToken);
        }

        @SuppressWarnings("null")
        private Long crearAnimalConToken(String nombre, Long estId, Long rodeoId, String token) throws Exception {
                AnimalRequest req = new AnimalRequest();
                req.setIdCaravanaOficial(UUID.randomUUID().toString());
                req.setAlias(nombre);
                req.setEstablecimientoId(estId);
                req.setRodeoLoteId(rodeoId);
                req.setEstadoActual(EstadoAnimal.ACTIVO);
                req.setFechaNacimiento(LocalDate.of(2022, 1, 1));
                req.setRaza("Angus");

                MvcResult result = mockMvc.perform(post("/api/v1/animales")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isCreated())
                                .andReturn();
                return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        }

        // =========================================================================
        // POST /api/v1/animales
        // =========================================================================

        @Test
        @DisplayName("POST /api/v1/animales/crear - Productor carga animal con rodeoLote exitosamente")
        @SuppressWarnings("null")
        void cargarAnimal_conRodeo_success() throws Exception {
                AnimalRequest req = new AnimalRequest();
                req.setIdCaravanaOficial("CARAVANA-123");
                req.setAlias("Toro 1");
                req.setEstablecimientoId(establecimientoId);
                req.setRodeoLoteId(rodeoLoteId);
                req.setEstadoActual(EstadoAnimal.ACTIVO);
                req.setFechaNacimiento(LocalDate.of(2021, 5, 10));
                req.setRaza("Hereford");

                mockMvc.perform(post("/api/v1/animales")
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
        @DisplayName("POST /api/v1/animales/crear - Productor carga animal sin rodeoLote retorna 400 (rodeo es obligatorio)")
        @SuppressWarnings("null")
        void cargarAnimal_sinRodeo_badRequest() throws Exception {
                AnimalRequest req = new AnimalRequest();
                req.setIdCaravanaOficial("CARAVANA-456");
                req.setAlias("Vaca Sin Rodeo");
                req.setEstablecimientoId(establecimientoId);
                req.setRodeoLoteId(null); // It is now @NotNull
                req.setEstadoActual(EstadoAnimal.ACTIVO);
                req.setFechaNacimiento(LocalDate.of(2020, 3, 15));
                req.setRaza("Criolla");

                mockMvc.perform(post("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/v1/animales/crear - Identificador duplicado en el mismo establecimiento retorna 400")
        @SuppressWarnings("null")
        void cargarAnimal_identificadorDuplicado_badRequest() throws Exception {
                // First animal
                AnimalRequest req1 = new AnimalRequest();
                req1.setIdCaravanaOficial("DUP-123");
                req1.setAlias("Toro Original");
                req1.setEstablecimientoId(establecimientoId);
                req1.setRodeoLoteId(rodeoLoteId);
                req1.setEstadoActual(EstadoAnimal.ACTIVO);
                req1.setFechaNacimiento(LocalDate.of(2021, 5, 10));

                mockMvc.perform(post("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req1)))
                                .andExpect(status().isCreated());

                // Second animal with same idCaravanaOficial
                AnimalRequest req2 = new AnimalRequest();
                req2.setIdCaravanaOficial("DUP-123");
                req2.setAlias("Toro Duplicado");
                req2.setEstablecimientoId(establecimientoId);
                req2.setRodeoLoteId(rodeoLoteId);
                req2.setEstadoActual(EstadoAnimal.ACTIVO);
                req2.setFechaNacimiento(LocalDate.of(2022, 1, 1));

                mockMvc.perform(post("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req2)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/v1/animales/crear - Admin recibe 403 Forbidden")
        @SuppressWarnings("null")
        void cargarAnimal_asAdmin_forbidden() throws Exception {
                AnimalRequest req = new AnimalRequest();
                req.setIdCaravanaOficial(UUID.randomUUID().toString());
                req.setFechaNacimiento(LocalDate.of(2022, 1, 1));
                req.setRodeoLoteId(rodeoLoteId);
                req.setAlias("Toro Prohibido");
                req.setEstablecimientoId(establecimientoId);
                req.setEstadoActual(EstadoAnimal.ACTIVO);

                mockMvc.perform(post("/api/v1/animales")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isForbidden());

                assertThat(animalRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("POST /api/v1/animales/crear - Sin autenticación recibe 401")
        @SuppressWarnings("null")
        void cargarAnimal_sinAuth_forbidden() throws Exception {
                AnimalRequest req = new AnimalRequest();
                req.setIdCaravanaOficial(UUID.randomUUID().toString());
                req.setFechaNacimiento(LocalDate.of(2022, 1, 1));
                req.setRodeoLoteId(rodeoLoteId);
                req.setAlias("Toro Anon");
                req.setEstablecimientoId(establecimientoId);
                req.setEstadoActual(EstadoAnimal.ACTIVO);

                mockMvc.perform(post("/api/v1/animales")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/v1/animales/crear - Establecimiento inexistente retorna 400")
        @SuppressWarnings("null")
        void cargarAnimal_establecimientoInexistente_badRequest() throws Exception {
                AnimalRequest req = new AnimalRequest();
                req.setIdCaravanaOficial(UUID.randomUUID().toString());
                req.setFechaNacimiento(LocalDate.of(2022, 1, 1));
                req.setRodeoLoteId(rodeoLoteId);
                req.setAlias("Toro");
                req.setEstablecimientoId(9999L);
                req.setEstadoActual(EstadoAnimal.ACTIVO);

                mockMvc.perform(post("/api/v1/animales")
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
                req.setIdCaravanaOficial(UUID.randomUUID().toString());
                req.setFechaNacimiento(LocalDate.of(2022, 1, 1));
                req.setRodeoLoteId(rodeoLoteId);
                req.setAlias("Toro Ajeno");
                req.setEstablecimientoId(otroEst.getId());
                req.setEstadoActual(EstadoAnimal.ACTIVO);

                mockMvc.perform(post("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/v1/animales/crear - RodeoLote inexistente retorna 400")
        @SuppressWarnings("null")
        void cargarAnimal_rodeoInexistente_badRequest() throws Exception {
                AnimalRequest req = new AnimalRequest();
                req.setIdCaravanaOficial(UUID.randomUUID().toString());
                req.setFechaNacimiento(LocalDate.of(2022, 1, 1));
                req.setRodeoLoteId(9999L);
                req.setAlias("Toro");
                req.setEstablecimientoId(establecimientoId);
                req.setEstadoActual(EstadoAnimal.ACTIVO);

                mockMvc.perform(post("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest());
        }

        private AnimalRequest animalRequestValido(String alias) {
                AnimalRequest req = new AnimalRequest();
                req.setIdCaravanaOficial(UUID.randomUUID().toString());
                req.setAlias(alias);
                req.setFechaNacimiento(LocalDate.of(2022, 1, 1));
                req.setRodeoLoteId(rodeoLoteId);
                req.setEstablecimientoId(establecimientoId);
                req.setEstadoActual(EstadoAnimal.ACTIVO);
                return req;
        }

        @SuppressWarnings("null")
        private void esperarBadRequest(AnimalRequest req) throws Exception {
                mockMvc.perform(post("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest());
                assertThat(animalRepository.findAll()).noneMatch(a -> req.getIdCaravanaOficial() != null
                                && req.getIdCaravanaOficial().equals(a.getIdCaravanaOficial()));
        }

        @Test
        @DisplayName("POST /api/v1/animales - Sin identificador retorna 400 aunque tenga alias")
        void cargarAnimal_sinIdentificador_badRequest() throws Exception {
                AnimalRequest req = animalRequestValido("Solo Alias");
                req.setIdCaravanaOficial("  ");
                esperarBadRequest(req);
        }

        @Test
        @DisplayName("POST /api/v1/animales - Fecha de nacimiento futura retorna 400")
        void cargarAnimal_fechaFutura_badRequest() throws Exception {
                AnimalRequest req = animalRequestValido("Vaca Futura");
                req.setFechaNacimiento(LocalDate.now().plusDays(1));
                esperarBadRequest(req);
        }

        @Test
        @DisplayName("POST /api/v1/animales - Rodeo de otro establecimiento retorna 400")
        @SuppressWarnings("null")
        void cargarAnimal_rodeoDeOtroEstablecimiento_badRequest() throws Exception {
                Establecimiento otroEst = new Establecimiento();
                otroEst.setNombre("Otro Campo Rodeo");
                otroEst.setLatitud(-35.0);
                otroEst.setLongitud(-59.0);
                otroEst.setActivo(true);
                otroEst.setFechaAlta(LocalDate.now());
                otroEst.setEmpleados(new ArrayList<>());
                otroEst = establecimientoRepository.save(otroEst);
                RodeoLote rodeoAjeno = new RodeoLote(otroEst);
                rodeoAjeno.setNombre("Rodeo Ajeno");
                rodeoAjeno = rodeoLoteRepository.save(rodeoAjeno);

                AnimalRequest req = animalRequestValido("Vaca Rodeo Ajeno");
                req.setRodeoLoteId(rodeoAjeno.getId());
                esperarBadRequest(req);
        }

        @Test
        @DisplayName("POST /api/v1/animales - Rodeo inactivo retorna 400")
        @SuppressWarnings("null")
        void cargarAnimal_rodeoInactivo_badRequest() throws Exception {
                RodeoLote inactivo = new RodeoLote(establecimientoRepository.findById(establecimientoId).get());
                inactivo.setNombre("Rodeo Inactivo");
                inactivo.setActivo(false);
                inactivo = rodeoLoteRepository.save(inactivo);

                AnimalRequest req = animalRequestValido("Vaca Rodeo Inactivo");
                req.setRodeoLoteId(inactivo.getId());
                esperarBadRequest(req);
        }

        @Test
        @DisplayName("POST /api/v1/animales - Alias duplicado en el establecimiento retorna 400")
        void cargarAnimal_aliasDuplicado_badRequest() throws Exception {
                crearAnimal("Lola", establecimientoId, rodeoLoteId);
                esperarBadRequest(animalRequestValido("Lola"));
        }

        // =========================================================================
        // GET /api/v1/animales — listarAnimales
        // =========================================================================

        @Test
        @DisplayName("GET /api/v1/animales - Productor ve los animales de su establecimiento")
        @SuppressWarnings("null")
        void listarAnimales_asProductor_retornaAnimalesDelEstablecimiento() throws Exception {
                crearAnimal("Vaca 1", establecimientoId, rodeoLoteId);
                crearAnimal("Vaca 2", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("GET /api/v1/animales - Veterinario ve los animales de su establecimiento")
        @SuppressWarnings("null")
        void listarAnimales_asVeterinario_retornaAnimalesDelEstablecimiento() throws Exception {
                crearAnimal("Toro Vet", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales")
                                .header("Authorization", "Bearer " + veterinarioToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].alias").value("Toro Vet"));
        }

        @Test
        @DisplayName("GET /api/v1/animales - Campero ve los animales de su establecimiento")
        @SuppressWarnings("null")
        void listarAnimales_asCampero_retornaAnimalesDelEstablecimiento() throws Exception {
                crearAnimal("Toro Camp", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales")
                                .header("Authorization", "Bearer " + camperoToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("GET /api/v1/animales - Admin ve todos los animales del sistema")
        @SuppressWarnings("null")
        void listarAnimales_asAdmin_retornaTodosLosAnimales() throws Exception {
                // Segundo productor con su propio establecimiento
                Usuario otroProductor = new Usuario();
                otroProductor.setEmail("otro@vigia.dev");
                otroProductor.setPasswordHash(passwordEncoder.encode("otro123"));
                otroProductor.setNombre("Otro");
                otroProductor.setRol(Rol.PRODUCTOR);
                otroProductor.setActivo(true);
                otroProductor = usuarioRepository.save(otroProductor);

                Establecimiento otroEst = new Establecimiento();
                otroEst.setNombre("Otro Campo");
                otroEst.setLatitud(-35.0);
                otroEst.setLongitud(-59.0);
                otroEst.setActivo(true);
                otroEst.setFechaAlta(LocalDate.now());
                otroEst.setEmpleados(new ArrayList<>());
                otroEst.getEmpleados().add(new Empleado(otroEst, otroProductor));
                final Establecimiento savedOtroEst = establecimientoRepository.save(otroEst);

                RodeoLote otroRodeo = new RodeoLote(savedOtroEst);
                otroRodeo.setNombre("Otro Rodeo");
                otroRodeo = rodeoLoteRepository.save(otroRodeo);

                final String otroToken = getTokenFor("otro@vigia.dev", "otro123");

                crearAnimal("Animal Est1", establecimientoId, rodeoLoteId);
                crearAnimalConToken("Animal Est2", savedOtroEst.getId(), otroRodeo.getId(), otroToken);

                mockMvc.perform(get("/api/v1/animales")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("GET /api/v1/animales - No devuelve animales INACTIVOS")
        @SuppressWarnings("null")
        void listarAnimales_noRetornaAnimalesInactivos() throws Exception {
                Long animalInactivoId = crearAnimal("Animal Inactivo", establecimientoId, rodeoLoteId);
                crearAnimal("Animal Activo", establecimientoId, rodeoLoteId);

                mockMvc.perform(delete("/api/v1/animales/" + animalInactivoId)
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].alias").value("Animal Activo"));
        }

        @Test
        @DisplayName("GET /api/v1/animales - Incluye animales con estado CON_ALERTA")
        @SuppressWarnings("null")
        void listarAnimales_incluyeAnimalesConAlerta() throws Exception {
                Animal conAlerta = new Animal(establecimientoRepository.findById(establecimientoId).get(), null);
                conAlerta.setAlias("Con Alerta");
                conAlerta.setEstadoActual(EstadoAnimal.CON_ALERTA);
                animalRepository.save(conAlerta);

                crearAnimal("Normal", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("GET /api/v1/animales - No devuelve animales de otros establecimientos")
        @SuppressWarnings("null")
        void listarAnimales_noRetornaAnimalesDeOtroEstablecimiento() throws Exception {
                crearAnimal("Mi Animal", establecimientoId, rodeoLoteId);

                Usuario otroProductor = new Usuario();
                otroProductor.setEmail("otro@vigia.dev");
                otroProductor.setPasswordHash(passwordEncoder.encode("otro123"));
                otroProductor.setNombre("Otro");
                otroProductor.setRol(Rol.PRODUCTOR);
                otroProductor.setActivo(true);
                otroProductor = usuarioRepository.save(otroProductor);

                Establecimiento otroEst = new Establecimiento();
                otroEst.setNombre("Otro Campo");
                otroEst.setLatitud(-35.0);
                otroEst.setLongitud(-59.0);
                otroEst.setActivo(true);
                otroEst.setFechaAlta(LocalDate.now());
                otroEst.setEmpleados(new ArrayList<>());
                otroEst.getEmpleados().add(new Empleado(otroEst, otroProductor));
                final Establecimiento savedOtroEst = establecimientoRepository.save(otroEst);

                RodeoLote otroRodeo = new RodeoLote(savedOtroEst);
                otroRodeo.setNombre("Otro Rodeo");
                otroRodeo = rodeoLoteRepository.save(otroRodeo);

                final String otroToken = getTokenFor("otro@vigia.dev", "otro123");

                crearAnimalConToken("Animal Ajeno", savedOtroEst.getId(), otroRodeo.getId(), otroToken);

                mockMvc.perform(get("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].alias").value("Mi Animal"));
        }

        @Test
        @DisplayName("GET /api/v1/animales - Retorna los campos mínimos requeridos (incluye rodeoLoteNombre)")
        @SuppressWarnings("null")
        void listarAnimales_retornaCamposRequeridos() throws Exception {
                crearAnimal("Toro Campos", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").exists())
                                .andExpect(jsonPath("$[0].alias").value("Toro Campos"))
                                .andExpect(jsonPath("$[0].raza").value("Angus"))
                                .andExpect(jsonPath("$[0].rodeoLoteId").value(rodeoLoteId))
                                .andExpect(jsonPath("$[0].rodeoLoteNombre").value("Rodeo Base"))
                                .andExpect(jsonPath("$[0].estadoActual").value("ACTIVO"));
        }

        @Test
        @DisplayName("GET /api/v1/animales - Retorna el último estado reproductivo del animal")
        @SuppressWarnings("null")
        void listarAnimales_conEstadoReproductivo_retornaCamposCelo() throws Exception {
                Long animalId = crearAnimal("Vaca Reproductiva", establecimientoId, rodeoLoteId);
                Animal animal = animalRepository.findById(animalId).get();

                // Estado reproductivo más antiguo (no debe aparecer)
                EstadoReproductivo estadoViejo = new EstadoReproductivo();
                estadoViejo.setAnimal(animal);
                estadoViejo.setEstado(TipoReproductivo.GESTANTE);
                estadoViejo.setFechaRegistro(java.time.LocalDateTime.now().minusDays(30));
                estadoReproductivoRepository.save(estadoViejo);

                // Estado reproductivo más reciente (debe aparecer en el response)
                EstadoReproductivo estadoActual = new EstadoReproductivo();
                estadoActual.setAnimal(animal);
                estadoActual.setEstado(TipoReproductivo.VACIA);
                estadoActual.setFechaUltParto(LocalDate.of(2025, 7, 1));
                estadoActual.setFechaUltInseminacion(LocalDate.of(2026, 7, 1));
                estadoActual.setFechaRegistro(java.time.LocalDateTime.now());
                estadoReproductivoRepository.save(estadoActual);

                // Flush y clear: vacia la caché L1 de Hibernate para que el @EntityGraph
                // recargue la colección estadosReproductivos desde la BD
                entityManager.flush();
                entityManager.clear();

                mockMvc.perform(get("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].estadoReproductivo").value("VACIA"))
                                .andExpect(jsonPath("$[0].fechaUltimoParto").value("2025-07-01"))
                                .andExpect(jsonPath("$[0].fechaUltimaInseminacion").value("2026-07-01"));
        }

        @Test
        @DisplayName("GET /api/v1/animales - Lista vacía cuando no hay animales activos")
        void listarAnimales_sinAnimales_retornaListaVacia() throws Exception {
                mockMvc.perform(get("/api/v1/animales")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("GET /api/v1/animales - Sin autenticación recibe 401")
        void listarAnimales_sinAuth_forbidden() throws Exception {
                mockMvc.perform(get("/api/v1/animales"))
                                .andExpect(status().isUnauthorized());
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
        @DisplayName("GET /api/v1/animales/{id} - Sin autenticación recibe 401")
        void getAnimalById_sinAuth_forbidden() throws Exception {
                mockMvc.perform(get("/api/v1/animales/1"))
                                .andExpect(status().isUnauthorized());
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

        @Test
        @DisplayName("POST /api/v1/animales/{id}/estado-reproductivo - Productor registra exitosamente")
        @SuppressWarnings("null")
        void registrarEstadoReproductivo_asProductor_success() throws Exception {
                Long animalId = crearAnimal("Vaca 1", establecimientoId, rodeoLoteId);

                EstadoReproductivoRequest request = new EstadoReproductivoRequest();
                request.setEstado(TipoReproductivo.VACIA);
                request.setFechaUltimoParto(LocalDate.now().minusDays(30));
                request.setFechaUltimaInseminacion(LocalDate.now().minusDays(10));

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-reproductivo")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.estado").value("VACIA"))
                                .andExpect(jsonPath("$.origen").value("MANUAL"));
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/estado-reproductivo - Falla por fechas futuras")
        @SuppressWarnings("null")
        void registrarEstadoReproductivo_fechasFuturas_badRequest() throws Exception {
                Long animalId = crearAnimal("Vaca 2", establecimientoId, rodeoLoteId);

                EstadoReproductivoRequest request = new EstadoReproductivoRequest();
                request.setEstado(TipoReproductivo.VACIA);
                request.setFechaUltimoParto(LocalDate.now().plusDays(10));

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-reproductivo")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/estado-reproductivo - Exito con cualquier fecha")
        @SuppressWarnings("null")
        void registrarEstadoReproductivo_fechasCualquiera_success() throws Exception {
                Long animalId = crearAnimal("Vaca 3", establecimientoId, rodeoLoteId);

                EstadoReproductivoRequest request = new EstadoReproductivoRequest();
                request.setEstado(TipoReproductivo.VACIA);
                request.setFechaUltimaInseminacion(LocalDate.now().minusDays(20));
                request.setFechaUltimoParto(LocalDate.now().minusDays(10));

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-reproductivo")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/estado-reproductivo - Admin recibe 403")
        @SuppressWarnings("null")
        void registrarEstadoReproductivo_asAdmin_forbidden() throws Exception {
                Long animalId = crearAnimal("Vaca 4", establecimientoId, rodeoLoteId);

                EstadoReproductivoRequest request = new EstadoReproductivoRequest();
                request.setEstado(TipoReproductivo.VACIA);

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-reproductivo")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PUT /api/v1/animales/{id}/estado-reproductivo/{estadoId} - Productor edita exitosamente")
        @SuppressWarnings("null")
        void editarEstadoReproductivo_asProductor_success() throws Exception {
                Long animalId = crearAnimal("Vaca 5", establecimientoId, rodeoLoteId);

                EstadoReproductivoRequest request = new EstadoReproductivoRequest();
                request.setEstado(TipoReproductivo.VACIA);

                MvcResult createResult = mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-reproductivo")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn();

                Long estadoId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id")
                                .asLong();

                EstadoReproductivoRequest editRequest = new EstadoReproductivoRequest();
                editRequest.setEstado(TipoReproductivo.GESTANTE);
                editRequest.setFechaUltimaInseminacion(LocalDate.now().minusDays(30));

                mockMvc.perform(put("/api/v1/animales/" + animalId + "/estado-reproductivo/" + estadoId)
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(editRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.estado").value("GESTANTE"));
        }

        // =========================================================================
        // GET /api/v1/animales/{id}/estado-reproductivo (historial reproductivo
        // consolidado)
        // =========================================================================

        @Test
        @DisplayName("GET estado-reproductivo - Sale íntegro de estados_reproductivos, ordenado desc")
        @SuppressWarnings("null")
        void historialReproductivo_soloEstadosReproductivos_ordenadoDesc() throws Exception {
                Long animalId = crearAnimal("Vaca Historial", establecimientoId, rodeoLoteId);
                Animal animal = animalRepository.findById(animalId).orElseThrow();
                Usuario productor = usuarioRepository.findByEmail("productor@vigia.dev").orElseThrow();

                // 1) Parto (estado PARIDA manual) - el más antiguo
                EstadoReproductivo manual = new EstadoReproductivo();
                manual.setAnimal(animal);
                manual.setEstado(TipoReproductivo.PARIDA);
                manual.setOrigen(dev.vigia.backend.model.OrigenEstado.MANUAL);
                manual.setUsuarioRegistrador(productor);
                manual.setFechaUltParto(LocalDate.of(2026, 1, 10));
                manual.setFechaRegistro(LocalDateTime.now().minusDays(5));
                estadoReproductivoRepository.save(manual);

                // 2) Celo automático (ml-celo) - intermedio
                EstadoReproductivo automatico = new EstadoReproductivo();
                automatico.setAnimal(animal);
                automatico.setEstado(TipoReproductivo.POSIBLE_CELO);
                automatico.setOrigen(dev.vigia.backend.model.OrigenEstado.AUTOMATICO);
                automatico.setFechaRegistro(LocalDateTime.now().minusDays(2));
                estadoReproductivoRepository.save(automatico);

                // 3) Inseminación: fila INSEMINADA (la crea intervenirAlerta) - la más reciente
                EstadoReproductivo insem = new EstadoReproductivo();
                insem.setAnimal(animal);
                insem.setEstado(TipoReproductivo.INSEMINADA);
                insem.setOrigen(dev.vigia.backend.model.OrigenEstado.MANUAL);
                insem.setUsuarioRegistrador(productor);
                insem.setFechaUltInseminacion(LocalDate.now().minusDays(1));
                insem.setFechaRegistro(LocalDateTime.now().minusDays(1));
                estadoReproductivoRepository.save(insem);

                // Intervención suelta: NO debe aparecer (el historial ya no fusiona
                // intervenciones)
                Intervencion revision = new Intervencion();
                revision.setAnimal(animal);
                revision.setInterventor(productor);
                revision.setDescripcion("Control sanitario");
                revision.setTipo(TipoIntervencion.REVISION_VETERINARIA);
                revision.setFechaHora(LocalDateTime.now());
                intervencionRepository.save(revision);

                entityManager.flush();
                entityManager.clear();

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/estado-reproductivo")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(3))
                                .andExpect(jsonPath("$[0].estado").value("INSEMINADA"))
                                .andExpect(jsonPath("$[0].origen").value("MANUAL"))
                                .andExpect(jsonPath("$[0].registradoPor").value("Productor"))
                                .andExpect(jsonPath("$[1].estado").value("POSIBLE_CELO"))
                                .andExpect(jsonPath("$[1].origen").value("AUTOMATICO"))
                                .andExpect(jsonPath("$[1].registradoPor").isEmpty())
                                .andExpect(jsonPath("$[2].estado").value("PARIDA"))
                                .andExpect(jsonPath("$[2].fechaUltimoParto").value("2026-01-10"))
                                .andExpect(jsonPath("$[2].origen").value("MANUAL"))
                                .andExpect(jsonPath("$[2].registradoPor").value("Productor"));
        }

        @Test
        @DisplayName("GET estado-reproductivo - Ordenado desc; mismo estado con distinto origen (manual vs automático)")
        @SuppressWarnings("null")
        void historialReproductivo_ordenDescYOrigen() throws Exception {
                Long animalId = crearAnimal("Vaca Eventos", establecimientoId, rodeoLoteId);
                Animal animal = animalRepository.findById(animalId).orElseThrow();
                Usuario productor = usuarioRepository.findByEmail("productor@vigia.dev").orElseThrow();

                EstadoReproductivo gestante = new EstadoReproductivo();
                gestante.setAnimal(animal);
                gestante.setEstado(TipoReproductivo.GESTANTE);
                gestante.setOrigen(dev.vigia.backend.model.OrigenEstado.MANUAL);
                gestante.setUsuarioRegistrador(productor);
                gestante.setFechaRegistro(LocalDateTime.now().minusDays(30));
                estadoReproductivoRepository.save(gestante);

                // Celo cargado a mano -> origen MANUAL
                EstadoReproductivo celoManual = new EstadoReproductivo();
                celoManual.setAnimal(animal);
                celoManual.setEstado(TipoReproductivo.CELO);
                celoManual.setOrigen(dev.vigia.backend.model.OrigenEstado.MANUAL);
                celoManual.setUsuarioRegistrador(productor);
                celoManual.setFechaRegistro(LocalDateTime.now().minusDays(20));
                estadoReproductivoRepository.save(celoManual);

                // Celo detectado por ml-celo -> origen AUTOMATICO (mismo estado, distinto
                // origen)
                EstadoReproductivo celoAuto = new EstadoReproductivo();
                celoAuto.setAnimal(animal);
                celoAuto.setEstado(TipoReproductivo.POSIBLE_CELO);
                celoAuto.setOrigen(dev.vigia.backend.model.OrigenEstado.AUTOMATICO);
                celoAuto.setFechaRegistro(LocalDateTime.now().minusDays(10));
                estadoReproductivoRepository.save(celoAuto);

                EstadoReproductivo parida = new EstadoReproductivo();
                parida.setAnimal(animal);
                parida.setEstado(TipoReproductivo.PARIDA);
                parida.setOrigen(dev.vigia.backend.model.OrigenEstado.MANUAL);
                parida.setUsuarioRegistrador(productor);
                parida.setFechaUltParto(LocalDate.of(2026, 8, 1));
                parida.setFechaRegistro(LocalDateTime.now().minusDays(3));
                estadoReproductivoRepository.save(parida);

                entityManager.flush();
                entityManager.clear();

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/estado-reproductivo")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(4))
                                .andExpect(jsonPath("$[0].estado").value("PARIDA"))
                                .andExpect(jsonPath("$[1].estado").value("POSIBLE_CELO"))
                                .andExpect(jsonPath("$[1].origen").value("AUTOMATICO"))
                                .andExpect(jsonPath("$[2].estado").value("CELO"))
                                .andExpect(jsonPath("$[2].origen").value("MANUAL"))
                                .andExpect(jsonPath("$[3].estado").value("GESTANTE"))
                                .andExpect(jsonPath("$[3].origen").value("MANUAL"));
        }

        @Test
        @DisplayName("GET estado-reproductivo - Carga masiva conserva su origen CARGA_MASIVA")
        @SuppressWarnings("null")
        void historialReproductivo_cargaMasiva_origen() throws Exception {
                Long animalId = crearAnimal("Vaca Masiva", establecimientoId, rodeoLoteId);
                Animal animal = animalRepository.findById(animalId).orElseThrow();
                Usuario productor = usuarioRepository.findByEmail("productor@vigia.dev").orElseThrow();

                EstadoReproductivo masivo = new EstadoReproductivo();
                masivo.setAnimal(animal);
                masivo.setEstado(TipoReproductivo.VACIA);
                masivo.setOrigen(dev.vigia.backend.model.OrigenEstado.CARGA_MASIVA);
                masivo.setUsuarioRegistrador(productor);
                masivo.setFechaRegistro(LocalDateTime.now());
                estadoReproductivoRepository.save(masivo);

                entityManager.flush();
                entityManager.clear();

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/estado-reproductivo")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].origen").value("CARGA_MASIVA"));
        }

        @Test
        @DisplayName("GET estado-reproductivo - Campero autorizado (200)")
        void historialReproductivo_comoCampero_ok() throws Exception {
                Long animalId = crearAnimal("Vaca Campero", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/estado-reproductivo")
                                .header("Authorization", "Bearer " + camperoToken))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET estado-reproductivo - Admin ve el historial (bypass de establecimiento)")
        @SuppressWarnings("null")
        void historialReproductivo_comoAdmin_ok() throws Exception {
                Long animalId = crearAnimal("Vaca Admin", establecimientoId, rodeoLoteId);
                Animal animal = animalRepository.findById(animalId).orElseThrow();

                EstadoReproductivo e = new EstadoReproductivo();
                e.setAnimal(animal);
                e.setEstado(TipoReproductivo.VACIA);
                e.setOrigen(dev.vigia.backend.model.OrigenEstado.MANUAL);
                e.setFechaRegistro(LocalDateTime.now());
                estadoReproductivoRepository.save(e);
                entityManager.flush();
                entityManager.clear();

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/estado-reproductivo")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("GET estado-reproductivo - Usuario de otro establecimiento recibe 403")
        @SuppressWarnings("null")
        void historialReproductivo_aislamientoPorEstablecimiento_forbidden() throws Exception {
                Establecimiento est2 = new Establecimiento();
                est2.setNombre("Estancia Ajena");
                est2.setActivo(true);
                est2.setFechaAlta(LocalDate.now());
                est2.setEmpleados(new ArrayList<>());
                est2 = establecimientoRepository.save(est2);

                RodeoLote rodeo2 = new RodeoLote(est2);
                rodeo2.setNombre("Rodeo Ajeno");
                rodeo2 = rodeoLoteRepository.save(rodeo2);

                Animal animalAjeno = new Animal(est2, rodeo2);
                animalAjeno.setAlias("Vaca Ajena");
                animalAjeno.setEstadoActual(EstadoAnimal.ACTIVO);
                animalAjeno = animalRepository.save(animalAjeno);

                mockMvc.perform(get("/api/v1/animales/" + animalAjeno.getId() + "/estado-reproductivo")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isForbidden());
        }

        private MockMultipartFile generateExcel(String[] headers, String[][] rows) throws Exception {
                org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Animales");

                org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                        headerRow.createCell(i).setCellValue(headers[i]);
                }

                for (int r = 0; r < rows.length; r++) {
                        org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
                        for (int c = 0; c < rows[r].length; c++) {
                                if (rows[r][c] != null) {
                                        row.createCell(c).setCellValue(rows[r][c]);
                                }
                        }
                }

                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                workbook.write(bos);
                workbook.close();

                return new MockMultipartFile(
                                "file", "test.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                bos.toByteArray());
        }

        @Test
        @DisplayName("POST /api/v1/animales/csv - Carga Exitosa (Productor)")
        @SuppressWarnings("null")
        void cargarAnimalesExcel_asProductor_success() throws Exception {
                String[] headers = { "IdCaravana", "Alias", "FechaNacimiento", "Raza", "RodeoLote" };
                String[][] rows = {
                                { "X-123", "Vaca Excel", "2020-01-01", "Angus", "Rodeo Base" }
                };

                MockMultipartFile file = generateExcel(headers, rows);

                mockMvc.perform(multipart("/api/v1/animales/csv")
                                .file(file)
                                .param("establecimientoId", establecimientoId.toString())
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.procesados").value(1))
                                .andExpect(jsonPath("$.exitosos").value(1))
                                .andExpect(jsonPath("$.fallidos").value(0))
                                .andExpect(jsonPath("$.animales[0].alias").value("Vaca Excel"))
                                .andExpect(jsonPath("$.animales[0].rodeoLoteId").value(rodeoLoteId));
        }

        @Test
        @DisplayName("POST /api/v1/animales/csv - Fecha y rodeo obligatorios, rodeo inexistente no se crea")
        @SuppressWarnings("null")
        void cargarAnimalesExcel_validacionesAltaManual_noGuardaNada() throws Exception {
                String[] headers = { "IdCaravana", "Alias", "FechaNacimiento", "Raza", "RodeoLote" };
                String[][] rows = {
                                { "X-1", "Vaca OK", "2020-01-01", "Angus", "Rodeo Base" },
                                { "X-2", "Vaca Sin Fecha", null, "Angus", "Rodeo Base" },
                                { "X-3", "Vaca Sin Rodeo", "2020-01-01", "Angus", null },
                                { "X-4", "Vaca Rodeo Nuevo", "2020-01-01", "Angus", "Rodeo Inventado" }
                };
                MockMultipartFile file = generateExcel(headers, rows);

                mockMvc.perform(multipart("/api/v1/animales/csv")
                                .file(file)
                                .param("establecimientoId", establecimientoId.toString())
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.procesados").value(4))
                                .andExpect(jsonPath("$.exitosos").value(0))
                                .andExpect(jsonPath("$.fallidos").value(3))
                                .andExpect(jsonPath("$.errores[0]").value("Fila 3: La fecha de nacimiento es obligatoria."))
                                .andExpect(jsonPath("$.errores[1]").value("Fila 4: El rodeo/lote es obligatorio."))
                                .andExpect(jsonPath("$.errores[2]").value(
                                                "Fila 5: El rodeo/lote 'Rodeo Inventado' no existe o no está activo en el establecimiento."));

                assertThat(animalRepository.findAll()).isEmpty();
                assertThat(rodeoLoteRepository.findByEstablecimientoId(establecimientoId)).hasSize(1);
        }

        @Test
        @DisplayName("POST /api/v1/animales/csv - Rechazo por Rol Incorrecto (Admin)")
        @SuppressWarnings("null")
        void cargarAnimalesExcel_asAdmin_forbidden() throws Exception {
                String[] headers = { "IdCaravana", "Alias", "FechaNacimiento", "Raza", "Establecimiento", "Rodeo" };
                String[][] rows = { { "X-123", "Vaca Excel", "2020-01-01", "Angus", "Estancia Test", "Rodeo Base" } };
                MockMultipartFile file = generateExcel(headers, rows);

                mockMvc.perform(multipart("/api/v1/animales/csv")
                                .file(file)
                                .param("establecimientoId", establecimientoId.toString())
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/v1/animales/csv - Archivo mayor a 5MB")
        void cargarAnimalesExcel_fileTooLarge_badRequest() throws Exception {

                byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB
                MockMultipartFile file = new MockMultipartFile(
                                "file", "large.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                largeContent);

                mockMvc.perform(multipart("/api/v1/animales/csv")
                                .file(file)
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().is4xxClientError());
        }

        // =========================================================================
        // POST /api/v1/animales/estado-reproductivo/csv
        // =========================================================================

        @Test
        @DisplayName("POST /api/v1/animales/estado-reproductivo/csv - Carga Exitosa (Productor)")
        @SuppressWarnings("null")
        void cargarEstadosExcel_asProductor_success() throws Exception {
                // Primero creamos el animal para que pueda encontrarlo
                Animal animal = new Animal();
                animal.setEstablecimiento(establecimientoRepository.findById(establecimientoId).get());
                animal.setEstadoActual(EstadoAnimal.ACTIVO);
                animal.setIdCaravanaOficial("CARAVANA-123");
                animal.setRaza("Angus");
                animal = animalRepository.save(animal);

                String csvContent = "IdCaravana,Alias,Estado,FechaUltimoParto,FechaUltimaInseminacion\n" +
                                animal.getIdCaravanaOficial() + ",,GESTANTE,2023-01-01,2023-02-01\n";

                MockMultipartFile file = new MockMultipartFile("file", "estados.csv", "text/csv",
                                csvContent.getBytes());

                mockMvc.perform(multipart("/api/v1/animales/estado-reproductivo/csv")
                                .file(file)
                                .param("establecimientoId", establecimientoId.toString())
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.procesados").value(1))
                                .andExpect(jsonPath("$.exitosos").value(1))
                                .andExpect(jsonPath("$.fallidos").value(0));
        }

        @Test
        @DisplayName("POST /api/v1/animales/estado-reproductivo/csv - Rechazo por Rol Incorrecto (Admin)")
        @SuppressWarnings("null")
        void cargarEstadosExcel_asAdmin_forbidden() throws Exception {
                String csvContent = "IdAnimal,Estado,FechaUltimoParto,FechaUltimaInseminacion\n1,GESTANTE,,\n";
                MockMultipartFile file = new MockMultipartFile("file", "estados.csv", "text/csv",
                                csvContent.getBytes());

                mockMvc.perform(multipart("/api/v1/animales/estado-reproductivo/csv")
                                .file(file)
                                .param("establecimientoId", establecimientoId.toString())
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isForbidden());
        }

        // =========================================================================
        // GET /api/v1/animales/mapa
        // =========================================================================

        @Test
        @DisplayName("GET /api/v1/animales/mapa - Listado exitoso y validacion de sin_datos_recientes")
        @SuppressWarnings("null")
        void getPosicionesMapa_success() throws Exception {
                // Configurar animal sin posición
                Long animal1Id = crearAnimal("Vaca Sin Pos", establecimientoId, rodeoLoteId);

                // Configurar animal con posición antigua (sin_datos_recientes = true)
                Long animal2Id = crearAnimal("Vaca Antigua", establecimientoId, rodeoLoteId);
                Animal animal2 = animalRepository.findById(animal2Id).orElseThrow();
                Caravana caravana2 = new Caravana();
                caravana2.setCodigo("C-ANTIGUA");
                caravana2.setActivo(true);
                caravana2.setDevEui("DEV-MET-003");
                caravana2 = caravanaRepository.save(caravana2);
                animal2.setCaravana(caravana2);
                animalRepository.save(animal2);

                Telemetria t2 = new Telemetria();
                t2.setCaravana(caravana2);
                t2.setFechaHora(LocalDateTime.now().minusMinutes(20));
                t2.setLatitud(-34.5);
                t2.setLongitud(-58.5);
                telemetriaRepository.save(t2);

                // Configurar animal con posición reciente (sin_datos_recientes = false)
                Long animal3Id = crearAnimal("Vaca Reciente", establecimientoId, rodeoLoteId);
                Animal animal3 = animalRepository.findById(animal3Id).orElseThrow();
                Caravana caravana3 = new Caravana();
                caravana3.setCodigo("C-RECIENTE");
                caravana3.setActivo(true);
                caravana3.setDevEui("DEV-MET-004");
                caravana3 = caravanaRepository.save(caravana3);
                animal3.setCaravana(caravana3);
                animalRepository.save(animal3);

                Telemetria t3 = new Telemetria();
                t3.setCaravana(caravana3);
                t3.setFechaHora(LocalDateTime.now().minusMinutes(5));
                t3.setLatitud(-34.1);
                t3.setLongitud(-58.1);
                telemetriaRepository.save(t3);

                mockMvc.perform(get("/api/v1/animales/mapa")
                                .param("establecimientoId", establecimientoId.toString())
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(3))
                                .andExpect(jsonPath("$[?(@.id == " + animal1Id + ")].sinDatosRecientes").value(true))
                                .andExpect(jsonPath("$[?(@.id == " + animal2Id + ")].sinDatosRecientes").value(true))
                                .andExpect(jsonPath("$[?(@.id == " + animal3Id + ")].sinDatosRecientes").value(false));
        }

        @Test
        @DisplayName("GET /api/v1/animales/mapa - Aislamiento por establecimiento")
        @SuppressWarnings("null")
        void getPosicionesMapa_isolation() throws Exception {
                // Crear otro establecimiento con su rodeo
                Establecimiento est2 = new Establecimiento();
                est2.setNombre("Estancia Aislada");
                est2.setActivo(true);
                est2.setFechaAlta(LocalDate.now());
                est2.setEmpleados(new ArrayList<>()); // sin empleados
                est2 = establecimientoRepository.save(est2);

                RodeoLote rodeo2 = new RodeoLote(est2);
                rodeo2.setNombre("Rodeo 2");
                rodeo2 = rodeoLoteRepository.save(rodeo2);

                Animal animalEst2 = new Animal(est2, rodeo2);
                animalEst2.setAlias("Vaca Est 2");
                animalEst2.setEstadoActual(EstadoAnimal.ACTIVO);
                animalRepository.save(animalEst2);

                // El productor consulta su establecimiento (id 1) y no ve el del est2
                mockMvc.perform(get("/api/v1/animales/mapa")
                                .param("establecimientoId", establecimientoId.toString())
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[?(@.id == " + animalEst2.getId() + ")]").isEmpty());

                // El productor intenta consultar mapa del est2 directamente
                mockMvc.perform(get("/api/v1/animales/mapa")
                                .param("establecimientoId", est2.getId().toString())
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/animales/mapa - Animales con y sin alertas, orden por severidad")
        @SuppressWarnings("null")
        void getPosicionesMapa_conAlertas() throws Exception {
                // 1. Animal sin alertas
                Long animal1Id = crearAnimal("Animal Sin Alertas", establecimientoId, rodeoLoteId);

                // 2. Animal con una alerta
                Long animal2Id = crearAnimal("Animal Una Alerta", establecimientoId, rodeoLoteId);
                Animal animal2 = animalRepository.findById(animal2Id).orElseThrow();
                Alerta alertaGeo = new Alerta();
                alertaGeo.setAnimal(animal2);
                alertaGeo.setDisparador(DisparadorAlerta.ABIGEATO);
                alertaGeo.setTipo("ABIGEATO");
                alertaGeo.setEstado(EstadoAlerta.PENDIENTE);
                alertaGeo.setSeveridad(SeveridadAlerta.MEDIA);
                alertaGeo.setTsGeneracion(LocalDateTime.now());
                alertaGeo.setValorUmbral(0.0);
                alertaGeo.setValorDetectado(0.0);
                alertaRepository.save(alertaGeo);

                // 3. Animal con múltiples alertas
                Long animal3Id = crearAnimal("Animal Multi Alertas", establecimientoId, rodeoLoteId);
                Animal animal3 = animalRepository.findById(animal3Id).orElseThrow();

                // Alerta Sanitaria (Severidad ALTA)
                Alerta alertaSalud = new Alerta();
                alertaSalud.setAnimal(animal3);
                alertaSalud.setDisparador(DisparadorAlerta.SALUD);
                alertaSalud.setTipo("SALUD");
                alertaSalud.setEstado(EstadoAlerta.PENDIENTE);
                alertaSalud.setSeveridad(SeveridadAlerta.ALTA);
                alertaSalud.setTsGeneracion(LocalDateTime.now());
                alertaSalud.setValorUmbral(0.0);
                alertaSalud.setValorDetectado(0.0);
                alertaRepository.save(alertaSalud);

                // Alerta Reproductiva (Severidad BAJA)
                Alerta alertaRepro = new Alerta();
                alertaRepro.setAnimal(animal3);
                alertaRepro.setDisparador(DisparadorAlerta.CELO);
                alertaRepro.setTipo("CELO");
                alertaRepro.setEstado(EstadoAlerta.PENDIENTE);
                alertaRepro.setSeveridad(SeveridadAlerta.BAJA);
                alertaRepro.setTsGeneracion(LocalDateTime.now());
                alertaRepro.setValorUmbral(0.0);
                alertaRepro.setValorDetectado(0.0);
                alertaRepository.save(alertaRepro);

                mockMvc.perform(get("/api/v1/animales/mapa")
                                .param("establecimientoId", establecimientoId.toString())
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                // Animal 1: 0 alertas
                                .andExpect(jsonPath("$[?(@.id == " + animal1Id + ")].alertasAnimal.length()").value(0))
                                // Animal 2: 1 alerta
                                .andExpect(jsonPath("$[?(@.id == " + animal2Id + ")].alertasAnimal.length()").value(1))
                                .andExpect(jsonPath("$[?(@.id == " + animal2Id + ")].alertasAnimal[0].tipo")
                                                .value("ABIGEATO"))
                                .andExpect(jsonPath("$[?(@.id == " + animal2Id + ")].alertasAnimal[0].severidad")
                                                .value("MEDIA"))
                                // Animal 3: 2 alertas, ordenadas por severidad (ALTA primero)
                                .andExpect(jsonPath("$[?(@.id == " + animal3Id + ")].alertasAnimal.length()").value(2))
                                .andExpect(jsonPath("$[?(@.id == " + animal3Id + ")].alertasAnimal[0].severidad")
                                                .value("ALTA"))
                                .andExpect(jsonPath("$[?(@.id == " + animal3Id + ")].alertasAnimal[0].tipo")
                                                .value("SALUD"))
                                .andExpect(jsonPath("$[?(@.id == " + animal3Id + ")].alertasAnimal[1].severidad")
                                                .value("BAJA"))
                                .andExpect(jsonPath("$[?(@.id == " + animal3Id + ")].alertasAnimal[1].tipo")
                                                .value("CELO"));
        }
        @Test
        @DisplayName("GET /api/v1/animales/mapa - Una alerta de aislamiento pendiente aparece en cada animal que lista, con sus animales")
        @SuppressWarnings("null")
        void getPosicionesMapa_aislamiento_enCadaAnimalListado() throws Exception {
                Animal principal = animalRepository.findById(
                                crearAnimal("Principal", establecimientoId, rodeoLoteId)).orElseThrow();
                Animal volvio = animalRepository.findById(
                                crearAnimal("Volvio", establecimientoId, rodeoLoteId)).orElseThrow();
                Long noListadoId = crearAnimal("No Listado", establecimientoId, rodeoLoteId);

                Alerta alerta = new Alerta();
                alerta.setAnimal(principal);
                alerta.setDisparador(DisparadorAlerta.AISLAMIENTO_SOCIAL);
                alerta.setTipo("AISLAMIENTO_SOCIAL");
                alerta.setEstado(EstadoAlerta.PENDIENTE);
                alerta.setSeveridad(SeveridadAlerta.ALTA);
                alerta.setTsGeneracion(LocalDateTime.now());
                alerta.setValorUmbral(150.0);
                alerta.setValorDetectado(400.0);
                alerta = alertaRepository.save(alerta);
                alertaAnimalAisladoRepository.save(AlertaAnimalAislado.builder()
                                .alerta(alerta).animal(principal)
                                .segundosFuera(1800L).distanciaGrupoM(400.0)
                                .tsInicio(LocalDateTime.now().minusMinutes(30))
                                .build());
                alertaAnimalAisladoRepository.save(AlertaAnimalAislado.builder()
                                .alerta(alerta).animal(volvio)
                                .segundosFuera(900L).distanciaGrupoM(200.0)
                                .tsInicio(LocalDateTime.now().minusMinutes(40))
                                .tsRegreso(LocalDateTime.now().minusMinutes(10))
                                .build());

                String json = mockMvc.perform(get("/api/v1/animales/mapa")
                                .param("establecimientoId", establecimientoId.toString())
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[?(@.id == " + principal.getId() + ")].alertasAnimal.length()").value(1))
                                .andExpect(jsonPath("$[?(@.id == " + volvio.getId() + ")].alertasAnimal.length()").value(1))
                                .andExpect(jsonPath("$[?(@.id == " + volvio.getId() + ")].alertasAnimal[0].id").value(alerta.getId().intValue()))
                                .andExpect(jsonPath("$[?(@.id == " + volvio.getId() + ")].alertasAnimal[0].animalesAislados.length()").value(2))
                                .andExpect(jsonPath("$[?(@.id == " + noListadoId + ")].alertasAnimal.length()").value(0))
                                .andReturn().getResponse().getContentAsString();
                List<Object> regreso = JsonPath.read(json, "$[?(@.id == " + volvio.getId()
                                + ")].alertasAnimal[0].animalesAislados[?(@.animalId == " + volvio.getId() + ")].tsRegreso");
                assertThat(regreso).singleElement().isNotNull();

                // Confirmada deja de estar activa: no aparece en ningún pin
                alerta.setEstado(EstadoAlerta.CONFIRMADA);
                alertaRepository.save(alerta);
                mockMvc.perform(get("/api/v1/animales/mapa")
                                .param("establecimientoId", establecimientoId.toString())
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[?(@.id == " + principal.getId() + ")].alertasAnimal.length()").value(0))
                                .andExpect(jsonPath("$[?(@.id == " + volvio.getId() + ")].alertasAnimal.length()").value(0));
        }

        // =========================================================================
        // GET /api/v1/animales/{id}/metricas
        // =========================================================================

        @Test
        @DisplayName("GET /api/v1/animales/{id}/metricas - Historial completo (7 días con datos)")
        @SuppressWarnings("null")
        void getMetricasAnimal_historialCompleto() throws Exception {
                Long animalId = crearAnimal("Vaca Metricas Completa", establecimientoId, rodeoLoteId);
                Animal animal = animalRepository.findById(animalId).orElseThrow();

                Caravana caravana = new Caravana();
                caravana.setCodigo("CAR-MET-001");
                caravana.setFechaAsignacion(LocalDate.now().minusDays(10));
                caravana.setFechaAlta(LocalDate.now().minusDays(10));
                caravana.setDevEui("DEV-MET-001");
                caravanaRepository.save(caravana);

                animal.setCaravana(caravana);
                animalRepository.save(animal);

                LocalDate hoy = LocalDate.now();
                for (int i = 0; i < 7; i++) {
                        Telemetria t = new Telemetria();
                        t.setCaravana(caravana);
                        t.setFechaHora(hoy.minusDays(i).atTime(12, 0));
                        t.setLatitud(-34.0 + i);
                        t.setLongitud(-58.0 - i);
                        t.setMoveState(100 + i * 10);
                        telemetriaRepository.save(t);
                }

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/metricas")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(7))
                                .andExpect(jsonPath("$[0].sinDatos").value(false))
                                .andExpect(jsonPath("$[6].sinDatos").value(false));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/metricas - Días sin datos")
        @SuppressWarnings("null")
        void getMetricasAnimal_diasSinDatos() throws Exception {
                Long animalId = crearAnimal("Vaca Metricas Incompleta", establecimientoId, rodeoLoteId);
                Animal animal = animalRepository.findById(animalId).orElseThrow();

                Caravana caravana = new Caravana();
                caravana.setCodigo("CAR-MET-002");
                caravana.setFechaAsignacion(LocalDate.now().minusDays(10));
                caravana.setFechaAlta(LocalDate.now().minusDays(10));
                caravana.setDevEui("DEV-MET-002");
                caravanaRepository.save(caravana);

                animal.setCaravana(caravana);
                animalRepository.save(animal);

                // Agregamos telemetría solo hace 1 día y hace 2 días (el resto sin datos)
                LocalDate hoy = LocalDate.now();
                for (int i = 1; i <= 2; i++) {
                        Telemetria t = new Telemetria();
                        t.setCaravana(caravana);
                        t.setFechaHora(hoy.minusDays(i).atTime(12, 0));
                        t.setLatitud(-34.0);
                        t.setLongitud(-58.0);
                        t.setMoveState(50);
                        telemetriaRepository.save(t);
                }

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/metricas")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(7))
                                // El último elemento de la lista (hoy) no debería tener datos
                                .andExpect(jsonPath("$[6].sinDatos").value(true))
                                // Hace 1 día debería tener datos
                                .andExpect(jsonPath("$[5].sinDatos").value(false));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/metricas - Aislamiento por establecimiento")
        @SuppressWarnings("null")
        void getMetricasAnimal_aislamientoEstablecimiento() throws Exception {
                // Crear un productor ajeno
                Usuario otroProductor = new Usuario();
                otroProductor.setEmail("otrop@vigia.dev");
                otroProductor.setPasswordHash(passwordEncoder.encode("otro123"));
                otroProductor.setNombre("Otro Productor");
                otroProductor.setRol(Rol.PRODUCTOR);
                otroProductor.setActivo(true);
                otroProductor = usuarioRepository.save(otroProductor);

                Establecimiento otroEst = new Establecimiento();
                otroEst.setNombre("Otro Campo Metricas");
                otroEst.setLatitud(-35.0);
                otroEst.setLongitud(-59.0);
                otroEst.setActivo(true);
                otroEst.setFechaAlta(LocalDate.now());
                otroEst.setEmpleados(new ArrayList<>());
                otroEst.getEmpleados().add(new Empleado(otroEst, otroProductor));
                establecimientoRepository.save(otroEst);

                String otroToken = getTokenFor("otrop@vigia.dev", "otro123");

                Long animalId = crearAnimal("Toro Establecimiento Ajeno", establecimientoId, rodeoLoteId);

                // Intento acceder a métricas de animalId (que es del establecimientoId) usando
                // el otroToken (que pertenece a otroEst)
                mockMvc.perform(get("/api/v1/animales/" + animalId + "/metricas")
                                .header("Authorization", "Bearer " + otroToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/metricas - Rechazo por rol incorrecto (Campero)")
        @SuppressWarnings("null")
        void getMetricasAnimal_rechazoPorRolIncorrecto() throws Exception {
                Long animalId = crearAnimal("Vaca Rol Incorrecto", establecimientoId, rodeoLoteId);

                // Campero no tiene acceso a métricas
                mockMvc.perform(get("/api/v1/animales/" + animalId + "/metricas")
                                .header("Authorization", "Bearer " + camperoToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - Veterinario registra intervención sin alerta → 201")
        @SuppressWarnings("null")
        void registrarIntervencion_veterinario_returns201() throws Exception {
                Long animalId = crearAnimal("Vaca Intervencion", establecimientoId, rodeoLoteId);
                String fechaHora = LocalDateTime.now().minusDays(1).withSecond(0).withNano(0).toString();
                long estadosSaludAntes = estadoSaludRepository.count();
                long estadosReproAntes = estadoReproductivoRepository.count();

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/intervenciones")
                                .header("Authorization", "Bearer " + veterinarioToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"tipo\":\"TRATAMIENTO\",\"fechaHora\":\"" + fechaHora
                                                + "\",\"descripcion\":\"  Pediluvio con sulfato de cobre 5 %  \"}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").isNumber())
                                .andExpect(jsonPath("$.alertaId").doesNotExist())
                                .andExpect(jsonPath("$.animalId").value(animalId))
                                .andExpect(jsonPath("$.interventor").value("Veterinario"))
                                .andExpect(jsonPath("$.tipo").value("TRATAMIENTO"))
                                .andExpect(jsonPath("$.descripcion").value("Pediluvio con sulfato de cobre 5 %"))
                                .andExpect(jsonPath("$.fechaHora").value(org.hamcrest.Matchers.startsWith(fechaHora)));

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/intervenciones")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].descripcion").value("Pediluvio con sulfato de cobre 5 %"));

                assertThat(estadoSaludRepository.count()).isEqualTo(estadosSaludAntes);
                assertThat(estadoReproductivoRepository.count()).isEqualTo(estadosReproAntes);
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - Sin fechaHora usa la hora actual")
        @SuppressWarnings("null")
        void registrarIntervencion_sinFechaHora_usaAhora() throws Exception {
                Long animalId = crearAnimal("Vaca Intervencion Sin Fecha", establecimientoId, rodeoLoteId);

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/intervenciones")
                                .header("Authorization", "Bearer " + veterinarioToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"tipo\":\"VACUNACION\",\"descripcion\":\"Antiaftosa 2 ml SC\"}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.fechaHora").value(org.hamcrest.Matchers.startsWith(
                                                LocalDate.now().toString())));
        }

        private static final String INTERVENCION_OK = "{\"tipo\":\"TRATAMIENTO\",\"descripcion\":\"Ivermectina 1 %\"}";

        @SuppressWarnings("null")
        private org.springframework.test.web.servlet.ResultActions postIntervencion(String token, Long animalId,
                        String body) throws Exception {
                return mockMvc.perform(post("/api/v1/animales/" + animalId + "/intervenciones")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body));
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - PRODUCTOR → 403")
        void registrarIntervencion_productor_returns403() throws Exception {
                Long animalId = crearAnimal("Vaca Intv Productor", establecimientoId, rodeoLoteId);
                postIntervencion(productorToken, animalId, INTERVENCION_OK).andExpect(status().isForbidden());
                assertThat(intervencionRepository.count()).isZero();
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - CAMPERO → 403")
        void registrarIntervencion_campero_returns403() throws Exception {
                Long animalId = crearAnimal("Vaca Intv Campero", establecimientoId, rodeoLoteId);
                postIntervencion(camperoToken, animalId, INTERVENCION_OK).andExpect(status().isForbidden());
                assertThat(intervencionRepository.count()).isZero();
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - ADMINISTRADOR → 403")
        void registrarIntervencion_administrador_returns403() throws Exception {
                Long animalId = crearAnimal("Vaca Intv Admin", establecimientoId, rodeoLoteId);
                postIntervencion(adminToken, animalId, INTERVENCION_OK).andExpect(status().isForbidden());
                assertThat(intervencionRepository.count()).isZero();
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - Veterinario de otro establecimiento → 403")
        @SuppressWarnings("null")
        void registrarIntervencion_veterinarioAjeno_returns403() throws Exception {
                Usuario otroVet = new Usuario();
                otroVet.setEmail("otrovet@vigia.dev");
                otroVet.setPasswordHash(passwordEncoder.encode("otro123"));
                otroVet.setNombre("Otro Vet");
                otroVet.setRol(Rol.VETERINARIO);
                otroVet.setActivo(true);
                otroVet = usuarioRepository.save(otroVet);

                Establecimiento otroEst = new Establecimiento();
                otroEst.setNombre("Otro Campo Intv");
                otroEst.setLatitud(-35.0);
                otroEst.setLongitud(-59.0);
                otroEst.setActivo(true);
                otroEst.setFechaAlta(LocalDate.now());
                otroEst.setEmpleados(new ArrayList<>());
                otroEst.getEmpleados().add(new Empleado(otroEst, otroVet));
                establecimientoRepository.save(otroEst);

                Long animalId = crearAnimal("Vaca Intv Ajena", establecimientoId, rodeoLoteId);
                postIntervencion(getTokenFor("otrovet@vigia.dev", "otro123"), animalId, INTERVENCION_OK)
                                .andExpect(status().isForbidden());
                assertThat(intervencionRepository.count()).isZero();
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - Animal inexistente → 404")
        void registrarIntervencion_animalInexistente_returns404() throws Exception {
                postIntervencion(veterinarioToken, 999999L, INTERVENCION_OK).andExpect(status().isNotFound());
                assertThat(intervencionRepository.count()).isZero();
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - Animal INACTIVO → 400")
        @SuppressWarnings("null")
        void registrarIntervencion_animalInactivo_returns400() throws Exception {
                Long animalId = crearAnimal("Vaca Intv Inactiva", establecimientoId, rodeoLoteId);
                Animal animal = animalRepository.findById(animalId).orElseThrow();
                animal.setEstadoActual(EstadoAnimal.INACTIVO);
                animalRepository.save(animal);

                postIntervencion(veterinarioToken, animalId, INTERVENCION_OK).andExpect(status().isBadRequest());
                assertThat(intervencionRepository.count()).isZero();
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - Sin tipo → 400")
        void registrarIntervencion_sinTipo_returns400() throws Exception {
                Long animalId = crearAnimal("Vaca Intv Sin Tipo", establecimientoId, rodeoLoteId);
                postIntervencion(veterinarioToken, animalId, "{\"descripcion\":\"Algo\"}")
                                .andExpect(status().isBadRequest());
                assertThat(intervencionRepository.count()).isZero();
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - INSEMINACION → 400")
        void registrarIntervencion_inseminacion_returns400() throws Exception {
                Long animalId = crearAnimal("Vaca Intv Inseminacion", establecimientoId, rodeoLoteId);
                postIntervencion(veterinarioToken, animalId, "{\"tipo\":\"INSEMINACION\",\"descripcion\":\"IA\"}")
                                .andExpect(status().isBadRequest());
                assertThat(intervencionRepository.count()).isZero();
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - Descripción vacía → 400")
        void registrarIntervencion_descripcionVacia_returns400() throws Exception {
                Long animalId = crearAnimal("Vaca Intv Desc Vacia", establecimientoId, rodeoLoteId);
                postIntervencion(veterinarioToken, animalId, "{\"tipo\":\"OTRO\",\"descripcion\":\"   \"}")
                                .andExpect(status().isBadRequest());
                assertThat(intervencionRepository.count()).isZero();
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - Sin descripción → 400")
        void registrarIntervencion_sinDescripcion_returns400() throws Exception {
                Long animalId = crearAnimal("Vaca Intv Sin Desc", establecimientoId, rodeoLoteId);
                postIntervencion(veterinarioToken, animalId, "{\"tipo\":\"OTRO\"}").andExpect(status().isBadRequest());
                assertThat(intervencionRepository.count()).isZero();
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - Descripción de más de 1000 caracteres → 400")
        void registrarIntervencion_descripcionMuyLarga_returns400() throws Exception {
                Long animalId = crearAnimal("Vaca Intv Desc Larga", establecimientoId, rodeoLoteId);
                postIntervencion(veterinarioToken, animalId,
                                "{\"tipo\":\"OTRO\",\"descripcion\":\"" + "a".repeat(1001) + "\"}")
                                .andExpect(status().isBadRequest());
                assertThat(intervencionRepository.count()).isZero();
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/intervenciones - fechaHora futura → 400")
        void registrarIntervencion_fechaFutura_returns400() throws Exception {
                Long animalId = crearAnimal("Vaca Intv Futura", establecimientoId, rodeoLoteId);
                String manana = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0).toString();
                postIntervencion(veterinarioToken, animalId,
                                "{\"tipo\":\"OTRO\",\"fechaHora\":\"" + manana + "\",\"descripcion\":\"Futuro\"}")
                                .andExpect(status().isBadRequest());
                assertThat(intervencionRepository.count()).isZero();
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/estado-salud - Éxito (Productor)")
        @SuppressWarnings("null")
        void registrarEstadoSalud_exito() throws Exception {
                Long animalId = crearAnimal("Vaca Enferma", establecimientoId, rodeoLoteId);

                dev.vigia.backend.dto.EstadoSaludRequest request = new dev.vigia.backend.dto.EstadoSaludRequest();
                request.setEstado(dev.vigia.backend.model.TipoEstadoSalud.ENFERMO);
                request.setDiagnosticoNotas("Presenta fiebre");
                request.setTratamientoIndicado("Antibioticos 500mg");

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-salud")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.estado").value("ENFERMO"))
                                .andExpect(jsonPath("$.diagnosticoNotas").value("Presenta fiebre"))
                                .andExpect(jsonPath("$.origen").value("MANUAL"));
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/estado-salud - Rechazo por rol incorrecto (Campero)")
        @SuppressWarnings("null")
        void registrarEstadoSalud_rechazoPorRolIncorrecto() throws Exception {
                Long animalId = crearAnimal("Vaca Rol Incorrecto 2", establecimientoId, rodeoLoteId);

                dev.vigia.backend.dto.EstadoSaludRequest request = new dev.vigia.backend.dto.EstadoSaludRequest();
                request.setEstado(dev.vigia.backend.model.TipoEstadoSalud.SANO);

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-salud")
                                .header("Authorization", "Bearer " + camperoToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/estado-salud - Aislamiento por establecimiento")
        @SuppressWarnings("null")
        void registrarEstadoSalud_aislamientoEstablecimiento() throws Exception {
                // Crear un productor ajeno
                Usuario otroProductor = new Usuario();
                otroProductor.setEmail("otropsalud@vigia.dev");
                otroProductor.setPasswordHash(passwordEncoder.encode("otro123"));
                otroProductor.setNombre("Otro Productor Salud");
                otroProductor.setRol(Rol.PRODUCTOR);
                otroProductor.setActivo(true);
                otroProductor = usuarioRepository.save(otroProductor);

                Establecimiento otroEst = new Establecimiento();
                otroEst.setNombre("Otro Campo Salud");
                otroEst.setLatitud(-35.0);
                otroEst.setLongitud(-59.0);
                otroEst.setActivo(true);
                otroEst.setFechaAlta(LocalDate.now());
                otroEst.setEmpleados(new ArrayList<>());
                otroEst.getEmpleados().add(new Empleado(otroEst, otroProductor));
                establecimientoRepository.save(otroEst);

                String otroToken = getTokenFor("otropsalud@vigia.dev", "otro123");

                Long animalId = crearAnimal("Vaca Establecimiento Ajeno Salud", establecimientoId, rodeoLoteId);

                dev.vigia.backend.dto.EstadoSaludRequest request = new dev.vigia.backend.dto.EstadoSaludRequest();
                request.setEstado(dev.vigia.backend.model.TipoEstadoSalud.SANO);

                // Intento registrar estado de salud de animalId (del establecimientoId) usando
                // el otroToken (de otroEst)
                mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-salud")
                                .header("Authorization", "Bearer " + otroToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/v1/animales/{id}/estado-salud - Rechazo por estado ausente")
        @SuppressWarnings("null")
        void registrarEstadoSalud_rechazoPorEstadoAusente() throws Exception {
                Long animalId = crearAnimal("Vaca Estado Ausente", establecimientoId, rodeoLoteId);

                dev.vigia.backend.dto.EstadoSaludRequest request = new dev.vigia.backend.dto.EstadoSaludRequest();
                request.setDiagnosticoNotas("Notas sin estado");

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-salud")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/estado-salud - Éxito: devuelve el historial")
        @SuppressWarnings("null")
        void listarEstadosSalud_exito() throws Exception {
                Long animalId = crearAnimal("Vaca Historial Salud", establecimientoId, rodeoLoteId);

                dev.vigia.backend.dto.EstadoSaludRequest request = new dev.vigia.backend.dto.EstadoSaludRequest();
                request.setEstado(dev.vigia.backend.model.TipoEstadoSalud.EN_OBSERVACION);
                request.setDiagnosticoNotas("Decaída");

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-salud")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/estado-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].estado").value("EN_OBSERVACION"))
                                .andExpect(jsonPath("$[0].diagnosticoNotas").value("Decaída"))
                                .andExpect(jsonPath("$[0].origen").value("MANUAL"));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/estado-salud - Aislamiento por establecimiento")
        @SuppressWarnings("null")
        void listarEstadosSalud_aislamientoEstablecimiento() throws Exception {
                Usuario otroProductor = new Usuario();
                otroProductor.setEmail("otropsaludget@vigia.dev");
                otroProductor.setPasswordHash(passwordEncoder.encode("otro123"));
                otroProductor.setNombre("Otro Productor Salud Get");
                otroProductor.setRol(Rol.PRODUCTOR);
                otroProductor.setActivo(true);
                usuarioRepository.save(otroProductor);

                Establecimiento otroEst = new Establecimiento();
                otroEst.setNombre("Otro Campo Salud Get");
                otroEst.setLatitud(-35.0);
                otroEst.setLongitud(-59.0);
                otroEst.setActivo(true);
                otroEst.setFechaAlta(LocalDate.now());
                otroEst.setEmpleados(new ArrayList<>());
                otroEst.getEmpleados().add(new Empleado(otroEst, otroProductor));
                establecimientoRepository.save(otroEst);

                String otroToken = getTokenFor("otropsaludget@vigia.dev", "otro123");

                Long animalId = crearAnimal("Vaca Ajena Historial Salud", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/estado-salud")
                                .header("Authorization", "Bearer " + otroToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id} - Incluye el último estado de salud manual")
        @SuppressWarnings("null")
        void getAnimalById_incluyeEstadoSalud() throws Exception {
                Long animalId = crearAnimal("Vaca Con Salud", establecimientoId, rodeoLoteId);

                dev.vigia.backend.dto.EstadoSaludRequest request = new dev.vigia.backend.dto.EstadoSaludRequest();
                request.setEstado(dev.vigia.backend.model.TipoEstadoSalud.ENFERMO);

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-salud")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                // Forzar recarga desde la BD (el test corre en una única transacción)
                entityManager.flush();
                entityManager.clear();

                mockMvc.perform(get("/api/v1/animales/" + animalId)
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.estadoSalud").value("ENFERMO"));
        }

        // =========================================================================
        // GET /api/v1/animales/{id}/indicadores-salud
        // =========================================================================

        @SuppressWarnings("null")
        private void asignarCaravanaConMoveState(Long animalId, int moveState) {
                asignarCaravanaConMoveState(animalId, moveState, LocalDateTime.now());
        }

        @SuppressWarnings("null")
        private void asignarCaravanaConMoveState(Long animalId, int moveState, LocalDateTime fechaHoraTelemetria) {
                Animal animal = animalRepository.findById(animalId).orElseThrow();

                Caravana caravana = new Caravana();
                caravana.setCodigo("CAR-IND-" + UUID.randomUUID());
                caravana.setFechaAsignacion(LocalDate.now().minusDays(5));
                caravana.setFechaAlta(LocalDate.now().minusDays(5));
                caravana.setDevEui("DEV-IND-" + UUID.randomUUID());
                caravanaRepository.save(caravana);

                animal.setCaravana(caravana);
                animalRepository.save(animal);

                Telemetria telemetria = new Telemetria();
                telemetria.setCaravana(caravana);
                telemetria.setFechaHora(fechaHoraTelemetria);
                telemetria.setMoveState(moveState);
                telemetriaRepository.save(telemetria);

                PerfilReferencia perfil = new PerfilReferencia();
                perfil.setAnimal(animal);
                perfil.setPerfilJson("{\"mean_moveState_mean\": 100.0, \"mean_moveState_std\": 10.0}");
                perfil.setFechaCalculo(LocalDateTime.now());
                perfilReferenciaRepository.save(perfil);

                entityManager.flush();
                entityManager.clear();
        }

        @SuppressWarnings("null")
        private void asignarEstadoReproductivo(Long animalId, TipoReproductivo tipo) {
                Animal animal = animalRepository.findById(animalId).orElseThrow();
                EstadoReproductivo estado = new EstadoReproductivo();
                estado.setAnimal(animal);
                estado.setEstado(tipo);
                estado.setOrigen(dev.vigia.backend.model.OrigenEstado.MANUAL);
                estado.setFechaRegistro(LocalDateTime.now());
                estadoReproductivoRepository.save(estado);

                entityManager.flush();
                entityManager.clear();
        }

        @SuppressWarnings("null")
        private void asignarEstadoSalud(Long animalId, TipoEstadoSalud tipo) {
                Animal animal = animalRepository.findById(animalId).orElseThrow();
                EstadoSalud estado = new EstadoSalud();
                estado.setAnimal(animal);
                estado.setEstado(tipo);
                estado.setOrigen(dev.vigia.backend.model.OrigenEstado.MANUAL);
                estado.setFechaRegistro(LocalDateTime.now());
                estadoSaludRepository.save(estado);

                entityManager.flush();
                entityManager.clear();
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/indicadores-salud - Devuelve movimiento, estado reproductivo y estado de salud en verde")
        @SuppressWarnings("null")
        void getIndicadoresSalud_verde() throws Exception {
                Long animalId = crearAnimal("Vaca Indicadores Verde", establecimientoId, rodeoLoteId);

                asignarCaravanaConMoveState(animalId, 100); // igual a la media -> VERDE
                asignarEstadoReproductivo(animalId, TipoReproductivo.GESTANTE);
                asignarEstadoSalud(animalId, TipoEstadoSalud.SANO);

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.animalId").value(animalId))
                                .andExpect(jsonPath("$.indicadores.length()").value(3))
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de movimiento')].estado")
                                                .value("VERDE"))
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de movimiento')].descripcion")
                                                .value(org.hamcrest.Matchers
                                                                .not(org.hamcrest.Matchers.emptyOrNullString())))
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado reproductivo')].estado")
                                                .value("VERDE"))
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado reproductivo')].descripcion")
                                                .value(org.hamcrest.Matchers
                                                                .not(org.hamcrest.Matchers.emptyOrNullString())))
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de salud')].estado")
                                                .value("VERDE"))
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de salud')].descripcion")
                                                .value(org.hamcrest.Matchers
                                                                .not(org.hamcrest.Matchers.emptyOrNullString())));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/indicadores-salud - Movimiento en amarillo y rojo según desvío respecto al perfil")
        @SuppressWarnings("null")
        void getIndicadoresSalud_movimientoAmarilloYRojo() throws Exception {
                Long animalAmarilloId = crearAnimal("Vaca Indicadores Amarillo", establecimientoId, rodeoLoteId);
                asignarCaravanaConMoveState(animalAmarilloId, 115); // 1.5 std por encima de la media -> AMARILLO

                Long animalRojoId = crearAnimal("Vaca Indicadores Rojo", establecimientoId, rodeoLoteId);
                asignarCaravanaConMoveState(animalRojoId, 200); // muy por encima de la media -> ROJO

                mockMvc.perform(get("/api/v1/animales/" + animalAmarilloId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de movimiento')].estado")
                                                .value("AMARILLO"));

                mockMvc.perform(get("/api/v1/animales/" + animalRojoId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de movimiento')].estado")
                                                .value("ROJO"));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/indicadores-salud - Estado reproductivo en amarillo (posible celo) y rojo (anestro)")
        @SuppressWarnings("null")
        void getIndicadoresSalud_reproductivoAmarilloYRojo() throws Exception {
                Long animalAmarilloId = crearAnimal("Vaca Reproductivo Amarillo", establecimientoId, rodeoLoteId);
                asignarEstadoReproductivo(animalAmarilloId, TipoReproductivo.POSIBLE_CELO);

                Long animalRojoId = crearAnimal("Vaca Reproductivo Rojo", establecimientoId, rodeoLoteId);
                asignarEstadoReproductivo(animalRojoId, TipoReproductivo.ANESTRO);

                mockMvc.perform(get("/api/v1/animales/" + animalAmarilloId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado reproductivo')].estado")
                                                .value("AMARILLO"));

                mockMvc.perform(get("/api/v1/animales/" + animalRojoId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado reproductivo')].estado")
                                                .value("ROJO"));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/indicadores-salud - Animal sin caravana, estado reproductivo ni estado de salud devuelve SIN DATOS (no 500)")
        @SuppressWarnings("null")
        void getIndicadoresSalud_sinDatos() throws Exception {
                Long animalId = crearAnimal("Vaca Sin Caravana", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.caravana").value(org.hamcrest.Matchers.nullValue()))
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de movimiento')].estado")
                                                .value("SIN DATOS"))
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado reproductivo')].estado")
                                                .value("SIN DATOS"))
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de salud')].estado")
                                                .value("SIN DATOS"));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/indicadores-salud - Estado de salud en amarillo (en observación) y rojo (enfermo)")
        @SuppressWarnings("null")
        void getIndicadoresSalud_estadoSaludAmarilloYRojo() throws Exception {
                Long animalAmarilloId = crearAnimal("Vaca Salud Amarillo", establecimientoId, rodeoLoteId);
                asignarEstadoSalud(animalAmarilloId, TipoEstadoSalud.EN_OBSERVACION);

                Long animalRojoId = crearAnimal("Vaca Salud Rojo", establecimientoId, rodeoLoteId);
                asignarEstadoSalud(animalRojoId, TipoEstadoSalud.ENFERMO);

                mockMvc.perform(get("/api/v1/animales/" + animalAmarilloId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de salud')].estado")
                                                .value("AMARILLO"));

                mockMvc.perform(get("/api/v1/animales/" + animalRojoId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de salud')].estado")
                                                .value("ROJO"));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/indicadores-salud - Caravana con telemetría pero sin perfil de referencia devuelve SIN DATOS")
        @SuppressWarnings("null")
        void getIndicadoresSalud_sinPerfilReferencia() throws Exception {
                Long animalId = crearAnimal("Vaca Sin Perfil", establecimientoId, rodeoLoteId);
                Animal animal = animalRepository.findById(animalId).orElseThrow();

                Caravana caravana = new Caravana();
                caravana.setCodigo("CAR-SIN-PERFIL");
                caravana.setFechaAsignacion(LocalDate.now());
                caravana.setFechaAlta(LocalDate.now());
                caravana.setDevEui("DEV-SIN-PERFIL");
                caravanaRepository.save(caravana);
                animal.setCaravana(caravana);
                animalRepository.save(animal);

                Telemetria telemetria = new Telemetria();
                telemetria.setCaravana(caravana);
                telemetria.setFechaHora(LocalDateTime.now());
                telemetria.setMoveState(100);
                telemetriaRepository.save(telemetria);

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de movimiento')].estado")
                                                .value("SIN DATOS"));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/indicadores-salud - Telemetría a menos de 6hs conserva el color calculado (caso límite)")
        @SuppressWarnings("null")
        void getIndicadoresSalud_telemetriaJustoDentroDeLas6Horas_mantieneColor() throws Exception {
                Long animalId = crearAnimal("Vaca Telemetria Reciente", establecimientoId, rodeoLoteId);
                // 5h59m de antigüedad: todavía dentro de la ventana de 6hs -> se calcula el
                // color normalmente.
                asignarCaravanaConMoveState(animalId, 100, LocalDateTime.now().minusHours(6).plusMinutes(1));

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de movimiento')].estado")
                                                .value("VERDE"));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/indicadores-salud - Telemetría a más de 6hs pasa a SIN DATOS y no conserva el color previo (caso límite)")
        @SuppressWarnings("null")
        void getIndicadoresSalud_telemetriaSuperaLas6Horas_pasaASinDatos() throws Exception {
                Long animalId = crearAnimal("Vaca Telemetria Vieja", establecimientoId, rodeoLoteId);
                // 6h01m de antigüedad: supera la ventana de 6hs -> SIN DATOS, aunque el
                // moveState sea "normal" (VERDE).
                asignarCaravanaConMoveState(animalId, 100, LocalDateTime.now().minusHours(6).minusMinutes(1));

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de movimiento')].estado")
                                                .value("SIN DATOS"));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/indicadores-salud - Telemetría vieja no conserva un estado ROJO previo")
        @SuppressWarnings("null")
        void getIndicadoresSalud_telemetriaViejaConValorAnomalo_noConservaRojo() throws Exception {
                Long animalId = crearAnimal("Vaca Telemetria Vieja Rojo", establecimientoId, rodeoLoteId);
                // moveState muy alejado de la media (daría ROJO), pero la telemetría es de hace
                // más de 6hs.
                asignarCaravanaConMoveState(animalId, 500, LocalDateTime.now().minusHours(7));

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.indicadores[?(@.nombre == 'Estado de movimiento')].estado")
                                                .value("SIN DATOS"))
                                .andExpect(jsonPath("$.indicadores[0].nombre").value("Estado de movimiento"))
                                .andExpect(jsonPath("$.indicadores[0].descripcion",
                                                org.hamcrest.Matchers.containsString("6 horas")));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/indicadores-salud - RBAC: CAMPERO no tiene acceso (403)")
        @SuppressWarnings("null")
        void getIndicadoresSalud_rbacCampero_forbidden() throws Exception {
                Long animalId = crearAnimal("Vaca RBAC Campero", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + camperoToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/indicadores-salud - Veterinario y Administrador tienen acceso")
        @SuppressWarnings("null")
        void getIndicadoresSalud_veterinarioYAdmin_ok() throws Exception {
                Long animalId = crearAnimal("Vaca RBAC Vet Admin", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + veterinarioToken))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/indicadores-salud - Aislamiento por establecimiento (403 para empleado ajeno)")
        @SuppressWarnings("null")
        void getIndicadoresSalud_aislamientoEstablecimiento() throws Exception {
                Usuario otroProductor = new Usuario();
                otroProductor.setEmail("otroprod.indicadores@vigia.dev");
                otroProductor.setPasswordHash(passwordEncoder.encode("otro123"));
                otroProductor.setNombre("Otro Productor Indicadores");
                otroProductor.setRol(Rol.PRODUCTOR);
                otroProductor.setActivo(true);
                usuarioRepository.save(otroProductor);

                Establecimiento otroEst = new Establecimiento();
                otroEst.setNombre("Otro Campo Indicadores");
                otroEst.setLatitud(-35.0);
                otroEst.setLongitud(-59.0);
                otroEst.setActivo(true);
                otroEst.setFechaAlta(LocalDate.now());
                otroEst.setEmpleados(new ArrayList<>());
                otroEst.getEmpleados().add(new Empleado(otroEst, otroProductor));
                establecimientoRepository.save(otroEst);

                String otroToken = getTokenFor("otroprod.indicadores@vigia.dev", "otro123");

                Long animalId = crearAnimal("Vaca Ajena Indicadores", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/indicadores-salud")
                                .header("Authorization", "Bearer " + otroToken))
                                .andExpect(status().isForbidden());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("POST /api/v1/animales/{id}/vincular-caravana - Vinculación exitosa por un productor")
        void vincularCaravana_exitoso() throws Exception {
                Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();

                Caravana caravana = new Caravana();
                caravana.setDevEui("TEST-DEV-EUI-01");
                caravana.setAlias("Sensor Caravana 01");
                caravana.setEstablecimiento(est);
                caravana.setEstadoSensor(EstadoSensor.DISPONIBLE);
                caravana.setActivo(false);
                caravana = caravanaRepository.save(caravana);

                Long animalId = crearAnimal("Vaca Para Sensor", establecimientoId, rodeoLoteId);

                VincularCaravanaRequest request = new VincularCaravanaRequest(caravana.getId());

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/vincular-caravana")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.caravanaId").value(caravana.getId()))
                                .andExpect(jsonPath("$.caravanaDevEui").value("TEST-DEV-EUI-01"))
                                .andExpect(jsonPath("$.caravanaAlias").value("Sensor Caravana 01"))
                                .andExpect(jsonPath("$.caravanaEstado").value("VINCULADO"));

                Caravana enDb = caravanaRepository.findById(caravana.getId()).orElseThrow();
                assertThat(enDb.getEstadoSensor()).isEqualTo(EstadoSensor.VINCULADO);
                assertThat(enDb.isActivo()).isTrue();
                assertThat(enDb.getFechaAsignacion()).isNotNull();

                Animal animalEnDb = animalRepository.findById(animalId).orElseThrow();
                assertThat(animalEnDb.getCaravana()).isNotNull();
                assertThat(animalEnDb.getCaravana().getId()).isEqualTo(caravana.getId());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("POST /api/v1/animales/{id}/vincular-caravana - Falla con 400 si la caravana ya está vinculada a otro animal")
        void vincularCaravana_yaVinculada_retornaBadRequest() throws Exception {
                Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();

                Caravana caravana = new Caravana();
                caravana.setDevEui("TEST-DEV-EUI-OCUPADO");
                caravana.setEstablecimiento(est);
                caravana.setEstadoSensor(EstadoSensor.VINCULADO);
                caravana.setActivo(true);
                caravana = caravanaRepository.save(caravana);

                Long animal1Id = crearAnimal("Vaca Dueña", establecimientoId, rodeoLoteId);
                Animal animal1 = animalRepository.findById(animal1Id).orElseThrow();
                animal1.setCaravana(caravana);
                animalRepository.save(animal1);

                Long animal2Id = crearAnimal("Vaca Intruso", establecimientoId, rodeoLoteId);

                VincularCaravanaRequest request = new VincularCaravanaRequest(caravana.getId());

                mockMvc.perform(post("/api/v1/animales/" + animal2Id + "/vincular-caravana")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("POST /api/v1/animales/{id}/vincular-caravana - Falla con 403 para rol VETERINARIO")
        void vincularCaravana_veterinario_retornaForbidden() throws Exception {
                Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();

                Usuario vet = new Usuario();
                vet.setEmail("vet.sensores@vigia.dev");
                vet.setPasswordHash(passwordEncoder.encode("vet123"));
                vet.setNombre("Veterinario Sensores");
                vet.setRol(Rol.VETERINARIO);
                vet.setActivo(true);
                usuarioRepository.save(vet);
                est.getEmpleados().add(new Empleado(est, vet));
                establecimientoRepository.save(est);

                String vetToken = getTokenFor("vet.sensores@vigia.dev", "vet123");

                Caravana caravana = new Caravana();
                caravana.setDevEui("TEST-DEV-EUI-VET");
                caravana.setEstablecimiento(est);
                caravana.setEstadoSensor(EstadoSensor.DISPONIBLE);
                caravana = caravanaRepository.save(caravana);

                Long animalId = crearAnimal("Vaca Vet Test", establecimientoId, rodeoLoteId);

                VincularCaravanaRequest request = new VincularCaravanaRequest(caravana.getId());

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/vincular-caravana")
                                .header("Authorization", "Bearer " + vetToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden());
        }

        @SuppressWarnings("null")
        @Test
        @DisplayName("DELETE /api/v1/animales/{id}/desvincular-caravana - Desvinculación exitosa por productor")
        void desvincularCaravana_exitoso() throws Exception {
                Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();

                Caravana caravana = new Caravana();
                caravana.setDevEui("TEST-DEV-EUI-DESV");
                caravana.setEstablecimiento(est);
                caravana.setEstadoSensor(EstadoSensor.VINCULADO);
                caravana.setActivo(true);
                caravana = caravanaRepository.save(caravana);

                Long animalId = crearAnimal("Vaca Con Sensor Desv", establecimientoId, rodeoLoteId);
                Animal animal = animalRepository.findById(animalId).orElseThrow();
                animal.setCaravana(caravana);
                animalRepository.save(animal);

                mockMvc.perform(delete("/api/v1/animales/" + animalId + "/desvincular-caravana")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.caravanaId").doesNotExist());

                Caravana enDb = caravanaRepository.findById(caravana.getId()).orElseThrow();
                assertThat(enDb.getEstadoSensor()).isEqualTo(EstadoSensor.DISPONIBLE);
                assertThat(enDb.isActivo()).isFalse();

                Animal animalEnDb = animalRepository.findById(animalId).orElseThrow();
                assertThat(animalEnDb.getCaravana()).isNull();
        }

        // ── Índice de cohesión (#344) ────────────────────────────────────────────

        private void guardarCohesion(Long animalId, LocalDateTime fecha, double indice) {
                dev.vigia.backend.model.RegistroCohesion registro = dev.vigia.backend.model.RegistroCohesion.builder()
                                .animal(animalRepository.findById(animalId).orElseThrow())
                                .establecimiento(establecimientoRepository.findById(establecimientoId).orElseThrow())
                                .fecha(fecha)
                                .indiceCohesion(indice)
                                .tiempoFueraGrupoS(0L)
                                .build();
                registroCohesionRepository.save(registro);
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/cohesion - Sin desde/hasta usa los últimos 7 días y promedia el rodeo")
        @SuppressWarnings("null")
        void getCohesion_sinRango_ultimos7DiasConPromedioDelRodeo() throws Exception {
                Long vaca = crearAnimal("Vaca Cohesion", establecimientoId, rodeoLoteId);
                Long companera = crearAnimal("Companera", establecimientoId, rodeoLoteId);
                LocalDateTime corrida1 = LocalDateTime.now().minusDays(2).withNano(0);
                LocalDateTime corrida2 = LocalDateTime.now().minusHours(1).withNano(0);
                guardarCohesion(vaca, corrida1, 0.8);
                guardarCohesion(companera, corrida1, 0.6);
                guardarCohesion(vaca, corrida2, 0.4);
                guardarCohesion(companera, corrida2, 1.0);
                // Fuera de la ventana de 7 días: no debe aparecer
                guardarCohesion(vaca, LocalDateTime.now().minusDays(10), 0.1);

                mockMvc.perform(get("/api/v1/animales/" + vaca + "/cohesion")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.actual").value(0.4))
                                .andExpect(jsonPath("$.serie.length()").value(2))
                                .andExpect(jsonPath("$.serie[0].valor").value(0.8))
                                .andExpect(jsonPath("$.serie[0].promedioRodeo").value(0.7))
                                .andExpect(jsonPath("$.serie[1].promedioRodeo").value(0.7))
                                .andExpect(jsonPath("$.promedioRodeo").value(0.7));
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/cohesion - Usuario de otro establecimiento recibe 403")
        @SuppressWarnings("null")
        void getCohesion_usuarioAjeno_403() throws Exception {
                Usuario otroProductor = new Usuario();
                otroProductor.setEmail("otro-cohesion@vigia.dev");
                otroProductor.setPasswordHash(passwordEncoder.encode("otro123"));
                otroProductor.setNombre("Otro Productor");
                otroProductor.setRol(Rol.PRODUCTOR);
                otroProductor.setActivo(true);
                otroProductor = usuarioRepository.save(otroProductor);

                Establecimiento otroEst = new Establecimiento();
                otroEst.setNombre("Otro Campo Cohesion");
                otroEst.setLatitud(-35.0);
                otroEst.setLongitud(-59.0);
                otroEst.setActivo(true);
                otroEst.setFechaAlta(LocalDate.now());
                otroEst.setEmpleados(new ArrayList<>());
                otroEst.getEmpleados().add(new Empleado(otroEst, otroProductor));
                establecimientoRepository.save(otroEst);

                String otroToken = getTokenFor("otro-cohesion@vigia.dev", "otro123");
                Long animalId = crearAnimal("Vaca Ajena Cohesion", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/cohesion")
                                .header("Authorization", "Bearer " + otroToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/animales/{id}/cohesion - desde posterior a hasta devuelve 400")
        @SuppressWarnings("null")
        void getCohesion_rangoInvertido_400() throws Exception {
                Long animalId = crearAnimal("Vaca Rango", establecimientoId, rodeoLoteId);

                mockMvc.perform(get("/api/v1/animales/" + animalId + "/cohesion")
                                .param("desde", "2026-10-10T00:00:00")
                                .param("hasta", "2026-10-01T00:00:00")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isBadRequest());
        }
}
