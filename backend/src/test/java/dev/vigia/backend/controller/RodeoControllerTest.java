package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.RodeoRequest;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.RodeoRepository;
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

import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("RodeoController — Tests")
class RodeoControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UsuarioRepository usuarioRepository;

        @Autowired
        private RodeoRepository rodeoRepository;

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

        private Establecimiento establecimiento;
        private Long establecimientoId;

        private String productorToken;
        private String adminToken;

        @BeforeEach
        @SuppressWarnings("null")
        void setUp() throws Exception {
                // Orden correcto: primero tablas hijas, luego padres
                rodeoRepository.deleteAll();
                invitacionRepository.deleteAll();
                establecimientoRepository.deleteAll();
                usuarioRepository.deleteAll();
                authService.clearCacheForTests();

                // 1. Admin (para test de rol incorrecto)
                Usuario admin = new Usuario();
                admin.setEmail("admin@vigia.dev");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setNombre("Admin");
                admin.setRol(Rol.ADMINISTRADOR);
                admin.setActivo(true);
                usuarioRepository.save(admin);

                // 2. Productor activo
                Usuario productor = new Usuario();
                productor.setEmail("productor@vigia.dev");
                productor.setPasswordHash(passwordEncoder.encode("prod123"));
                productor.setNombre("Productor");
                productor.setRol(Rol.PRODUCTOR);
                productor.setActivo(true);
                productor = usuarioRepository.save(productor);

                // 3. Establecimiento con productor como empleado activo
                establecimiento = new Establecimiento();
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

                // 4. Tokens
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

        @Test
        @DisplayName("POST /api/v1/rodeos/crear - Productor crea rodeo exitosamente")
        @SuppressWarnings("null")
        void crearRodeo_asProductor_success() throws Exception {
                RodeoRequest request = new RodeoRequest();
                request.setNombre("Rodeo Verano");
                request.setEstablecimientoId(establecimientoId);

                mockMvc.perform(post("/api/v1/rodeos/crear")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.nombre").value("Rodeo Verano"))
                                .andExpect(jsonPath("$.activo").value(true))
                                .andExpect(jsonPath("$.establecimientoId").value(establecimientoId));

                // Verificar en BD que el rodeo fue persistido
                assertThat(rodeoRepository.findByEstablecimientoId(establecimientoId)).hasSize(1);
        }

        @Test
        @DisplayName("POST /api/v1/rodeos/crear - Admin recibe 403 Forbidden")
        @SuppressWarnings("null")
        void crearRodeo_asAdmin_forbidden() throws Exception {
                RodeoRequest request = new RodeoRequest();
                request.setNombre("Rodeo No Permitido");
                request.setEstablecimientoId(establecimientoId);

                mockMvc.perform(post("/api/v1/rodeos/crear")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden());

                assertThat(rodeoRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("POST /api/v1/rodeos/crear - Sin autenticación recibe 401")
        @SuppressWarnings("null")
        void crearRodeo_sinAutenticacion_unauthorized() throws Exception {
                RodeoRequest request = new RodeoRequest();
                request.setNombre("Rodeo Anonimo");
                request.setEstablecimientoId(establecimientoId);

                mockMvc.perform(post("/api/v1/rodeos/crear")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/v1/rodeos/crear - Establecimiento no encontrado retorna 400")
        @SuppressWarnings("null")
        void crearRodeo_establecimientoInexistente_badRequest() throws Exception {
                RodeoRequest request = new RodeoRequest();
                request.setNombre("Rodeo Sin Est.");
                request.setEstablecimientoId(9999L);

                mockMvc.perform(post("/api/v1/rodeos/crear")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/v1/rodeos/crear - Productor no pertenece al establecimiento retorna 400")
        @SuppressWarnings("null")
        void crearRodeo_productorNoPertenece_badRequest() throws Exception {
                // Crear otro establecimiento sin vincular al productor
                Establecimiento otroEst = new Establecimiento();
                otroEst.setNombre("Otro Establecimiento");
                otroEst.setLatitud(-35.0);
                otroEst.setLongitud(-59.0);
                otroEst.setActivo(true);
                otroEst.setFechaAlta(LocalDate.now());
                otroEst.setEmpleados(new ArrayList<>());
                otroEst = establecimientoRepository.save(otroEst);

                RodeoRequest request = new RodeoRequest();
                request.setNombre("Rodeo Ajeno");
                request.setEstablecimientoId(otroEst.getId());

                mockMvc.perform(post("/api/v1/rodeos/crear")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("POST /api/v1/rodeos/crear - Productor inactivo en establecimiento retorna 400")
        @SuppressWarnings("null")
        void crearRodeo_productorInactivo_badRequest() throws Exception {
                // Crear un segundo productor inactivo en el establecimiento
                Usuario productorInactivo = new Usuario();
                productorInactivo.setEmail("inactivo@vigia.dev");
                productorInactivo.setPasswordHash(passwordEncoder.encode("inac123"));
                productorInactivo.setNombre("Inactivo");
                productorInactivo.setRol(Rol.PRODUCTOR);
                productorInactivo.setActivo(true); // activo en sistema para poder loguear
                productorInactivo = usuarioRepository.save(productorInactivo);

                // Vínculo con el establecimiento pero marcado como inactivo
                Empleado vinculoInactivo = new Empleado(establecimiento, productorInactivo);
                vinculoInactivo.setActivo(false);
                establecimiento.getEmpleados().add(vinculoInactivo);
                establecimientoRepository.save(establecimiento);

                String tokenInactivo = getTokenFor("inactivo@vigia.dev", "inac123");

                RodeoRequest request = new RodeoRequest();
                request.setNombre("Rodeo Inactivo");
                request.setEstablecimientoId(establecimientoId);

                mockMvc.perform(post("/api/v1/rodeos/crear")
                                .header("Authorization", "Bearer " + tokenInactivo)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /api/v1/rodeos - Admin lista todos los rodeos")
        @SuppressWarnings("null")
        void getAllRodeos_asAdmin_success() throws Exception {
                // Crear un rodeo en la BD previamente
                RodeoRequest request = new RodeoRequest();
                request.setNombre("Rodeo Admin View");
                request.setEstablecimientoId(establecimientoId);

                mockMvc.perform(post("/api/v1/rodeos/crear")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get("/api/v1/rodeos")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].nombre").value("Rodeo Admin View"));
        }

        @Test
        @DisplayName("GET /api/v1/rodeos - Productor lista solo sus rodeos")
        @SuppressWarnings("null")
        void getAllRodeos_asProductor_success() throws Exception {
                RodeoRequest request = new RodeoRequest();
                request.setNombre("Rodeo Propio");
                request.setEstablecimientoId(establecimientoId);

                mockMvc.perform(post("/api/v1/rodeos/crear")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                mockMvc.perform(get("/api/v1/rodeos")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(1))
                                .andExpect(jsonPath("$[0].nombre").value("Rodeo Propio"));
        }

        @Test
        @DisplayName("GET /api/v1/rodeos - Sin autenticación recibe 403")
        void getAllRodeos_sinAutenticacion_forbidden() throws Exception {
                mockMvc.perform(get("/api/v1/rodeos"))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/rodeos/{id} - Productor obtiene rodeo por id")
        @SuppressWarnings("null")
        void getRodeoById_asProductor_success() throws Exception {
                // Crear rodeo primero
                RodeoRequest request = new RodeoRequest();
                request.setNombre("Rodeo Get By Id");
                request.setEstablecimientoId(establecimientoId);

                MvcResult createResult = mockMvc.perform(post("/api/v1/rodeos/crear")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn();

                Long rodeoId = objectMapper.readTree(
                                createResult.getResponse().getContentAsString()).get("id").asLong();

                mockMvc.perform(get("/api/v1/rodeos/" + rodeoId)
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(rodeoId))
                                .andExpect(jsonPath("$.nombre").value("Rodeo Get By Id"))
                                .andExpect(jsonPath("$.activo").value(true));
        }

        @Test
        @DisplayName("GET /api/v1/rodeos/{id} - Rodeo inexistente retorna 400")
        void getRodeoById_inexistente_badRequest() throws Exception {
                mockMvc.perform(get("/api/v1/rodeos/9999")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /api/v1/rodeos/{id} - Admin recibe 403 Forbidden")
        void getRodeoById_asAdmin_forbidden() throws Exception {
                mockMvc.perform(get("/api/v1/rodeos/1")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DELETE /api/v1/rodeos/{id} - Productor desactiva su rodeo exitosamente")
        @SuppressWarnings("null")
        void desactivarRodeo_asProductor_success() throws Exception {
                // Crear rodeo
                RodeoRequest request = new RodeoRequest();
                request.setNombre("Rodeo Para Eliminar");
                request.setEstablecimientoId(establecimientoId);

                MvcResult createResult = mockMvc.perform(post("/api/v1/rodeos/crear")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn();

                Long rodeoId = objectMapper.readTree(
                                createResult.getResponse().getContentAsString()).get("id").asLong();

                mockMvc.perform(delete("/api/v1/rodeos/" + rodeoId)
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isOk());

                // Verificar baja lógica en BD
                assertThat(rodeoRepository.findById(rodeoId)).isPresent();
                assertThat(rodeoRepository.findById(rodeoId).get().isActivo()).isFalse();
        }

        @Test
        @DisplayName("DELETE /api/v1/rodeos/{id} - Rodeo inexistente retorna 400")
        void desactivarRodeo_inexistente_badRequest() throws Exception {
                mockMvc.perform(delete("/api/v1/rodeos/9999")
                                .header("Authorization", "Bearer " + productorToken))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DELETE /api/v1/rodeos/{id} - Admin recibe 403 Forbidden")
        void desactivarRodeo_asAdmin_forbidden() throws Exception {
                mockMvc.perform(delete("/api/v1/rodeos/1")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DELETE /api/v1/rodeos/{id} - Productor no pertenece al establecimiento retorna 400")
        @SuppressWarnings("null")
        void desactivarRodeo_productorAjeno_badRequest() throws Exception {
                // Crear otro productor y vincularlo al establecimiento base
                Usuario otroProductor = new Usuario();
                otroProductor.setEmail("otro@vigia.dev");
                otroProductor.setPasswordHash(passwordEncoder.encode("otro123"));
                otroProductor.setNombre("Otro");
                otroProductor.setRol(Rol.PRODUCTOR);
                otroProductor.setActivo(true);
                otroProductor = usuarioRepository.save(otroProductor);

                // Establecimiento propio del primer productor — crear un rodeo en él
                RodeoRequest request = new RodeoRequest();
                request.setNombre("Rodeo Ajeno Delete");
                request.setEstablecimientoId(establecimientoId);

                MvcResult createResult = mockMvc.perform(post("/api/v1/rodeos/crear")
                                .header("Authorization", "Bearer " + productorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn();

                Long rodeoId = objectMapper.readTree(
                                createResult.getResponse().getContentAsString()).get("id").asLong();

                // Crear establecimiento propio para "otro" productor para que pueda
                // autenticarse
                Establecimiento otroEst = new Establecimiento();
                otroEst.setNombre("Otro Est");
                otroEst.setLatitud(-36.0);
                otroEst.setLongitud(-60.0);
                otroEst.setActivo(true);
                otroEst.setFechaAlta(java.time.LocalDate.now());
                otroEst.setEmpleados(new ArrayList<>());
                Empleado vinculoOtro = new Empleado(otroEst, otroProductor);
                otroEst.getEmpleados().add(vinculoOtro);
                establecimientoRepository.save(otroEst);

                String otroToken = getTokenFor("otro@vigia.dev", "otro123");

                // "otro" intenta borrar el rodeo del primer productor
                mockMvc.perform(delete("/api/v1/rodeos/" + rodeoId)
                                .header("Authorization", "Bearer " + otroToken))
                                .andExpect(status().isBadRequest());
        }
}
