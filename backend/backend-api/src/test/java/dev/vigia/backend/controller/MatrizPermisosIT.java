package dev.vigia.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matriz rol × endpoint de todos los controllers de {@code /api/v1} (evidencia de RNF04). Para cada endpoint se declara
 * qué roles lo pueden usar, tal como lo dice su {@code @PreAuthorize}, y el test genera un caso por cada rol que NO
 * puede: todos tienen que responder 403. PENDIENTE (usuario registrado sin rol asignado todavía) no puede nada fuera de
 * lo que es de cualquier usuario autenticado.
 * <p>
 * Los requests llevan los parámetros obligatorios y un cuerpo válido: Spring resuelve y valida los argumentos antes de
 * evaluar {@code @PreAuthorize}, y sin eso el 403 quedaría tapado por un 400. Otro test controla que la tabla cubra
 * todos los endpoints registrados, para que un endpoint nuevo no quede afuera sin que nadie lo note.
 */
@DisplayName("H0002-06 - Matriz de permisos rol × endpoint (RNF04) — IT")
class MatrizPermisosIT extends SeguridadItBase {

    private static final String A = "ADMINISTRADOR";
    private static final String P = "PRODUCTOR";
    private static final String V = "VETERINARIO";
    private static final String C = "CAMPERO";
    private static final List<String> ROLES = List.of(A, P, V, C, "PENDIENTE");

    /** Endpoints de cualquier usuario autenticado: no entran en los casos de 403. */
    private static final Set<String> TODOS = Set.of();

    // Los JWT solo llevan el email: siguen sirviendo aunque el escenario se vuelva a cargar con otros ids.
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();
    private static final String EMAIL_PENDIENTE = emailUnico();

    private static final String PERIMETRO = "{\"nombre\":\"Perímetro IT\",\"establecimientoId\":{estA},"
            + "\"coordenadas\":[{\"latitud\":-36.5,\"longitud\":-61.5},{\"latitud\":-36.51,\"longitud\":-61.5},"
            + "{\"latitud\":-36.51,\"longitud\":-61.51}]}";
    private static final String ANIMAL = "{\"idCaravanaOficial\":\"A-999\",\"fechaNacimiento\":\"2021-01-01\","
            + "\"rodeoLoteId\":{rodeoA},\"establecimientoId\":{estA}}";
    private static final String PESO = "{\"valorKg\":400,\"unidad\":\"KG\",\"fecha\":\"2026-01-01\"}";
    private static final String ESTABLECIMIENTO = "{\"nombre\":\"Fixture IT matriz\",\"latitud\":-36.7,"
            + "\"longitud\":-61.7}";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Override
    protected boolean recargarEscenarioEnCadaTest() {
        return false;
    }

    record Endpoint(String metodo, String patron, String url, Set<String> roles, String cuerpo, boolean multipart) {

        String clave() {
            return metodo + " " + patron;
        }
    }

    // ── La matriz ────────────────────────────────────────────────────────────

    static List<Endpoint> matriz() {
        List<Endpoint> m = new ArrayList<>();

        // UsuarioController
        m.add(ep("GET", "/api/v1/usuarios/productores", Set.of(A)));
        m.add(ep("DELETE", "/api/v1/usuarios/productores/{id}", "/api/v1/usuarios/productores/{productorB}", Set.of(A)));
        m.add(ep("DELETE", "/api/v1/usuarios/empleados/{establecimientoId}/{id}",
                "/api/v1/usuarios/empleados/{estA}/{camperoA}", Set.of(P)));
        m.add(ep("GET", "/api/v1/usuarios", Set.of(A)));
        m.add(ep("GET", "/api/v1/usuarios/empleados", Set.of(P)));
        m.add(ep("PATCH", "/api/v1/usuarios/{id}/estado", "/api/v1/usuarios/{camperoB}/estado", Set.of(A)));
        m.add(ep("GET", "/api/v1/usuarios/me", TODOS));
        m.add(ep("PUT", "/api/v1/usuarios/me", TODOS));
        m.add(ep("PUT", "/api/v1/usuarios/me/password", TODOS));

        // EstablecimientoController
        m.add(ep("POST", "/api/v1/establecimientos", "/api/v1/establecimientos", Set.of(A), ESTABLECIMIENTO));
        m.add(ep("GET", "/api/v1/establecimientos", Set.of(A, P, V, C)));
        m.add(ep("GET", "/api/v1/establecimientos/{id}", "/api/v1/establecimientos/{estA}", Set.of(A)));
        m.add(ep("PUT", "/api/v1/establecimientos/{id}", "/api/v1/establecimientos/{estA}", Set.of(A, P),
                ESTABLECIMIENTO));
        m.add(ep("DELETE", "/api/v1/establecimientos/{id}", "/api/v1/establecimientos/{estA}", Set.of(A)));
        m.add(ep("PUT", "/api/v1/establecimientos/activar/{id}", "/api/v1/establecimientos/activar/{estA}",
                Set.of(A)));
        m.add(ep("GET", "/api/v1/establecimientos/{id}/intervenciones",
                "/api/v1/establecimientos/{estA}/intervenciones", Set.of(A, P, V, C)));
        m.add(ep("GET", "/api/v1/establecimientos/{id}/pesos", "/api/v1/establecimientos/{estA}/pesos",
                Set.of(A, P, V, C)));
        m.add(ep("GET", "/api/v1/establecimientos/{id}/anestro", "/api/v1/establecimientos/{estA}/anestro",
                Set.of(P, V)));
        m.add(ep("PUT", "/api/v1/establecimientos/{id}/anestro", "/api/v1/establecimientos/{estA}/anestro",
                Set.of(P, V)));
        m.add(ep("GET", "/api/v1/establecimientos/{id}/aislamiento", "/api/v1/establecimientos/{estA}/aislamiento",
                Set.of(P, V)));
        m.add(ep("PUT", "/api/v1/establecimientos/{id}/aislamiento", "/api/v1/establecimientos/{estA}/aislamiento",
                Set.of(P, V)));

        // InvitacionController
        m.add(ep("POST", "/api/v1/invitaciones/productor", "/api/v1/invitaciones/productor", Set.of(A),
                "{\"email\":\"" + emailUnico() + "\",\"establecimientoId\":{estA}}"));
        m.add(ep("POST", "/api/v1/invitaciones/empleado", "/api/v1/invitaciones/empleado", Set.of(P),
                "{\"email\":\"" + emailUnico() + "\",\"establecimientoId\":{estA},\"rol\":\"CAMPERO\"}"));
        m.add(ep("GET", "/api/v1/invitaciones/productor", Set.of(A)));
        m.add(ep("GET", "/api/v1/invitaciones/empleado", Set.of(P)));
        m.add(ep("GET", "/api/v1/invitaciones/mis-invitaciones", TODOS));
        m.add(ep("POST", "/api/v1/invitaciones/{id}/aceptar", TODOS));
        m.add(ep("POST", "/api/v1/invitaciones/token/{token}/aceptar", TODOS));
        m.add(ep("POST", "/api/v1/invitaciones/{id}/rechazar", TODOS));
        m.add(ep("DELETE", "/api/v1/invitaciones/{id}", "/api/v1/invitaciones/999999999", Set.of(A, P)));

        // AlertaController
        Set<String> pvc = Set.of(P, V, C);
        m.add(ep("GET", "/api/v1/alertas", pvc));
        m.add(ep("GET", "/api/v1/alertas/resumen", pvc));
        m.add(ep("GET", "/api/v1/alertas/celo", pvc));
        m.add(ep("GET", "/api/v1/alertas/salud", pvc));
        m.add(ep("GET", "/api/v1/alertas/{id}", "/api/v1/alertas/{alertaA}", pvc));
        m.add(ep("GET", "/api/v1/alertas/{id}/ventana-inseminacion", "/api/v1/alertas/{alertaA}/ventana-inseminacion",
                pvc));
        m.add(ep("GET", "/api/v1/alertas/{id}/ventana", "/api/v1/alertas/{alertaA}/ventana", pvc));
        m.add(ep("PUT", "/api/v1/alertas/{id}/archivar", "/api/v1/alertas/{alertaA}/archivar", pvc));
        m.add(ep("PUT", "/api/v1/alertas/estado/{id}", "/api/v1/alertas/estado/{alertaA}?estado=DESCARTADA", pvc));
        m.add(ep("POST", "/api/v1/alertas/intervenir/{id}", "/api/v1/alertas/intervenir/{alertaA}", pvc));
        m.add(ep("POST", "/api/v1/alertas/{id}/diagnosticos", "/api/v1/alertas/{alertaA}/diagnosticos", Set.of(V)));
        m.add(ep("GET", "/api/v1/alertas/diagnosticos", pvc));
        m.add(ep("GET", "/api/v1/alertas/stream", pvc));

        // AnimalController
        Set<String> apvc = Set.of(A, P, V, C);
        m.add(ep("GET", "/api/v1/animales", apvc));
        m.add(ep("GET", "/api/v1/animales/mapa", apvc));
        // Mismo alcance que /mapa: hoy no tiene @PreAuthorize.
        m.add(ep("GET", "/api/v1/animales/mapa/stream", apvc));
        m.add(ep("GET", "/api/v1/animales/{id}", "/api/v1/animales/{animalA}", apvc));
        m.add(ep("POST", "/api/v1/animales", "/api/v1/animales", Set.of(P), ANIMAL));
        m.add(ep("DELETE", "/api/v1/animales/{id}", "/api/v1/animales/{animalA}", Set.of(P)));
        m.add(ep("PUT", "/api/v1/animales/{id}", "/api/v1/animales/{animalA}", Set.of(P), ANIMAL));
        m.add(ep("GET", "/api/v1/animales/{id}/estado-reproductivo", "/api/v1/animales/{animalA}/estado-reproductivo",
                apvc));
        m.add(ep("POST", "/api/v1/animales/{id}/estado-reproductivo", "/api/v1/animales/{animalA}/estado-reproductivo",
                Set.of(P, V), "{\"estado\":\"VACIA\"}"));
        m.add(ep("PUT", "/api/v1/animales/{id}/estado-reproductivo/{estadoId}",
                "/api/v1/animales/{animalA}/estado-reproductivo/999999999", Set.of(P, V), "{\"estado\":\"VACIA\"}"));
        m.add(ep("GET", "/api/v1/animales/{id}/intervenciones", "/api/v1/animales/{animalA}/intervenciones", apvc));
        m.add(ep("POST", "/api/v1/animales/{id}/intervenciones", "/api/v1/animales/{animalA}/intervenciones",
                Set.of(V)));
        m.add(new Endpoint("POST", "/api/v1/animales/csv", "/api/v1/animales/csv", Set.of(P), null, true));
        m.add(new Endpoint("POST", "/api/v1/animales/estado-reproductivo/csv", "/api/v1/animales/estado-reproductivo/csv",
                Set.of(P), null, true));
        m.add(ep("GET", "/api/v1/animales/{id}/metricas", "/api/v1/animales/{animalA}/metricas", Set.of(P, V)));
        m.add(ep("POST", "/api/v1/animales/{id}/estado-salud", "/api/v1/animales/{animalA}/estado-salud", Set.of(P, V),
                "{\"estado\":\"SANO\"}"));
        m.add(ep("GET", "/api/v1/animales/{id}/estado-salud", "/api/v1/animales/{animalA}/estado-salud", apvc));
        m.add(ep("GET", "/api/v1/animales/{id}/historial-pesos", "/api/v1/animales/{animalA}/historial-pesos", apvc));
        m.add(ep("POST", "/api/v1/animales/{id}/historial-pesos", "/api/v1/animales/{animalA}/historial-pesos", pvc,
                PESO));
        m.add(ep("PUT", "/api/v1/animales/{id}/historial-pesos/{pesoId}",
                "/api/v1/animales/{animalA}/historial-pesos/999999999", pvc, PESO));
        m.add(ep("DELETE", "/api/v1/animales/{id}/historial-pesos/{pesoId}",
                "/api/v1/animales/{animalA}/historial-pesos/999999999", pvc));
        m.add(ep("GET", "/api/v1/animales/{id}/indicadores-salud", "/api/v1/animales/{animalA}/indicadores-salud",
                Set.of(A, P, V)));
        m.add(ep("GET", "/api/v1/animales/{id}/linea-tiempo-alertas", "/api/v1/animales/{animalA}/linea-tiempo-alertas",
                pvc));
        m.add(ep("POST", "/api/v1/animales/{id}/vincular-caravana", "/api/v1/animales/{animalA}/vincular-caravana",
                Set.of(A, P, C), "{\"caravanaId\":{caravanaLibreA}}"));
        m.add(ep("DELETE", "/api/v1/animales/{id}/desvincular-caravana",
                "/api/v1/animales/{animalA}/desvincular-caravana", Set.of(A, P, C)));
        m.add(ep("POST", "/api/v1/animales/asignar-caravanas", Set.of(A, P, C)));
        m.add(ep("POST", "/api/v1/animales/asignaciones-caravanas/excel", "/api/v1/animales/asignaciones-caravanas/excel",
                Set.of(A, P, C), "[]"));
        m.add(ep("GET", "/api/v1/animales/{id}/cohesion", "/api/v1/animales/{animalA}/cohesion", pvc));

        // CaravanaController
        m.add(ep("POST", "/api/v1/caravana", "/api/v1/caravana", Set.of(A),
                "{\"establecimientoId\":{estA},\"dev_eui\":\"IT-MATRIZ-01\"}"));
        m.add(ep("GET", "/api/v1/caravana", Set.of(A, P, C)));
        m.add(ep("GET", "/api/v1/caravana/disponibles", Set.of(A, P, C)));
        m.add(ep("PATCH", "/api/v1/caravana/{id}/baja", "/api/v1/caravana/{caravanaLibreA}/baja", Set.of(A)));
        m.add(ep("PATCH", "/api/v1/caravana/{id}/reactivar", "/api/v1/caravana/{caravanaLibreA}/reactivar",
                Set.of(A)));

        // PerimetroController
        Set<String> pa = Set.of(P, A);
        m.add(ep("POST", "/api/v1/perimetros", "/api/v1/perimetros", pa, PERIMETRO));
        m.add(ep("PUT", "/api/v1/perimetros/{id}", "/api/v1/perimetros/{perimetroA}", pa, PERIMETRO));
        m.add(ep("GET", "/api/v1/perimetros/{id}", "/api/v1/perimetros/{perimetroA}", apvc));
        m.add(ep("GET", "/api/v1/perimetros", apvc));
        m.add(ep("PATCH", "/api/v1/perimetros/{id}/activar", "/api/v1/perimetros/{perimetroA}/activar", pa));
        m.add(ep("PATCH", "/api/v1/perimetros/{id}/desactivar", "/api/v1/perimetros/{perimetroA}/desactivar", pa));
        m.add(ep("DELETE", "/api/v1/perimetros/{id}", "/api/v1/perimetros/{perimetroA}", pa));

        // RodeoLoteController
        m.add(ep("GET", "/api/v1/rodeo-lotes", apvc));
        m.add(ep("POST", "/api/v1/rodeo-lotes", "/api/v1/rodeo-lotes", Set.of(P),
                "{\"nombre\":\"Rodeo IT\",\"establecimientoId\":{estA}}"));
        m.add(ep("DELETE", "/api/v1/rodeo-lotes/{id}", "/api/v1/rodeo-lotes/{rodeoA}", Set.of(P)));
        m.add(ep("GET", "/api/v1/rodeo-lotes/{id}", "/api/v1/rodeo-lotes/{rodeoA}", Set.of(P)));

        // MapaCalorController
        m.add(ep("GET", "/api/v1/mapa-calor", apvc));

        return m;
    }

    private static Endpoint ep(String metodo, String patron, Set<String> roles) {
        return ep(metodo, patron, patron, roles);
    }

    private static Endpoint ep(String metodo, String patron, String url, Set<String> roles) {
        return ep(metodo, patron, url, roles, metodo.equals("GET") || metodo.equals("DELETE") ? null : "{}");
    }

    private static Endpoint ep(String metodo, String patron, String url, Set<String> roles, String cuerpo) {
        return new Endpoint(metodo, patron, url, roles, cuerpo, false);
    }

    // ── Casos ────────────────────────────────────────────────────────────────

    static Stream<Arguments> accesosNoPermitidos() {
        return matriz().stream()
                .filter(e -> !e.roles().isEmpty())
                .flatMap(e -> ROLES.stream()
                        .filter(rol -> !e.roles().contains(rol))
                        .map(rol -> Arguments.of(rol, e.metodo(), e.patron(), e)));
    }

    @ParameterizedTest(name = "H0002-06 [{0}] {1} {2}")
    @MethodSource("accesosNoPermitidos")
    @DisplayName("H0002-06 - RBAC - Acceso no permitido devuelve 403")
    void H0002_06_accesoNoPermitidoDevuelve403(String rol, String metodo, String patron, Endpoint endpoint)
            throws Exception {
        MockHttpServletRequestBuilder request = armarRequest(endpoint);
        request.header("Authorization", "Bearer " + tokenPorRol(rol));

        MvcResult resultado = mockMvc.perform(request).andReturn();

        assertThat(resultado.getResponse().getStatus())
                .as("%s %s con rol %s; respuesta: %s", metodo, patron, rol, resultado.getResponse().getContentAsString())
                .isEqualTo(403);
    }

    static Stream<Arguments> todosLosEndpoints() {
        return matriz().stream().map(e -> Arguments.of(e.metodo(), e.patron(), e));
    }

    @ParameterizedTest(name = "H0002-06 [SIN TOKEN] {0} {1}")
    @MethodSource("todosLosEndpoints")
    @DisplayName("H0002-06 - RBAC - Acceso sin autenticar devuelve 401")
    void H0002_06_sinAutenticarDevuelve401(String metodo, String patron, Endpoint endpoint) throws Exception {
        MvcResult resultado = mockMvc.perform(armarRequest(endpoint)).andReturn();

        assertThat(resultado.getResponse().getStatus())
                .as("%s %s sin token; respuesta: %s", metodo, patron, resultado.getResponse().getContentAsString())
                .isEqualTo(401);
    }

    private MockHttpServletRequestBuilder armarRequest(Endpoint endpoint) throws Exception {
        Map<String, Object> ids = idsDelEscenario();
        String url = agregarEstablecimiento(resolver(endpoint.url(), ids), ids);
        if (endpoint.multipart()) {
            return MockMvcRequestBuilders.multipart(HttpMethod.POST, url)
                    .file(new MockMultipartFile("file", "datos.csv", "text/csv",
                            "idCaravanaOficial\n".getBytes(StandardCharsets.UTF_8)));
        }
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(HttpMethod.valueOf(endpoint.metodo()), url);
        if (endpoint.cuerpo() != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(resolver(endpoint.cuerpo(), ids));
        }
        return request;
    }

    @Test
    @DisplayName("H0002-06 - RBAC - La matriz cubre todos los endpoints de /api/v1")
    void H0002_06_matrizCubreTodosLosEndpoints() {
        Set<String> registrados = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, metodo) -> {
            for (String patron : info.getPatternValues()) {
                if (!patron.startsWith("/api/v1/") || patron.startsWith("/api/v1/auth/")) {
                    continue;
                }
                for (RequestMethod verbo : info.getMethodsCondition().getMethods()) {
                    registrados.add(verbo.name() + " " + patron);
                }
            }
        });
        Set<String> declarados = matriz().stream().map(Endpoint::clave).collect(Collectors.toCollection(TreeSet::new));

        assertThat(declarados).as("endpoints de la matriz que ya no existen").isSubsetOf(registrados);
        assertThat(registrados).as("endpoints sin declarar en la matriz").isSubsetOf(declarados);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private String tokenPorRol(String rol) throws Exception {
        String email = switch (rol) {
            case A -> email("admin");
            case P -> email("productorA");
            case V -> email("veterinarioA");
            case C -> email("camperoA");
            default -> EMAIL_PENDIENTE;
        };
        if (rol.equals("PENDIENTE") && jdbcTemplate.queryForObject("SELECT count(*) FROM usuarios WHERE email = ?",
                Integer.class, email) == 0) {
            crearUsuario(email, "PENDIENTE", null);
        }
        String token = TOKENS.get(email);
        if (token == null) {
            token = tokenDe(email, credenciales.password());
            TOKENS.put(email, token);
        }
        return token;
    }

    private Map<String, Object> idsDelEscenario() {
        Map<String, Object> ids = new HashMap<>();
        ids.put("estA", idEstablecimiento(EST_A));
        ids.put("animalA", idsAnimales(EST_A).get(0));
        ids.put("alertaA", idsAlertas(EST_A).get(0));
        ids.put("rodeoA", idRodeo(EST_A));
        ids.put("perimetroA", idPerimetro(EST_A));
        ids.put("caravanaLibreA", idCaravanaLibre(EST_A));
        ids.put("camperoA", idUsuario(email("camperoA")));
        ids.put("camperoB", idUsuario(email("camperoB")));
        ids.put("productorB", idUsuario(email("productorB")));
        // Rutas con variables que no dependen del escenario (id o token de una invitación cualquiera).
        ids.put("id", 999999999L);
        ids.put("token", "token-inexistente");
        return ids;
    }

    private static String resolver(String plantilla, Map<String, Object> ids) {
        String resultado = plantilla;
        for (Map.Entry<String, Object> id : ids.entrySet()) {
            resultado = resultado.replace("{" + id.getKey() + "}", id.getValue().toString());
        }
        return resultado;
    }

    // Varios endpoints exigen ?establecimientoId; a los que no, el parámetro de más no les cambia nada.
    private static String agregarEstablecimiento(String url, Map<String, Object> ids) {
        return url + (url.contains("?") ? "&" : "?") + "establecimientoId=" + ids.get("estA");
    }
}
