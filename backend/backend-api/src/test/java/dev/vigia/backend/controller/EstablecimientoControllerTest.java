package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.Intervencion;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.TipoIntervencion;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.repository.InvitacionRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("EstablecimientoController — Tests")

class EstablecimientoControllerTest {

    @MockBean
    private ConnectionFactory connectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private dev.vigia.backend.repository.EstadoReproductivoRepository estadoReproductivoRepository;
    @Autowired
    private dev.vigia.backend.repository.AnimalRepository animalRepository;
    @Autowired
    private dev.vigia.backend.repository.RodeoLoteRepository rodeoLoteRepository;

    @Autowired
    private EstablecimientoRepository establecimientoRepository;

    @Autowired
    private InvitacionRepository invitacionRepository;

    @Autowired
    private IntervencionRepository intervencionRepository;

    @Autowired
    private AlertaRepository alertaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    private String adminToken;
    private String userToken;
    private String vetToken;
    @SuppressWarnings("unused")
    private String camperoToken;
    @SuppressWarnings("unused")
    private String otroProductorToken;
    @SuppressWarnings("unused")
    private Long establecimientoId;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() throws Exception {
        intervencionRepository.deleteAll();
        alertaRepository.deleteAll();
        estadoReproductivoRepository.deleteAll();
        animalRepository.deleteAll();
        rodeoLoteRepository.deleteAll();
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.flush();
        authService.clearCacheForTests();

        // 1. Setup ADMIN user
        Usuario admin = new Usuario();
        admin.setEmail("admin@vigia.dev");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setNombre("Admin");
        admin.setRol(Rol.ADMINISTRADOR);
        admin.setActivo(true);
        usuarioRepository.save(admin);

        // 2. Setup PRODUCTOR user (non-admin)
        Usuario productor = new Usuario();
        productor.setEmail("productor@vigia.dev");
        productor.setPasswordHash(passwordEncoder.encode("prod123"));
        productor.setNombre("Productor");
        productor.setRol(Rol.PRODUCTOR);
        productor.setActivo(true);
        usuarioRepository.save(productor);

        Usuario veterinario = crearUsuario("vet@vigia.dev", Rol.VETERINARIO);
        Usuario campero = crearUsuario("campero@vigia.dev", Rol.CAMPERO);
        crearUsuario("otro@vigia.dev", Rol.PRODUCTOR); // no pertenece al establecimiento

        // 3. Get tokens
        adminToken = getTokenFor("admin@vigia.dev", "admin123");
        userToken = getTokenFor("productor@vigia.dev", "prod123");
        vetToken = getTokenFor("vet@vigia.dev", "pass123");
        camperoToken = getTokenFor("campero@vigia.dev", "pass123");
        otroProductorToken = getTokenFor("otro@vigia.dev", "pass123");

        // 4. Create an initial establishment
        Establecimiento est = new Establecimiento();
        est.setNombre("Establecimiento Test");
        est.setLatitud(-34.0);
        est.setLongitud(-58.0);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        est.setEmpleados(new ArrayList<>());
        Empleado vinculoProd = new Empleado(est, productor);
        est.getEmpleados().add(vinculoProd);
        est.getEmpleados().add(new Empleado(est, veterinario));
        est.getEmpleados().add(new Empleado(est, campero));
        est = establecimientoRepository.save(est);
        establecimientoId = est.getId();
    }

    private Usuario crearUsuario(String email, Rol rol) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("pass123"));
        u.setNombre(email);
        u.setRol(rol);
        u.setActivo(true);
        return usuarioRepository.save(u);
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
    @DisplayName("POST /api/v1/establecimientos - Admin crea establecimiento con la configuración de anestro por defecto")
    @SuppressWarnings("null")
    void createEstablecimiento_asAdmin_success() throws Exception {
        EstablecimientoRequest request = new EstablecimientoRequest();
        request.setNombre("Nuevo Est");
        request.setLatitud(-35.0);
        request.setLongitud(-59.0);

        mockMvc.perform(post("/api/v1/establecimientos")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Nuevo Est"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.diasDeteccionAnestro").value(45))
                .andExpect(jsonPath("$.severidadAnestro").value("MEDIA"));
    }

    @Test
    @DisplayName("GET /api/v1/establecimientos - Admin lista establecimientos")
    void getAllEstablecimientos_asAdmin_success() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Establecimiento Test"));
    }

    @Test
    @DisplayName("DELETE /api/v1/establecimientos/{id} - Admin desactiva establecimiento")
    @SuppressWarnings("null")
    void desactivarEstablecimiento_asAdmin_success() throws Exception {
        mockMvc.perform(delete("/api/v1/establecimientos/" + establecimientoId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        // Verificar en BD que fue baja lógica
        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();
        assertThat(est.isActivo()).isFalse();
    }

    @Test
    @DisplayName("POST /api/v1/establecimientos - Usuario no-Admin recibe 403 Forbidden")
    @SuppressWarnings("null")
    void createEstablecimiento_asProductor_forbidden() throws Exception {
        EstablecimientoRequest request = new EstablecimientoRequest();
        request.setNombre("No Deberia Crear");
        request.setLatitud(-35.0);
        request.setLongitud(-59.0);

        mockMvc.perform(post("/api/v1/establecimientos")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("GET /api/v1/establecimientos - Productor lista solo sus establecimientos")
    void getAllEstablecimientos_asProductor_success() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Establecimiento Test"));
    }

    // ── PUT /{id}: chequeo de pertenencia ────────────────────────────────────

    @SuppressWarnings("null")
    private ResultActions putEstablecimiento(String token, String nombre) throws Exception {
        EstablecimientoRequest request = new EstablecimientoRequest();
        request.setNombre(nombre);
        request.setLatitud(-34.0);
        request.setLongitud(-58.0);
        return mockMvc.perform(put("/api/v1/establecimientos/" + establecimientoId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    @Test
    @DisplayName("PUT /api/v1/establecimientos/{id} - Productor ajeno recibe 403 y no se modifica nada")
    @SuppressWarnings("null")
    void updateEstablecimiento_asProductorAjeno_forbidden() throws Exception {
        putEstablecimiento(otroProductorToken, "Hackeado").andExpect(status().isForbidden());

        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();
        assertThat(est.getNombre()).isEqualTo("Establecimiento Test");
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("PUT /api/v1/establecimientos/{id} - Productor del establecimiento lo edita sin pisar la configuración de anestro")
    void updateEstablecimiento_asProductorDueno_ok() throws Exception {
        mockMvc.perform(put("/api/v1/establecimientos/" + establecimientoId + "/anestro")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"diasDeteccionAnestro\":30,\"severidadAnestro\":\"ALTA\"}"))
                .andExpect(status().isOk());

        putEstablecimiento(userToken, "Renombrado")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Renombrado"))
                .andExpect(jsonPath("$.diasDeteccionAnestro").value(30))
                .andExpect(jsonPath("$.severidadAnestro").value("ALTA"));
    }

    @Test
    @DisplayName("PUT /api/v1/establecimientos/{id} - Admin edita cualquier establecimiento")
    void updateEstablecimiento_asAdmin_ok() throws Exception {
        putEstablecimiento(adminToken, "Editado por admin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Editado por admin"));
    }

    @Test
    @DisplayName("PUT /api/v1/establecimientos/{id} - Veterinario sigue sin poder editar el establecimiento")
    void updateEstablecimiento_asVeterinario_forbidden() throws Exception {
        putEstablecimiento(vetToken, "No deberia").andExpect(status().isForbidden());
    }

    // ── Tests para GET /{id}/intervenciones ─────────────────────────────────────

    @Test
    @DisplayName("GET /{id}/intervenciones - Productor obtiene lista vacía cuando no hay intervenciones")
    void obtenerIntervenciones_sinDatos_retornaListaVacia() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/" + establecimientoId + "/intervenciones")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /{id}/intervenciones - Productor obtiene intervenciones de su establecimiento")
    @SuppressWarnings("null")
    void obtenerIntervenciones_conDatos_retornaIntervenciones() throws Exception {
        // Crear un RodeoLote y un Animal en el establecimiento
        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();
        RodeoLote rodeo = new RodeoLote(est);
        rodeo.setNombre("Rodeo Test");
        rodeo = rodeoLoteRepository.save(rodeo);

        Animal animal = new Animal(est, rodeo);
        animal.setAlias("Vaca Test");
        animal = animalRepository.save(animal);

        // Crear una Alerta asociada al animal
        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setDisparador(DisparadorAlerta.SALUD);
        alerta.setTipo("SALUD");
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setSeveridad(SeveridadAlerta.MEDIA);
        alerta.setTsGeneracion(LocalDateTime.now());
        alerta.setValorUmbral(0.0);
        alerta.setValorDetectado(0.0);
        alerta = alertaRepository.save(alerta);

        // Crear una Intervención
        Usuario productor = usuarioRepository.findByEmail("productor@vigia.dev").orElseThrow();
        Intervencion intervencion = new Intervencion();
        intervencion.setAnimal(animal);
        intervencion.setAlerta(alerta);
        intervencion.setInterventor(productor);
        intervencion.setDescripcion("Tratamiento antibiótico aplicado");
        intervencion.setFechaHora(LocalDateTime.now());
        intervencion.setTipo(TipoIntervencion.TRATAMIENTO);
        intervencionRepository.save(intervencion);

        mockMvc.perform(get("/api/v1/establecimientos/" + establecimientoId + "/intervenciones")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].descripcion").value("Tratamiento antibiótico aplicado"))
                .andExpect(jsonPath("$[0].tipo").value("TRATAMIENTO"))
                .andExpect(jsonPath("$[0].interventor").value("Productor"))
                .andExpect(jsonPath("$[0].animalId").value(animal.getId()))
                .andExpect(jsonPath("$[0].alertaId").value(alerta.getId()));
    }

    @Test
    @DisplayName("GET /{id}/intervenciones - Intervención manual (sin alerta) se retorna con alertaId nulo")
    @SuppressWarnings("null")
    void obtenerIntervenciones_intervencionManual_retornaAlertaIdNulo() throws Exception {
        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();
        RodeoLote rodeo = new RodeoLote(est);
        rodeo.setNombre("Rodeo Manual");
        rodeo = rodeoLoteRepository.save(rodeo);

        Animal animal = new Animal(est, rodeo);
        animal.setAlias("Vaca Manual");
        animal = animalRepository.save(animal);

        Usuario productor = usuarioRepository.findByEmail("productor@vigia.dev").orElseThrow();
        Intervencion intervencion = new Intervencion();
        intervencion.setAnimal(animal);
        intervencion.setInterventor(productor);
        intervencion.setDescripcion("Vacunación anual");
        intervencion.setFechaHora(LocalDateTime.now());
        intervencion.setTipo(TipoIntervencion.VACUNACION);
        intervencionRepository.save(intervencion);

        mockMvc.perform(get("/api/v1/establecimientos/" + establecimientoId + "/intervenciones")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("VACUNACION"))
                .andExpect(jsonPath("$[0].alertaId").doesNotExist());
    }

    @Test
    @DisplayName("GET /{id}/intervenciones - Múltiples intervenciones se retornan correctamente")
    @SuppressWarnings("null")
    void obtenerIntervenciones_multiplesIntervenciones_retornaTodas() throws Exception {
        Establecimiento est = establecimientoRepository.findById(establecimientoId).orElseThrow();
        RodeoLote rodeo = new RodeoLote(est);
        rodeo.setNombre("Rodeo Multi");
        rodeo = rodeoLoteRepository.save(rodeo);

        Animal animal = new Animal(est, rodeo);
        animal.setAlias("Vaca Multi");
        animal = animalRepository.save(animal);

        Usuario productor = usuarioRepository.findByEmail("productor@vigia.dev").orElseThrow();

        // Crear dos alertas y dos intervenciones
        Alerta alerta1 = new Alerta();
        alerta1.setAnimal(animal);
        alerta1.setDisparador(DisparadorAlerta.SALUD);
        alerta1.setTipo("SALUD");
        alerta1.setEstado(EstadoAlerta.PENDIENTE);
        alerta1.setSeveridad(SeveridadAlerta.ALTA);
        alerta1.setTsGeneracion(LocalDateTime.now().minusDays(2));
        alerta1.setValorUmbral(0.0);
        alerta1.setValorDetectado(0.0);
        alerta1 = alertaRepository.save(alerta1);

        Intervencion intervencion1 = new Intervencion();
        intervencion1.setAnimal(animal);
        intervencion1.setAlerta(alerta1);
        intervencion1.setInterventor(productor);
        intervencion1.setDescripcion("Vacunación preventiva");
        intervencion1.setFechaHora(LocalDateTime.now().minusDays(2));
        intervencion1.setTipo(TipoIntervencion.VACUNACION);
        intervencionRepository.save(intervencion1);

        Alerta alerta2 = new Alerta();
        alerta2.setAnimal(animal);
        alerta2.setDisparador(DisparadorAlerta.CELO);
        alerta2.setTipo("CELO");
        alerta2.setEstado(EstadoAlerta.PENDIENTE);
        alerta2.setSeveridad(SeveridadAlerta.BAJA);
        alerta2.setTsGeneracion(LocalDateTime.now().minusDays(1));
        alerta2.setValorUmbral(0.0);
        alerta2.setValorDetectado(0.0);
        alerta2 = alertaRepository.save(alerta2);

        Intervencion intervencion2 = new Intervencion();
        intervencion2.setAnimal(animal);
        intervencion2.setAlerta(alerta2);
        intervencion2.setInterventor(productor);
        intervencion2.setDescripcion("Revisión veterinaria general");
        intervencion2.setFechaHora(LocalDateTime.now().minusDays(1));
        intervencion2.setTipo(TipoIntervencion.REVISION_VETERINARIA);
        intervencionRepository.save(intervencion2);

        mockMvc.perform(get("/api/v1/establecimientos/" + establecimientoId + "/intervenciones")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /{id}/intervenciones - Usuario que no pertenece al establecimiento recibe error")
    @SuppressWarnings("null")
    void obtenerIntervenciones_usuarioNoPerteneceAlEstablecimiento_retornaError() throws Exception {
        // otro@vigia.dev (creado en setUp) NO pertenece al establecimiento
        mockMvc.perform(get("/api/v1/establecimientos/" + establecimientoId + "/intervenciones")
                .header("Authorization", "Bearer " + otroProductorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /{id}/intervenciones - Sin autenticación retorna 401")
    void obtenerIntervenciones_sinToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/" + establecimientoId + "/intervenciones"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /{id}/intervenciones - Establecimiento inexistente retorna error")
    void obtenerIntervenciones_establecimientoInexistente_retornaError() throws Exception {
        mockMvc.perform(get("/api/v1/establecimientos/99999/intervenciones")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
    }
}

