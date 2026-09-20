package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.AuthService;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("AlertaController Tests")

class AlertaControllerTest {

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthService authService;

    @Autowired
    private AlertaRepository alertaRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private RodeoLoteRepository rodeoLoteRepository;
    @Autowired
    private EstablecimientoRepository establecimientoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private InvitacionRepository invitacionRepository;
    @Autowired
    private EstadoReproductivoRepository estadoReproductivoRepository;

    private Long alertaId;
    private Long alertaOtroEstId;
    private Long establecimientoId;

    private String productorToken;
    private String veterinarioToken;
    private String camperoToken;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() throws Exception {
        // Limpieza en orden (hijos primero)
        alertaRepository.deleteAll();
        estadoReproductivoRepository.deleteAll();
        animalRepository.deleteAll();
        rodeoLoteRepository.deleteAll();
        invitacionRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.flush();
        authService.clearCacheForTests();

        // --- Usuarios ---
        Usuario productor = crearUsuario("productor@vigia.dev", "prod123", Rol.PRODUCTOR);
        Usuario veterinario = crearUsuario("veterinario@vigia.dev", "vet123", Rol.VETERINARIO);
        Usuario campero = crearUsuario("campero@vigia.dev", "camp123", Rol.CAMPERO);

        // --- Establecimiento principal ---
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

        // Animal en el establecimiento principal
        RodeoLote rodeo = new RodeoLote(est);
        rodeo.setNombre("Rodeo Base");
        rodeo = rodeoLoteRepository.save(rodeo);

        Animal animal = new Animal();
        animal.setIdCaravanaOficial("CAR-001");
        animal.setEstablecimiento(est);
        animal.setRodeoLote(rodeo);
        animal.setEstadoActual(EstadoAnimal.ACTIVO);
        animal.setFechaNacimiento(LocalDate.of(2020, 1, 1));
        animal = animalRepository.save(animal);

        // Alerta PENDIENTE en el establecimiento principal
        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setDisparador(DisparadorAlerta.CELO);
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setTsGeneracion(LocalDateTime.now());
        alerta.setValorUmbral(80.0);
        alerta.setValorDetectado(95.0);
        alerta.setAccionRecomendada("Planificar inseminación artificial");
        alerta = alertaRepository.save(alerta);
        alertaId = alerta.getId();

        // --- Establecimiento ajeno (para test de acceso denegado) ---
        Establecimiento otroEst = new Establecimiento();
        otroEst.setNombre("Estancia Ajena");
        otroEst.setLatitud(-35.0);
        otroEst.setLongitud(-59.0);
        otroEst.setActivo(true);
        otroEst.setFechaAlta(LocalDate.now());
        otroEst.setEmpleados(new ArrayList<>());
        otroEst = establecimientoRepository.save(otroEst);

        RodeoLote rodeoAjeno = new RodeoLote(otroEst);
        rodeoAjeno.setNombre("Rodeo Ajeno");
        rodeoAjeno = rodeoLoteRepository.save(rodeoAjeno);

        Animal animalAjeno = new Animal();
        animalAjeno.setIdCaravanaOficial("CAR-002");
        animalAjeno.setEstablecimiento(otroEst);
        animalAjeno.setRodeoLote(rodeoAjeno);
        animalAjeno.setEstadoActual(EstadoAnimal.ACTIVO);
        animalAjeno.setFechaNacimiento(LocalDate.of(2020, 1, 1));
        animalAjeno = animalRepository.save(animalAjeno);

        Alerta alertaAjena = new Alerta();
        alertaAjena.setAnimal(animalAjeno);
        alertaAjena.setDisparador(DisparadorAlerta.CELO);
        alertaAjena.setEstado(EstadoAlerta.PENDIENTE);
        alertaAjena.setTsGeneracion(LocalDateTime.now());
        alertaAjena.setValorUmbral(80.0);
        alertaAjena.setValorDetectado(95.0);
        alertaAjena = alertaRepository.save(alertaAjena);
        alertaOtroEstId = alertaAjena.getId();

        // --- Tokens ---
        productorToken = getTokenFor("productor@vigia.dev", "prod123");
        veterinarioToken = getTokenFor("veterinario@vigia.dev", "vet123");
        camperoToken = getTokenFor("campero@vigia.dev", "camp123");
    }

    // =========================================================================
    // Casos de éxito
    // =========================================================================

    @Test
    @DisplayName("Productor confirma alerta PENDIENTE → 200 con estado CONFIRMADA")
    @SuppressWarnings("null")
    void confirmarAlerta_comoProductor_returns200() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertaId))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.accionRecomendada").value("Planificar inseminación artificial"));
    }

    @Test
    @DisplayName("Veterinario descarta alerta PENDIENTE → 200 con estado DESCARTADA")
    @SuppressWarnings("null")
    void descartarAlerta_comoVeterinario_returns200() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + veterinarioToken)
                .param("estado", "DESCARTADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertaId))
                .andExpect(jsonPath("$.estado").value("DESCARTADA"));
    }

    @Test
    @DisplayName("Veterinario confirma alerta PENDIENTE → 200 con estado CONFIRMADA")
    @SuppressWarnings("null")
    void confirmarAlerta_comoVeterinario_returns200() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + veterinarioToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("H0011: Filtros de alertas (tipo, severidad, combinados, vacío, ordenamiento predeterminado y sin filtros)")
    @SuppressWarnings("null")
    void getAlertas_filtros() throws Exception {
        // Limpiamos la alerta creada en setUp() para armar un set específico
        alertaRepository.deleteAll();

        Animal animal = animalRepository.findAll().get(0);

        // 1. Alerta PENDIENTE, CELO, ALTA (urgencia mayor)
        Alerta a1 = new Alerta();
        a1.setAnimal(animal);
        a1.setDisparador(DisparadorAlerta.CELO);
        a1.setEstado(EstadoAlerta.PENDIENTE);
        a1.setSeveridad(SeveridadAlerta.ALTA);
        a1.setTsGeneracion(LocalDateTime.now().minusHours(1));
        a1.setValorUmbral(1.0);
        a1.setAccionRecomendada("Planificar IA en ventana optima");
        alertaRepository.save(a1);

        // 2. Alerta PENDIENTE, SALUD, MEDIA
        Alerta a2 = new Alerta();
        a2.setAnimal(animal);
        a2.setDisparador(DisparadorAlerta.SALUD);
        a2.setEstado(EstadoAlerta.PENDIENTE);
        a2.setSeveridad(SeveridadAlerta.MEDIA);
        a2.setTsGeneracion(LocalDateTime.now().minusHours(2));
        a2.setValorUmbral(1.0);
        alertaRepository.save(a2);

        // 3. Alerta PENDIENTE, ABIGEATO, BAJA
        Alerta a3 = new Alerta();
        a3.setAnimal(animal);
        a3.setDisparador(DisparadorAlerta.ABIGEATO);
        a3.setEstado(EstadoAlerta.PENDIENTE);
        a3.setSeveridad(SeveridadAlerta.BAJA);
        a3.setTsGeneracion(LocalDateTime.now().minusHours(3));
        a3.setValorUmbral(1.0);
        alertaRepository.save(a3);

        // Test 1: Sin filtros, trae las 3 alertas activas y ordenadas por severidad descendente por defecto
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/alertas")
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // Por defecto ordena severidad DESC: ALTA (1), MEDIA (2), BAJA (3)
                .andExpect(jsonPath("$[0].severidad").value("ALTA"))
                .andExpect(jsonPath("$[0].accionRecomendada").value("Planificar IA en ventana optima"))
                .andExpect(jsonPath("$[1].severidad").value("MEDIA"))
                .andExpect(jsonPath("$[2].severidad").value("BAJA"));

        // Test 2: Filtro por tipo (REPRODUCTIVO -> CELO)
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/alertas")
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString())
                .param("tipo", "REPRODUCTIVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("CELO"))
                .andExpect(jsonPath("$[0].accionRecomendada").value("Planificar IA en ventana optima"));

        // Test 3: Filtro por severidad (MEDIA)
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/alertas")
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString())
                .param("severidad", "MEDIA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severidad").value("MEDIA"));

        // Test 4: Filtros combinados (SANITARIO + MEDIA)
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/alertas")
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString())
                .param("tipo", "SANITARIO")
                .param("severidad", "MEDIA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("SALUD"))
                .andExpect(jsonPath("$[0].severidad").value("MEDIA"));

        // Test 5: Resultado vacío (REPRODUCTIVO + BAJA)
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/alertas")
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString())
                .param("tipo", "REPRODUCTIVO")
                .param("severidad", "BAJA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // =========================================================================
    // Control de acceso por rol
    // =========================================================================

    @Test
    @DisplayName("Campero intenta actualizar alerta → 403 Forbidden")
    @SuppressWarnings("null")
    void actualizarAlerta_comoCampero_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + camperoToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sin token → 401 Unauthorized")
    @SuppressWarnings("null")
    void actualizarAlerta_sinToken_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Validaciones de negocio
    // =========================================================================

    @Test
    @DisplayName("Productor intenta actualizar alerta de otro establecimiento → 403")
    @SuppressWarnings("null")
    void actualizarAlerta_deOtroEstablecimiento_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaOtroEstId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Alerta inexistente → 400")
    @SuppressWarnings("null")
    void actualizarAlerta_idInexistente_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", 999999L)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Parámetro estado inválido → 400")
    @SuppressWarnings("null")
    void actualizarAlerta_estadoInvalido_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "ESTADO_INEXISTENTE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Confirmar alerta ya CONFIRMADA → 400 (transición inválida)")
    @SuppressWarnings("null")
    void confirmarAlerta_yaConfirmada_returns400() throws Exception {
        // Primera confirmación
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isOk());

        // Intento de segunda confirmación
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Confirmar alerta ya DESCARTADA → 400 (transición inválida)")
    @SuppressWarnings("null")
    void confirmarAlerta_yaDescartada_returns400() throws Exception {
        // Primero descartar
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "DESCARTADA"))
                .andExpect(status().isOk());

        // Luego intentar confirmar
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("estado", "CONFIRMADA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Falta el parámetro estado → 400")
    @SuppressWarnings("null")
    void actualizarAlerta_sinParametroEstado_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/alertas/estado/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/v1/alertas/{id} (Detalle)
    // =========================================================================

    @Test
    @DisplayName("Detalle alerta existente → 200 OK")
    @SuppressWarnings("null")
    void obtenerDetalleAlerta_exitoso_returns200() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/v1/alertas/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertaId))
                .andExpect(jsonPath("$.accionRecomendada").value("Planificar inseminación artificial"));
    }

    @Test
    @DisplayName("Detalle alerta inexistente → 404 Not Found")
    @SuppressWarnings("null")
    void obtenerDetalleAlerta_inexistente_returns404() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/alertas/{id}", 999999L)
                        .header("Authorization", "Bearer " + productorToken)
                        .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Detalle alerta de otro establecimiento → 403 Forbidden")
    @SuppressWarnings("null")
    void obtenerDetalleAlerta_accesoCruzado_returns403() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/v1/alertas/{id}", alertaOtroEstId)
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Detalle con rol no autorizado (ADMINISTRADOR) → 403 Forbidden")
    @SuppressWarnings("null")
    void obtenerDetalleAlerta_rolNoAutorizado_returns403() throws Exception {
        crearUsuario("admin@vigia.dev", "admin123", Rol.ADMINISTRADOR);
        String adminToken = getTokenFor("admin@vigia.dev", "admin123");

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/v1/alertas/{id}", alertaId)
                .header("Authorization", "Bearer " + adminToken)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Detalle alerta reproductiva → sin bloque detalleSanitario, con dato disparador genérico")
    @SuppressWarnings("null")
    void obtenerDetalleAlerta_reproductiva_sinDetalleSanitario() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/v1/alertas/{id}", alertaId)
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalleSanitario").doesNotExist())
                .andExpect(jsonPath("$.datoDisparador").value("CELO"))
                .andExpect(jsonPath("$.valorDetectado").value(95.0))
                .andExpect(jsonPath("$.valorUmbral").value(80.0))
                .andExpect(jsonPath("$.descripcionDatoOrigen").isNotEmpty());
    }

    @Test
    @DisplayName("Detalle alerta geoespacial (ABIGEATO) → tipo de dato, valor, timestamp, umbral y descripción")
    @SuppressWarnings("null")
    void obtenerDetalleAlerta_abigeato_returnsDatoDisparadorGenerico() throws Exception {
        Animal animal = animalRepository.findAll().stream()
                .filter(a -> a.getEstablecimiento().getId().equals(establecimientoId))
                .findFirst().orElseThrow();

        Alerta alertaAbigeato = new Alerta();
        alertaAbigeato.setAnimal(animal);
        alertaAbigeato.setDisparador(DisparadorAlerta.ABIGEATO);
        alertaAbigeato.setEstado(EstadoAlerta.PENDIENTE);
        alertaAbigeato.setTsGeneracion(LocalDateTime.now());
        alertaAbigeato.setValorUmbral(1.5);
        alertaAbigeato.setValorDetectado(3.2);
        alertaAbigeato.setTipo("ABIGEATO");
        alertaAbigeato = alertaRepository.save(alertaAbigeato);

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/v1/alertas/{id}", alertaAbigeato.getId())
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalleSanitario").doesNotExist())
                .andExpect(jsonPath("$.datoDisparador").value("ABIGEATO"))
                .andExpect(jsonPath("$.tsGeneracion").exists())
                .andExpect(jsonPath("$.valorDetectado").value(3.2))
                .andExpect(jsonPath("$.valorUmbral").value(1.5))
                .andExpect(jsonPath("$.descripcionDatoOrigen").isNotEmpty());
    }

    @Test
    @DisplayName("Detalle alerta sanitaria → 200 con causa, severidad y serie comparativa (snapshot)")
    @SuppressWarnings("null")
    void obtenerDetalleAlerta_sanitaria_returnsDetalleSanitario() throws Exception {
        Animal animal = animalRepository.findAll().stream()
                .filter(a -> a.getEstablecimiento().getId().equals(establecimientoId))
                .findFirst().orElseThrow();

        String serieComparativaJson = "{"
                + "\"descripcionDesvio\":\"Actividad promedio 66% por debajo del perfil de referencia del animal "
                + "en el bloque 0-6h. Severidad media. Posible causa: Enfermedad moderada / Cojera clínica.\","
                + "\"ventanaDesde\":\"2026-01-15T00:00:00\",\"ventanaHasta\":\"2026-01-15T23:55:00\","
                + "\"perfilCalculadoEn\":\"2026-01-12T08:00:00\","
                + "\"serieActividad\":["
                + "{\"bloque\":\"0-6h\",\"actual\":1.0,\"referenciaMedia\":3.0,\"referenciaDesvio\":0.5},"
                + "{\"bloque\":\"6-12h\",\"actual\":5.0,\"referenciaMedia\":2.0,\"referenciaDesvio\":0.4},"
                + "{\"bloque\":\"12-18h\",\"actual\":5.0,\"referenciaMedia\":4.0,\"referenciaDesvio\":0.6},"
                + "{\"bloque\":\"18-24h\",\"actual\":5.0,\"referenciaMedia\":3.5,\"referenciaDesvio\":0.5}],"
                + "\"serieDesplazamiento\":["
                + "{\"bloque\":\"0-6h\",\"actual\":9.8,\"referenciaMedia\":10.0,\"referenciaDesvio\":2.0},"
                + "{\"bloque\":\"6-12h\",\"actual\":10.0,\"referenciaMedia\":8.0,\"referenciaDesvio\":1.5},"
                + "{\"bloque\":\"12-18h\",\"actual\":10.0,\"referenciaMedia\":12.0,\"referenciaDesvio\":2.5},"
                + "{\"bloque\":\"18-24h\",\"actual\":10.0,\"referenciaMedia\":9.0,\"referenciaDesvio\":2.0}]}";

        Alerta alertaSalud = new Alerta();
        alertaSalud.setAnimal(animal);
        alertaSalud.setDisparador(DisparadorAlerta.SALUD);
        alertaSalud.setEstado(EstadoAlerta.PENDIENTE);
        alertaSalud.setSeveridad(SeveridadAlerta.MEDIA);
        alertaSalud.setTsGeneracion(LocalDateTime.now());
        alertaSalud.setValorUmbral(0.5);
        alertaSalud.setValorDetectado(0.82);
        alertaSalud.setConfianzaModelo(0.82f);
        alertaSalud.setDesvioDetectado(true);
        alertaSalud.setPosibleCausa("Enfermedad moderada / Cojera clínica");
        alertaSalud.setTipo("SALUD");
        alertaSalud.setSerieComparativaJson(serieComparativaJson);
        alertaSalud = alertaRepository.save(alertaSalud);

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/v1/alertas/{id}", alertaSalud.getId())
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorDetectado").value(0.82))
                .andExpect(jsonPath("$.valorUmbral").value(0.5))
                .andExpect(jsonPath("$.descripcionDatoOrigen").isNotEmpty())
                .andExpect(jsonPath("$.detalleSanitario.desvioDetectado").value(true))
                .andExpect(jsonPath("$.detalleSanitario.severidad").value("MEDIA"))
                .andExpect(jsonPath("$.detalleSanitario.posibleCausa").value("Enfermedad moderada / Cojera clínica"))
                .andExpect(jsonPath("$.detalleSanitario.descripcionDesvio").isNotEmpty())
                .andExpect(jsonPath("$.detalleSanitario.ventanaDesde").exists())
                .andExpect(jsonPath("$.detalleSanitario.serieActividad.length()").value(4))
                .andExpect(jsonPath("$.detalleSanitario.serieActividad[0].bloque").value("0-6h"))
                .andExpect(jsonPath("$.detalleSanitario.serieActividad[0].referenciaMedia").value(3.0))
                .andExpect(jsonPath("$.detalleSanitario.serieDesplazamiento.length()").value(4));
    }

    @Test
    @DisplayName("Detalle alerta sanitaria sin snapshot → 200, series null y descripción genérica")
    @SuppressWarnings("null")
    void obtenerDetalleAlerta_sanitaria_sinSnapshot_returnsEscalares() throws Exception {
        Animal animal = animalRepository.findAll().stream()
                .filter(a -> a.getEstablecimiento().getId().equals(establecimientoId))
                .findFirst().orElseThrow();

        Alerta alertaSalud = new Alerta();
        alertaSalud.setAnimal(animal);
        alertaSalud.setDisparador(DisparadorAlerta.SALUD);
        alertaSalud.setEstado(EstadoAlerta.PENDIENTE);
        alertaSalud.setSeveridad(SeveridadAlerta.ALTA);
        alertaSalud.setTsGeneracion(LocalDateTime.now());
        alertaSalud.setValorUmbral(0.5);
        alertaSalud.setValorDetectado(0.9);
        alertaSalud.setDesvioDetectado(true);
        alertaSalud.setPosibleCausa("Problema severo postrante o complicación de parto");
        alertaSalud.setTipo("SALUD");
        alertaSalud = alertaRepository.save(alertaSalud);

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/v1/alertas/{id}", alertaSalud.getId())
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalleSanitario.severidad").value("ALTA"))
                .andExpect(jsonPath("$.detalleSanitario.descripcionDesvio").isNotEmpty())
                .andExpect(jsonPath("$.detalleSanitario.serieActividad").doesNotExist())
                .andExpect(jsonPath("$.detalleSanitario.serieDesplazamiento").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/alertas/resumen - Conteo correcto de alertas por tipo")
    @SuppressWarnings("null")
    void obtenerResumenAlertas_conteoCorrecto() throws Exception {
        // En setUp ya se creó 1 alerta de CELO (Reproductivo) en estado PENDIENTE.
        
        Animal animal = animalRepository.findAll().get(0);
        
        // Agregamos 1 de salud
        Alerta alertaSanidad = new Alerta();
        alertaSanidad.setAnimal(animal);
        alertaSanidad.setDisparador(DisparadorAlerta.SALUD);
        alertaSanidad.setEstado(EstadoAlerta.PENDIENTE);
        alertaSanidad.setSeveridad(SeveridadAlerta.ALTA);
        alertaSanidad.setTsGeneracion(LocalDateTime.now());
        alertaSanidad.setValorUmbral(0.0);
        alertaSanidad.setValorDetectado(0.0);
        alertaRepository.save(alertaSanidad);

        // Agregamos otra de salud
        Alerta alertaSanidad2 = new Alerta();
        alertaSanidad2.setAnimal(animal);
        alertaSanidad2.setDisparador(DisparadorAlerta.SALUD);
        alertaSanidad2.setEstado(EstadoAlerta.PENDIENTE);
        alertaSanidad2.setSeveridad(SeveridadAlerta.MEDIA);
        alertaSanidad2.setTsGeneracion(LocalDateTime.now());
        alertaSanidad2.setValorUmbral(0.0);
        alertaSanidad2.setValorDetectado(0.0);
        alertaRepository.save(alertaSanidad2);

        // Agregamos 1 geoespacial
        Alerta alertaGeo = new Alerta();
        alertaGeo.setAnimal(animal);
        alertaGeo.setDisparador(DisparadorAlerta.ABIGEATO);
        alertaGeo.setEstado(EstadoAlerta.PENDIENTE);
        alertaGeo.setSeveridad(SeveridadAlerta.ALTA);
        alertaGeo.setTsGeneracion(LocalDateTime.now());
        alertaGeo.setValorUmbral(0.0);
        alertaGeo.setValorDetectado(0.0);
        alertaRepository.save(alertaGeo);
        
        // Agregamos 1 DESCARTADA (no debería contar si el resumen es de activas)
        Alerta alertaDescartada = new Alerta();
        alertaDescartada.setAnimal(animal);
        alertaDescartada.setDisparador(DisparadorAlerta.CELO);
        alertaDescartada.setEstado(EstadoAlerta.DESCARTADA);
        alertaDescartada.setSeveridad(SeveridadAlerta.BAJA);
        alertaDescartada.setTsGeneracion(LocalDateTime.now());
        alertaDescartada.setValorUmbral(0.0);
        alertaDescartada.setValorDetectado(0.0);
        alertaRepository.save(alertaDescartada);

        mockMvc.perform(MockMvcRequestBuilders
                .get("/api/v1/alertas/resumen")
                .header("Authorization", "Bearer " + productorToken)
                .param("establecimientoId", establecimientoId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reproductivo").value(1))
                .andExpect(jsonPath("$.sanitario").value(2))
                .andExpect(jsonPath("$.geoespacial").value(1));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Usuario crearUsuario(String email, String password, Rol rol) {
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setNombre(rol.name().toLowerCase());
        u.setRol(rol);
        u.setActivo(true);
        u.setFechaAlta(LocalDate.now());
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
}
