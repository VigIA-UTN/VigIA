package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.dto.CambiarPasswordRequest;
import dev.vigia.backend.dto.UpdatePerfilRequest;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("UsuarioController — Tests (Listado y Baja de Productores)")

class UsuarioControllerTest {

    @MockBean
    private ConnectionFactory connectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RodeoLoteRepository rodeoLoteRepository;

    @Autowired
    private dev.vigia.backend.repository.AnimalRepository animalRepository;

    @Autowired
    private dev.vigia.backend.repository.EstadoReproductivoRepository estadoReproductivoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private dev.vigia.backend.repository.EstablecimientoRepository establecimientoRepository;

    @Autowired
    private dev.vigia.backend.repository.InvitacionRepository invitacionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private dev.vigia.backend.service.AuthService authService;

    private String adminToken;
    private String productorToken;
    private Long productorId;

    @BeforeEach
    void setUp() throws Exception {
        estadoReproductivoRepository.deleteAll();
        animalRepository.deleteAll();
        rodeoLoteRepository.deleteAll();
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.flush();
        authService.clearCacheForTests();

        Usuario admin = new Usuario();
        admin.setEmail("admin@vigia.dev");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setNombre("Super");
        admin.setRol(Rol.ADMINISTRADOR);
        admin.setActivo(true);
        usuarioRepository.save(admin);

        Usuario productor = new Usuario();
        productor.setEmail("productor@vigia.dev");
        productor.setPasswordHash(passwordEncoder.encode("prod123"));
        productor.setNombre("Juan");
        productor.setRol(Rol.PRODUCTOR);
        productor.setActivo(true);
        productor = usuarioRepository.save(productor);
        productorId = productor.getId();

        Usuario productorInactivo = new Usuario();
        productorInactivo.setEmail("inactivo@vigia.dev");
        productorInactivo.setPasswordHash(passwordEncoder.encode("prod123"));
        productorInactivo.setNombre("Pedro");
        productorInactivo.setRol(Rol.PRODUCTOR);
        productorInactivo.setActivo(false);
        usuarioRepository.save(productorInactivo);

        adminToken = getTokenFor("admin@vigia.dev", "admin123");
        productorToken = getTokenFor("productor@vigia.dev", "prod123");
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
    @DisplayName("GET /api/v1/usuarios/productores - Admin ve lista de productores activos")
    void getProductores_asAdmin_success() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/productores")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Juan"))
                .andExpect(jsonPath("$[0].activo").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/usuarios/productores - Productor recibe 403 Forbidden")
    void getProductores_asProductor_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/productores")
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/v1/usuarios/productores/{id} - Admin da de baja lógica a productor")
    @SuppressWarnings("null")
    void desactivarProductor_asAdmin_success() throws Exception {
        mockMvc.perform(delete("/api/v1/usuarios/productores/" + productorId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        Usuario user = usuarioRepository.findById(productorId).orElseThrow();
        assertThat(user.isActivo()).isFalse();
    }

    @Test
    @DisplayName("DELETE /api/v1/usuarios/productores/{id} - Productor no puede desactivar y recibe 403")
    void desactivarProductor_asProductor_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/usuarios/productores/" + productorId)
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/usuarios/empleados - Productor ve a los usuarios activos del establecimiento, productores incluidos")
    @SuppressWarnings("null")
    void getEmpleados_asProductor_success() throws Exception {
        Usuario empleado = new Usuario();
        empleado.setEmail("empleado@vigia.dev");
        empleado.setPasswordHash(passwordEncoder.encode("emp123"));
        empleado.setNombre("Empleado 1");
        empleado.setRol(Rol.CAMPERO);
        empleado.setActivo(true);
        empleado = usuarioRepository.save(empleado);

        Establecimiento est = new Establecimiento();
        est.setNombre("Estancia Test");
        est.setLatitud(-34.0);
        est.setLongitud(-58.0);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        Empleado vinculoProd = new Empleado(est, usuarioRepository.findById(productorId).orElseThrow());
        Empleado vinculoEmp = new Empleado(est, empleado);
        est.setEmpleados(new ArrayList<>(List.of(vinculoProd, vinculoEmp)));
        establecimientoRepository.save(est);

        mockMvc.perform(get("/api/v1/usuarios/empleados")
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].nombre", containsInAnyOrder("Juan", "Empleado 1")))
                .andExpect(jsonPath("$[*].rol", containsInAnyOrder("PRODUCTOR", "CAMPERO")));
    }

    @Test
    @DisplayName("DELETE /api/v1/usuarios/empleados/{id} - Un productor no puede quitar a otro productor")
    @SuppressWarnings("null")
    void removerEmpleado_otroProductor_badRequest() throws Exception {
        Usuario otroProductor = new Usuario();
        otroProductor.setEmail("otro.productor@vigia.dev");
        otroProductor.setPasswordHash(passwordEncoder.encode("prod123"));
        otroProductor.setNombre("Socio");
        otroProductor.setRol(Rol.PRODUCTOR);
        otroProductor.setActivo(true);
        otroProductor = usuarioRepository.save(otroProductor);

        Establecimiento est = new Establecimiento();
        est.setNombre("Estancia Compartida");
        est.setLatitud(-34.0);
        est.setLongitud(-58.0);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        Empleado vinculoProd = new Empleado(est, usuarioRepository.findById(productorId).orElseThrow());
        Empleado vinculoOtro = new Empleado(est, otroProductor);
        est.setEmpleados(new ArrayList<>(List.of(vinculoProd, vinculoOtro)));
        est = establecimientoRepository.save(est);

        mockMvc.perform(delete("/api/v1/usuarios/empleados/" + est.getId() + "/" + otroProductor.getId())
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isBadRequest());

        final Long estId = est.getId();
        final Long otroId = otroProductor.getId();
        Boolean sigueVinculado = new TransactionTemplate(transactionManager).execute(status -> {
            Establecimiento updatedEst = establecimientoRepository.findById(estId).orElseThrow();
            return updatedEst.getEmpleados().stream()
                    .anyMatch(v -> v.getUsuario().getId().equals(otroId) && v.isActivo());
        });
        assertThat(sigueVinculado).isTrue();
    }

    @Test
    @DisplayName("GET /api/v1/usuarios/empleados - Admin recibe 403 Forbidden")
    void getEmpleados_asAdmin_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/empleados")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/v1/usuarios/empleados/{id} - Productor remueve empleado de su establecimiento")
    @SuppressWarnings("null")
    void removerEmpleado_asProductor_success() throws Exception {
        Usuario empleado = new Usuario();
        empleado.setEmail("empleado2@vigia.dev");
        empleado.setPasswordHash(passwordEncoder.encode("emp123"));
        empleado.setNombre("Empleado 2");
        empleado.setRol(Rol.CAMPERO);
        empleado.setActivo(true);
        empleado = usuarioRepository.save(empleado);

        Establecimiento est = new Establecimiento();
        est.setNombre("Estancia Test 2");
        est.setLatitud(-34.0);
        est.setLongitud(-58.0);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        Empleado vinculoProd = new Empleado(est, usuarioRepository.findById(productorId).orElseThrow());
        Empleado vinculoEmp = new Empleado(est, empleado);
        est.setEmpleados(new ArrayList<>(List.of(vinculoProd, vinculoEmp)));
        est = establecimientoRepository.save(est);

        mockMvc.perform(delete("/api/v1/usuarios/empleados/" + est.getId() + "/" + empleado.getId())
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isOk());

        final Long estId = est.getId();
        final Long empId = empleado.getId();

        // Verificar en BD que el vínculo fue desactivado (requiere sesión abierta para
        // la colección lazy)
        Boolean isStillActive = new TransactionTemplate(transactionManager).execute(status -> {
            Establecimiento updatedEst = establecimientoRepository.findById(estId).orElseThrow();
            return updatedEst.getEmpleados().stream()
                    .anyMatch(v -> v.getUsuario().getId().equals(empId) && v.isActivo());
        });
        assertThat(isStillActive).isFalse();

        Usuario user = usuarioRepository.findById(empleado.getId()).orElseThrow();
        assertThat(user.isActivo()).isTrue();
    }

    @Test
    @DisplayName("DELETE /api/v1/usuarios/empleados/{id} - Falla si empleado no pertenece al establecimiento")
    @SuppressWarnings("null")
    void desactivarEmpleado_wrongEstablecimiento_badRequest() throws Exception {
        Usuario empleado = new Usuario();
        empleado.setEmail("empleado3@vigia.dev");
        empleado.setPasswordHash(passwordEncoder.encode("emp123"));
        empleado.setNombre("Empleado 3");
        empleado.setRol(Rol.CAMPERO);
        empleado.setActivo(true);
        empleado = usuarioRepository.save(empleado);

        Establecimiento est = new Establecimiento();
        est.setNombre("Estancia Test 3");
        est.setLatitud(-34.0);
        est.setLongitud(-58.0);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        Empleado vinculoEmp = new Empleado(est, empleado);
        est.setEmpleados(new ArrayList<>(List.of(vinculoEmp)));
        establecimientoRepository.save(est);

        Establecimiento estProd = new Establecimiento();
        estProd.setNombre("Estancia Prod");
        estProd.setLatitud(-34.0);
        estProd.setLongitud(-58.0);
        estProd.setActivo(true);
        estProd.setFechaAlta(LocalDate.now());
        Empleado vinculoProd = new Empleado(estProd, usuarioRepository.findById(productorId).orElseThrow());
        estProd.setEmpleados(new ArrayList<>(List.of(vinculoProd)));
        establecimientoRepository.save(estProd);

        mockMvc.perform(delete("/api/v1/usuarios/empleados/" + estProd.getId() + "/" + empleado.getId())
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/usuarios/me - Retorna el perfil del usuario autenticado")
    void getMiPerfil_success() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/me")
                .header("Authorization", "Bearer " + productorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Juan"))
                .andExpect(jsonPath("$.email").value("productor@vigia.dev"))
                .andExpect(jsonPath("$.rol").value("PRODUCTOR"));
    }

    @Test
    @DisplayName("PUT /api/v1/usuarios/me - Actualiza datos personales (nombre, apellido)")
    @SuppressWarnings("null")
    void updateMiPerfil_success() throws Exception {
        UpdatePerfilRequest request = new UpdatePerfilRequest();
        request.setNombre("Juan Modificado");
        request.setApellido("Perez");

        mockMvc.perform(put("/api/v1/usuarios/me")
                .header("Authorization", "Bearer " + productorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Juan Modificado"))
                .andExpect(jsonPath("$.apellido").value("Perez"));

        Usuario user = usuarioRepository.findById(productorId).orElseThrow();
        assertThat(user.getNombre()).isEqualTo("Juan Modificado");
        assertThat(user.getApellido()).isEqualTo("Perez");
    }

    @Test
    @DisplayName("PUT /api/v1/usuarios/me/password - Cambia la password con la contraseña actual correcta")
    @SuppressWarnings("null")
    void cambiarPassword_success() throws Exception {
        CambiarPasswordRequest request = new CambiarPasswordRequest("prod123", "NuevaPass123!");

        mockMvc.perform(put("/api/v1/usuarios/me/password")
                .header("Authorization", "Bearer " + productorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Usuario user = usuarioRepository.findById(productorId).orElseThrow();
        assertThat(passwordEncoder.matches("NuevaPass123!", user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("PUT /api/v1/usuarios/me/password - Falla al cambiar la password si la actual es incorrecta")
    @SuppressWarnings("null")
    void cambiarPassword_wrongCurrentPassword_badRequest() throws Exception {
        CambiarPasswordRequest request = new CambiarPasswordRequest("claveIncorrecta", "NuevaPass123!");

        mockMvc.perform(put("/api/v1/usuarios/me/password")
                .header("Authorization", "Bearer " + productorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
