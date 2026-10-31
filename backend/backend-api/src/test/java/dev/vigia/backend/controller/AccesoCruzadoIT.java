package dev.vigia.backend.controller;

import dev.vigia.backend.controller.MatrizPermisosIT.Endpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Accesos cruzados entre establecimientos (evidencia de RNF03): un usuario de Fixture A intenta leer o modificar datos
 * de Fixture B, por id del recurso o por {@code establecimientoId}. Todos tienen que responder 403 sin datos de B.
 * <p>
 * Cada acceso se prueba con cada rol de A que tiene permiso sobre el endpoint según la matriz de
 * {@link MatrizPermisosIT}: así el request pasa el {@code @PreAuthorize} y lo que se prueba es la pertenencia, no el
 * rol. Un test de cobertura controla que todo endpoint al que llega un rol de establecimiento esté en la tabla.
 */
@DisplayName("H0035-03 - Accesos cruzados entre establecimientos (RNF03) — IT")
class AccesoCruzadoIT extends SeguridadItBase {

    // Textos que identifican datos de Fixture B: el nombre del establecimiento, los ids oficiales de sus animales
    // (B-101...) y los códigos de sus caravanas (FX-B-01...).
    private static final List<String> HUELLAS_DE_B = List.of(EST_B, "B-10", "FX-B-");

    /** Usuario de Fixture A por rol. */
    private static final Map<String, String> USUARIO_DE_A = Map.of(
            "PRODUCTOR", "productorA", "VETERINARIO", "veterinarioA", "CAMPERO", "camperoA");

    /**
     * Endpoints que no apuntan a datos de un establecimiento: los del propio usuario y el listado de establecimientos,
     * que filtra por usuario en lugar de recibir un id (lo prueba H0003-04).
     */
    private static final Set<String> SIN_RECURSO_DE_ESTABLECIMIENTO = Set.of(
            "GET /api/v1/usuarios/me", "PUT /api/v1/usuarios/me", "PUT /api/v1/usuarios/me/password",
            "GET /api/v1/invitaciones/mis-invitaciones", "GET /api/v1/establecimientos");

    private static final String PESO = "{\"valorKg\":400,\"unidad\":\"KG\",\"fecha\":\"2026-01-01\"}";
    private static final String INTERVENCION = "{\"descripcion\":\"Revisión\",\"tipo\":\"REVISION_VETERINARIA\"}";

    private static String animal(String est, String rodeo) {
        return "{\"idCaravanaOficial\":\"X-" + UUID.randomUUID().toString().substring(0, 8)
                + "\",\"fechaNacimiento\":\"2021-01-01\",\"rodeoLoteId\":{" + rodeo + "},\"establecimientoId\":{"
                + est + "}}";
    }

    private static String perimetro(String est, String lotes) {
        return "{\"nombre\":\"Perímetro IT\",\"establecimientoId\":{" + est + "},\"loteIds\":[" + lotes + "],"
                + "\"coordenadas\":[{\"latitud\":-36.6,\"longitud\":-61.6},{\"latitud\":-36.61,\"longitud\":-61.6},"
                + "{\"latitud\":-36.61,\"longitud\":-61.61}]}";
    }

    record Acceso(String metodo, String patron, String url, String cuerpo, boolean multipart) {
    }

    private static Acceso a(String metodo, String patron, String url) {
        return a(metodo, patron, url, metodo.equals("GET") || metodo.equals("DELETE") ? null : "{}");
    }

    private static Acceso a(String metodo, String patron, String url, String cuerpo) {
        return new Acceso(metodo, patron, url, cuerpo, false);
    }

    // ── La tabla de accesos cruzados ─────────────────────────────────────────

    static List<Acceso> accesos() {
        List<Acceso> t = new ArrayList<>();

        // Usuarios
        t.add(a("GET", "/api/v1/usuarios/empleados", "/api/v1/usuarios/empleados?establecimientoId={estB}"));
        t.add(a("DELETE", "/api/v1/usuarios/empleados/{establecimientoId}/{id}",
                "/api/v1/usuarios/empleados/{estB}/{camperoB}"));
        // Por su propio establecimiento, pero contra un empleado de B.
        t.add(a("DELETE", "/api/v1/usuarios/empleados/{establecimientoId}/{id}",
                "/api/v1/usuarios/empleados/{estA}/{camperoB}"));

        // Establecimientos
        t.add(a("PUT", "/api/v1/establecimientos/{id}", "/api/v1/establecimientos/{estB}",
                "{\"nombre\":\"Fixture B\",\"latitud\":-36.6,\"longitud\":-61.6}"));
        t.add(a("GET", "/api/v1/establecimientos/{id}/intervenciones", "/api/v1/establecimientos/{estB}/intervenciones"));
        t.add(a("GET", "/api/v1/establecimientos/{id}/pesos", "/api/v1/establecimientos/{estB}/pesos"));
        t.add(a("GET", "/api/v1/establecimientos/{id}/anestro", "/api/v1/establecimientos/{estB}/anestro"));
        t.add(a("PUT", "/api/v1/establecimientos/{id}/anestro", "/api/v1/establecimientos/{estB}/anestro",
                "{\"diasDeteccionAnestro\":60}"));
        t.add(a("GET", "/api/v1/establecimientos/{id}/aislamiento", "/api/v1/establecimientos/{estB}/aislamiento"));
        t.add(a("PUT", "/api/v1/establecimientos/{id}/aislamiento", "/api/v1/establecimientos/{estB}/aislamiento",
                "{\"umbralAislamientoM\":150}"));

        // Invitaciones
        t.add(a("POST", "/api/v1/invitaciones/empleado", "/api/v1/invitaciones/empleado",
                "{\"email\":\"" + emailUnico() + "\",\"establecimientoId\":{estB},\"rol\":\"CAMPERO\"}"));
        t.add(a("GET", "/api/v1/invitaciones/empleado", "/api/v1/invitaciones/empleado?establecimientoId={estB}"));
        t.add(a("POST", "/api/v1/invitaciones/{id}/aceptar", "/api/v1/invitaciones/{invitacionAjena}/aceptar"));
        t.add(a("POST", "/api/v1/invitaciones/token/{token}/aceptar",
                "/api/v1/invitaciones/token/{tokenAjeno}/aceptar"));
        t.add(a("POST", "/api/v1/invitaciones/{id}/rechazar", "/api/v1/invitaciones/{invitacionAjena}/rechazar"));
        t.add(a("DELETE", "/api/v1/invitaciones/{id}", "/api/v1/invitaciones/{invitacionDeB}"));

        // Alertas: por establecimientoId de B y, con el establecimiento propio, contra una alerta de B.
        for (String lista : List.of("", "/resumen", "/celo", "/salud", "/diagnosticos", "/stream")) {
            t.add(a("GET", "/api/v1/alertas" + lista, "/api/v1/alertas" + lista + "?establecimientoId={estB}"));
        }
        t.add(a("GET", "/api/v1/alertas/diagnosticos",
                "/api/v1/alertas/diagnosticos?animalId={animalB}&establecimientoId={estA}"));
        for (String est : List.of("estA", "estB")) {
            String q = "?establecimientoId={" + est + "}";
            t.add(a("GET", "/api/v1/alertas/{id}", "/api/v1/alertas/{alertaB}" + q));
            t.add(a("GET", "/api/v1/alertas/{id}/ventana-inseminacion",
                    "/api/v1/alertas/{alertaCeloB}/ventana-inseminacion" + q));
            t.add(a("GET", "/api/v1/alertas/{id}/ventana", "/api/v1/alertas/{alertaCeloB}/ventana" + q));
            t.add(a("PUT", "/api/v1/alertas/{id}/archivar", "/api/v1/alertas/{alertaB}/archivar" + q));
        }
        t.add(a("PUT", "/api/v1/alertas/estado/{id}", "/api/v1/alertas/estado/{alertaB}?estado=DESCARTADA"));
        t.add(a("POST", "/api/v1/alertas/intervenir/{id}", "/api/v1/alertas/intervenir/{alertaB}", INTERVENCION));
        t.add(a("POST", "/api/v1/alertas/{id}/diagnosticos", "/api/v1/alertas/{alertaB}/diagnosticos",
                "{\"diagnostico\":\"Mastitis\",\"tratamiento\":\"Antibiótico\"}"));

        // Animales
        t.add(a("GET", "/api/v1/animales", "/api/v1/animales?establecimientoId={estB}"));
        t.add(a("GET", "/api/v1/animales/mapa", "/api/v1/animales/mapa?establecimientoId={estB}"));
        t.add(a("GET", "/api/v1/animales/mapa/stream", "/api/v1/animales/mapa/stream?establecimientoId={estB}"));
        t.add(a("POST", "/api/v1/animales", "/api/v1/animales", animal("estB", "rodeoB")));
        // En el establecimiento propio, pero en un rodeo de B.
        t.add(a("POST", "/api/v1/animales", "/api/v1/animales", animal("estA", "rodeoB")));
        t.add(a("GET", "/api/v1/animales/{id}", "/api/v1/animales/{animalB}"));
        t.add(a("PUT", "/api/v1/animales/{id}", "/api/v1/animales/{animalB}", animal("estB", "rodeoB")));
        // Mover un animal propio a B.
        t.add(a("PUT", "/api/v1/animales/{id}", "/api/v1/animales/{animalA}", animal("estB", "rodeoB")));
        t.add(a("DELETE", "/api/v1/animales/{id}", "/api/v1/animales/{animalB}"));
        t.add(a("GET", "/api/v1/animales/{id}/estado-reproductivo", "/api/v1/animales/{animalB}/estado-reproductivo"));
        t.add(a("POST", "/api/v1/animales/{id}/estado-reproductivo", "/api/v1/animales/{animalB}/estado-reproductivo",
                "{\"estado\":\"VACIA\"}"));
        t.add(a("PUT", "/api/v1/animales/{id}/estado-reproductivo/{estadoId}",
                "/api/v1/animales/{animalB}/estado-reproductivo/{estadoB}", "{\"estado\":\"VACIA\"}"));
        // Un estado de B colgado de un animal propio.
        t.add(a("PUT", "/api/v1/animales/{id}/estado-reproductivo/{estadoId}",
                "/api/v1/animales/{animalA}/estado-reproductivo/{estadoB}", "{\"estado\":\"VACIA\"}"));
        t.add(a("GET", "/api/v1/animales/{id}/intervenciones", "/api/v1/animales/{animalB}/intervenciones"));
        t.add(a("POST", "/api/v1/animales/{id}/intervenciones", "/api/v1/animales/{animalB}/intervenciones",
                INTERVENCION));
        t.add(new Acceso("POST", "/api/v1/animales/csv", "/api/v1/animales/csv?establecimientoId={estB}", null, true));
        t.add(new Acceso("POST", "/api/v1/animales/estado-reproductivo/csv",
                "/api/v1/animales/estado-reproductivo/csv?establecimientoId={estB}", null, true));
        t.add(a("GET", "/api/v1/animales/{id}/metricas", "/api/v1/animales/{animalB}/metricas"));
        t.add(a("POST", "/api/v1/animales/{id}/estado-salud", "/api/v1/animales/{animalB}/estado-salud",
                "{\"estado\":\"SANO\"}"));
        t.add(a("GET", "/api/v1/animales/{id}/estado-salud", "/api/v1/animales/{animalB}/estado-salud"));
        t.add(a("GET", "/api/v1/animales/{id}/historial-pesos", "/api/v1/animales/{animalB}/historial-pesos"));
        t.add(a("POST", "/api/v1/animales/{id}/historial-pesos", "/api/v1/animales/{animalB}/historial-pesos", PESO));
        t.add(a("PUT", "/api/v1/animales/{id}/historial-pesos/{pesoId}",
                "/api/v1/animales/{animalB}/historial-pesos/{pesoB}", PESO));
        t.add(a("DELETE", "/api/v1/animales/{id}/historial-pesos/{pesoId}",
                "/api/v1/animales/{animalB}/historial-pesos/{pesoB}"));
        t.add(a("GET", "/api/v1/animales/{id}/indicadores-salud", "/api/v1/animales/{animalB}/indicadores-salud"));
        t.add(a("GET", "/api/v1/animales/{id}/linea-tiempo-alertas",
                "/api/v1/animales/{animalB}/linea-tiempo-alertas"));
        t.add(a("GET", "/api/v1/animales/{id}/cohesion", "/api/v1/animales/{animalB}/cohesion"));
        t.add(a("POST", "/api/v1/animales/{id}/vincular-caravana", "/api/v1/animales/{animalB}/vincular-caravana",
                "{\"caravanaId\":{caravanaB}}"));
        // Una caravana de B en un animal propio.
        t.add(a("POST", "/api/v1/animales/{id}/vincular-caravana", "/api/v1/animales/{animalA}/vincular-caravana",
                "{\"caravanaId\":{caravanaB}}"));
        t.add(a("DELETE", "/api/v1/animales/{id}/desvincular-caravana",
                "/api/v1/animales/{animalB}/desvincular-caravana"));
        t.add(a("POST", "/api/v1/animales/asignar-caravanas",
                "/api/v1/animales/asignar-caravanas?establecimientoId={estB}"));
        t.add(a("POST", "/api/v1/animales/asignaciones-caravanas/excel",
                "/api/v1/animales/asignaciones-caravanas/excel?establecimientoId={estB}", "[]"));
        t.add(a("POST", "/api/v1/animales/asignaciones-caravanas/excel",
                "/api/v1/animales/asignaciones-caravanas/excel?establecimientoId={estA}", "[{animalB}]"));

        // Caravanas
        t.add(a("GET", "/api/v1/caravana", "/api/v1/caravana?establecimientoId={estB}"));
        t.add(a("GET", "/api/v1/caravana/disponibles", "/api/v1/caravana/disponibles?establecimientoId={estB}"));

        // Perímetros
        t.add(a("POST", "/api/v1/perimetros", "/api/v1/perimetros", perimetro("estB", "")));
        t.add(a("POST", "/api/v1/perimetros", "/api/v1/perimetros", perimetro("estA", "{rodeoB}")));
        t.add(a("PUT", "/api/v1/perimetros/{id}", "/api/v1/perimetros/{perimetroB}", perimetro("estB", "")));
        t.add(a("GET", "/api/v1/perimetros/{id}", "/api/v1/perimetros/{perimetroB}"));
        t.add(a("GET", "/api/v1/perimetros", "/api/v1/perimetros?establecimientoId={estB}"));
        t.add(a("PATCH", "/api/v1/perimetros/{id}/activar", "/api/v1/perimetros/{perimetroB}/activar"));
        t.add(a("PATCH", "/api/v1/perimetros/{id}/desactivar", "/api/v1/perimetros/{perimetroB}/desactivar"));
        t.add(a("DELETE", "/api/v1/perimetros/{id}", "/api/v1/perimetros/{perimetroB}"));

        // Rodeos
        t.add(a("GET", "/api/v1/rodeo-lotes", "/api/v1/rodeo-lotes?establecimientoId={estB}"));
        t.add(a("POST", "/api/v1/rodeo-lotes", "/api/v1/rodeo-lotes",
                "{\"nombre\":\"Rodeo IT\",\"establecimientoId\":{estB}}"));
        t.add(a("DELETE", "/api/v1/rodeo-lotes/{id}", "/api/v1/rodeo-lotes/{rodeoB}"));
        t.add(a("GET", "/api/v1/rodeo-lotes/{id}", "/api/v1/rodeo-lotes/{rodeoB}"));

        // Mapa de calor
        t.add(a("GET", "/api/v1/mapa-calor", "/api/v1/mapa-calor?establecimientoId={estB}"));

        return t;
    }

    // ── Casos ────────────────────────────────────────────────────────────────

    /** Roles de establecimiento que pasan el @PreAuthorize del endpoint (todos, si es de cualquier autenticado). */
    private static List<String> rolesConPermiso(String patron, String metodo) {
        Endpoint endpoint = MatrizPermisosIT.matriz().stream()
                .filter(e -> e.metodo().equals(metodo) && e.patron().equals(patron))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No está en la matriz: " + metodo + " " + patron));
        return USUARIO_DE_A.keySet().stream()
                .filter(rol -> endpoint.roles().isEmpty() || endpoint.roles().contains(rol))
                .sorted()
                .toList();
    }

    static Stream<Arguments> accesosCruzados() {
        return accesos().stream()
                .flatMap(acceso -> rolesConPermiso(acceso.patron(), acceso.metodo()).stream()
                        .map(rol -> Arguments.of(rol, acceso.metodo(), acceso.url(), acceso)));
    }

    @ParameterizedTest(name = "H0035-03 [{0} de A] {1} {2}")
    @MethodSource("accesosCruzados")
    @DisplayName("H0035-03 - Acceso cruzado - 403 sin exponer datos de B")
    void H0035_03_accesoCruzadoDevuelve403(String rol, String metodo, String url, Acceso acceso) throws Exception {
        Map<String, Object> ids = prepararIds();
        String destino = resolver(url, ids);

        MockHttpServletRequestBuilder request;
        if (acceso.multipart()) {
            request = MockMvcRequestBuilders.multipart(HttpMethod.POST, destino)
                    .file(new MockMultipartFile("file", "datos.csv", "text/csv",
                            "idCaravanaOficial\n".getBytes(StandardCharsets.UTF_8)));
        } else {
            request = MockMvcRequestBuilders.request(HttpMethod.valueOf(metodo), destino);
            if (acceso.cuerpo() != null) {
                request.contentType(MediaType.APPLICATION_JSON).content(resolver(acceso.cuerpo(), ids));
            }
        }
        request.header("Authorization", bearer(USUARIO_DE_A.get(rol)));

        MvcResult resultado = mockMvc.perform(request).andReturn();

        String respuesta = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(resultado.getResponse().getStatus())
                .as("%s %s con %s de A; respuesta: %s", metodo, destino, rol, respuesta)
                .isEqualTo(403);
        for (String huella : HUELLAS_DE_B) {
            assertThat(respuesta).as("la respuesta expone datos de B").doesNotContain(huella);
        }
    }

    @Test
    @DisplayName("H0035-03 - Acceso cruzado - La tabla cubre todos los endpoints de establecimiento")
    void H0035_03_tablaCubreTodosLosEndpoints() {
        // Todo endpoint al que llega un rol de establecimiento (productor, veterinario o campero) tiene que estar en
        // la tabla, salvo los que no reciben datos de un establecimiento.
        Set<String> alcanzables = MatrizPermisosIT.matriz().stream()
                .filter(e -> e.roles().isEmpty() || e.roles().stream().anyMatch(USUARIO_DE_A::containsKey))
                .map(Endpoint::clave)
                .filter(clave -> !SIN_RECURSO_DE_ESTABLECIMIENTO.contains(clave))
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> cubiertos = accesos().stream().map(a -> a.metodo() + " " + a.patron())
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(cubiertos).as("endpoints sin acceso cruzado probado").containsAll(alcanzables);
    }

    // ── Datos de B ───────────────────────────────────────────────────────────

    private Map<String, Object> prepararIds() {
        Map<String, Object> ids = new HashMap<>();
        Long animalB = idsAnimales(EST_B).get(0);
        ids.put("estA", idEstablecimiento(EST_A));
        ids.put("estB", idEstablecimiento(EST_B));
        ids.put("animalA", idsAnimales(EST_A).get(0));
        ids.put("animalB", animalB);
        ids.put("alertaB", idsAlertas(EST_B).get(0));
        ids.put("alertaCeloB", jdbcTemplate.queryForObject("SELECT min(al.id) FROM alertas al JOIN animales a "
                + "ON a.id = al.animal_id JOIN establecimientos e ON e.id = a.establecimiento_id "
                + "WHERE e.nombre = ? AND al.disparador = 'CELO'", Long.class, EST_B));
        ids.put("rodeoB", idRodeo(EST_B));
        ids.put("perimetroB", idPerimetro(EST_B));
        ids.put("caravanaB", idCaravanaLibre(EST_B));
        ids.put("camperoB", idUsuario(email("camperoB")));
        ids.put("estadoB", jdbcTemplate.queryForObject(
                "SELECT min(id) FROM estados_reproductivos WHERE animal_id = ?", Long.class, animalB));
        // El escenario no trae pesadas: se agrega una en un animal de B.
        jdbcTemplate.update("INSERT INTO registros_peso (animal_id, fecha, valor_kg, unidad, fecha_registro) "
                + "VALUES (?, current_date, 380, 'kg', ?)", animalB, Timestamp.valueOf(LocalDateTime.now()));
        ids.put("pesoB", jdbcTemplate.queryForObject(
                "SELECT max(id) FROM registros_peso WHERE animal_id = ?", Long.class, animalB));
        // Una invitación dirigida a productorB y otra creada por productorB: ninguna es de un usuario de A.
        String tokenAjeno = crearInvitacion("PRODUCTOR", "PRODUCTOR", email("productorB"), EST_A, "PENDIENTE",
                LocalDateTime.now().plusDays(7));
        ids.put("tokenAjeno", tokenAjeno);
        ids.put("invitacionAjena", idInvitacion(tokenAjeno));
        String tokenDeB = crearInvitacion("EMPLEADO", "CAMPERO", emailUnico(), EST_B, "PENDIENTE",
                LocalDateTime.now().plusDays(7));
        jdbcTemplate.update("UPDATE invitaciones SET creador_id = ? WHERE token = ?",
                idUsuario(email("productorB")), tokenDeB);
        ids.put("invitacionDeB", idInvitacion(tokenDeB));
        return ids;
    }

    private static String resolver(String plantilla, Map<String, Object> ids) {
        String resultado = plantilla;
        for (Map.Entry<String, Object> id : ids.entrySet()) {
            resultado = resultado.replace("{" + id.getKey() + "}", String.valueOf(id.getValue()));
        }
        return resultado;
    }
}
