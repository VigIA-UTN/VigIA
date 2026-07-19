package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AnimalRequest;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.EstadoReproductivoRequest;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.UsuarioRepository;
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
import java.util.ArrayList;
import java.util.UUID;

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
        private EntityManager entityManager;

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
                estadoReproductivoRepository.deleteAll();
                animalRepository.deleteAll();
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

        /** Crea un animal ACTIVO via API (como productor) y devuelve su id. */
        @SuppressWarnings("null")
        private Long crearAnimal(String nombre, Long estId, Long rodeoId) throws Exception {
                return crearAnimalConToken(nombre, estId, rodeoId, productorToken);
        }

        /** Crea un animal ACTIVO via API con el token indicado y devuelve su id. */
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
        @DisplayName("POST /api/v1/animales/crear - Sin autenticación recibe 403")
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
                                .andExpect(status().isForbidden());
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
        @DisplayName("GET /api/v1/animales - Sin autenticación recibe 403")
        void listarAnimales_sinAuth_forbidden() throws Exception {
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
        @DisplayName("POST /api/v1/animales/{id}/estado-reproductivo - Falla por consistencia de fechas (parto > inseminación)")
        @SuppressWarnings("null")
        void registrarEstadoReproductivo_fechasInconsistentes_badRequest() throws Exception {
                Long animalId = crearAnimal("Vaca 3", establecimientoId, rodeoLoteId);

                EstadoReproductivoRequest request = new EstadoReproductivoRequest();
                request.setEstado(TipoReproductivo.VACIA);
                request.setFechaUltimaInseminacion(LocalDate.now().minusDays(20));
                request.setFechaUltimoParto(LocalDate.now().minusDays(10));

                mockMvc.perform(post("/api/v1/animales/" + animalId + "/estado-reproductivo")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
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
                String[] headers = { "IdCaravana", "Alias", "FechaNacimiento", "Raza", "Establecimiento", "Rodeo" };
                String[][] rows = {
                                { "X-123", "Vaca Excel", "2020-01-01", "Angus", "Estancia Test", "Rodeo Base" }
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
                                .andExpect(jsonPath("$.animales[0].alias").value("Vaca Excel"));
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
                animal.setRaza("Angus");
                animal = animalRepository.save(animal);

                String csvContent = "IdAnimal,Estado,FechaUltimoParto,FechaUltimaInseminacion\n" +
                                animal.getId() + ",GESTANTE,2023-01-01,2023-02-01\n";

                MockMultipartFile file = new MockMultipartFile("file", "estados.csv", "text/csv",
                                csvContent.getBytes());

                mockMvc.perform(multipart("/api/v1/animales/estado-reproductivo/csv")
                                .file(file)
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
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isForbidden());
        }
}
