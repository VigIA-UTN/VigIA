package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Trabajo con varios establecimientos y aislamiento entre ellos. Es la evidencia de RNF03: un usuario solo ve los
 * datos del establecimiento al que pertenece, aunque pida por id o por {@code establecimientoId} recursos de otro.
 * <p>
 * VigIA no guarda un "establecimiento activo" en el servidor: el frontend manda el establecimiento elegido en cada
 * request. Cambiar de contexto es, entonces, pedir con otro {@code establecimientoId}.
 */
@DisplayName("H0035 - Trabajar con varios establecimientos (cambio de contexto y aislamiento) — IT")
class MultiEstablecimientoIT extends SeguridadItBase {

    // Textos que identifican datos de Fixture B y no pueden aparecer en una respuesta denegada: el nombre del
    // establecimiento, los ids oficiales de sus animales (B-101...) y los códigos de sus caravanas (FX-B-01...).
    private static final List<String> HUELLAS_DE_B = List.of(EST_B, "B-10", "FX-B-");

    @Test
    @DisplayName("H0035-01 - Cambio de establecimiento - Productor de A y B ve los datos de cada uno")
    void H0035_01_cambioDeEstablecimiento() throws Exception {
        for (String establecimiento : List.of(EST_A, EST_B)) {
            Long id = idEstablecimiento(establecimiento);

            assertThat(ids("productorAmbos", "/api/v1/animales", id))
                    .as("animales de %s", establecimiento)
                    .containsExactlyInAnyOrderElementsOf(idsAnimales(establecimiento));
            assertThat(ids("productorAmbos", "/api/v1/alertas", id))
                    .as("alertas de %s", establecimiento)
                    .isNotEmpty()
                    .isSubsetOf(idsAlertas(establecimiento));
        }
    }

    @Test
    @DisplayName("H0035-02 - Cambio de establecimiento - Los datos de A y B no se mezclan")
    void H0035_02_datosNoSeMezclan() throws Exception {
        Long idA = idEstablecimiento(EST_A);
        Long idB = idEstablecimiento(EST_B);

        for (String recurso : List.of("/api/v1/animales", "/api/v1/alertas", "/api/v1/rodeo-lotes",
                "/api/v1/perimetros", "/api/v1/caravana")) {
            Set<Long> deA = ids("productorAmbos", recurso, idA);
            Set<Long> deB = ids("productorAmbos", recurso, idB);

            assertThat(deA).as("%s de A", recurso).isNotEmpty();
            assertThat(deB).as("%s de B", recurso).isNotEmpty();
            assertThat(deA).as("%s: A y B comparten ids", recurso).doesNotContainAnyElementsOf(deB);
        }
    }

    /**
     * Cada request lo hace productorA (solo pertenece a Fixture A) contra un recurso de Fixture B. Las rutas llevan
     * marcadores que se resuelven con los ids de la carga actual del escenario.
     */
    static Stream<Arguments> recursosDeOtroEstablecimiento() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/animales?establecimientoId={estB}", null),
                Arguments.of("GET", "/api/v1/animales/mapa?establecimientoId={estB}", null),
                Arguments.of("GET", "/api/v1/animales/{animalB}", null),
                Arguments.of("GET", "/api/v1/animales/{animalB}/estado-reproductivo", null),
                Arguments.of("GET", "/api/v1/animales/{animalB}/intervenciones", null),
                Arguments.of("GET", "/api/v1/animales/{animalB}/estado-salud", null),
                Arguments.of("GET", "/api/v1/animales/{animalB}/historial-pesos", null),
                Arguments.of("GET", "/api/v1/animales/{animalB}/indicadores-salud", null),
                Arguments.of("GET", "/api/v1/animales/{animalB}/linea-tiempo-alertas", null),
                Arguments.of("GET", "/api/v1/animales/{animalB}/metricas", null),
                Arguments.of("PUT", "/api/v1/animales/{animalB}",
                        "{\"idCaravanaOficial\":\"B-999\",\"fechaNacimiento\":\"2021-01-01\","
                                + "\"rodeoLoteId\":{rodeoB},\"establecimientoId\":{estB}}"),
                Arguments.of("POST", "/api/v1/animales/{animalB}/historial-pesos",
                        "{\"valorKg\":400,\"unidad\":\"KG\",\"fecha\":\"2026-01-01\"}"),
                Arguments.of("GET", "/api/v1/alertas?establecimientoId={estB}", null),
                Arguments.of("GET", "/api/v1/alertas/resumen?establecimientoId={estB}", null),
                Arguments.of("GET", "/api/v1/alertas/celo?establecimientoId={estB}", null),
                Arguments.of("GET", "/api/v1/alertas/salud?establecimientoId={estB}", null),
                Arguments.of("GET", "/api/v1/alertas/{alertaB}?establecimientoId={estB}", null),
                Arguments.of("GET", "/api/v1/alertas/{alertaB}?establecimientoId={estA}", null),
                Arguments.of("GET", "/api/v1/alertas/diagnosticos?establecimientoId={estB}", null),
                Arguments.of("PUT", "/api/v1/alertas/estado/{alertaB}?estado=DESCARTADA&establecimientoId={estA}",
                        null),
                Arguments.of("GET", "/api/v1/rodeo-lotes?establecimientoId={estB}", null),
                Arguments.of("GET", "/api/v1/rodeo-lotes/{rodeoB}", null),
                Arguments.of("GET", "/api/v1/perimetros?establecimientoId={estB}", null),
                Arguments.of("GET", "/api/v1/perimetros/{perimetroB}", null),
                Arguments.of("GET", "/api/v1/caravana?establecimientoId={estB}", null),
                Arguments.of("GET", "/api/v1/caravana/disponibles?establecimientoId={estB}", null),
                Arguments.of("GET", "/api/v1/establecimientos/{estB}/intervenciones", null),
                Arguments.of("GET", "/api/v1/establecimientos/{estB}/pesos", null),
                Arguments.of("GET", "/api/v1/establecimientos/{estB}/anestro", null),
                Arguments.of("GET", "/api/v1/establecimientos/{estB}/aislamiento", null),
                Arguments.of("PUT", "/api/v1/establecimientos/{estB}",
                        "{\"nombre\":\"Fixture B\",\"latitud\":-36.6,\"longitud\":-61.6}"),
                Arguments.of("GET", "/api/v1/usuarios/empleados?establecimientoId={estB}", null),
                Arguments.of("DELETE", "/api/v1/usuarios/empleados/{estB}/{camperoB}", null),
                Arguments.of("GET", "/api/v1/invitaciones/empleado?establecimientoId={estB}", null),
                Arguments.of("GET", "/api/v1/mapa-calor?establecimientoId={estB}", null));
    }

    @ParameterizedTest(name = "H0035-03 - Recurso de otro establecimiento - {0} {1}")
    @MethodSource("recursosDeOtroEstablecimiento")
    @DisplayName("H0035-03 - Recurso de otro establecimiento - 403/404 sin exponer datos")
    void H0035_03_recursoDeOtroEstablecimiento(String metodo, String ruta, String cuerpo) throws Exception {
        Map<String, Object> ids = idsDelEscenario();
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders
                .request(HttpMethod.valueOf(metodo), resolver(ruta, ids))
                .header("Authorization", bearer("productorA"));
        if (cuerpo != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(resolver(cuerpo, ids));
        }

        MvcResult resultado = mockMvc.perform(request).andReturn();

        String respuesta = resultado.getResponse().getContentAsString();
        assertThat(resultado.getResponse().getStatus()).as("status; respuesta: %s", respuesta).isIn(403, 404);
        for (String huella : HUELLAS_DE_B) {
            assertThat(respuesta).as("la respuesta expone datos de B").doesNotContain(huella);
        }
    }

    @Test
    @DisplayName("H0035-04 - Aislamiento - Dos usuarios de establecimientos distintos en simultáneo (RNF03)")
    void H0035_04_usuariosSimultaneos() throws Exception {
        Map<String, String> tokens = Map.of("productorA", bearer("productorA"), "productorB", bearer("productorB"));
        Map<String, Long> establecimientos = Map.of("productorA", idEstablecimiento(EST_A),
                "productorB", idEstablecimiento(EST_B));
        Map<String, Set<Long>> propios = Map.of("productorA", new HashSet<>(idsAnimales(EST_A)),
                "productorB", new HashSet<>(idsAnimales(EST_B)));

        int pedidosPorUsuario = 25;
        List<String> problemas = new CopyOnWriteArrayList<>();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> tareas = new ArrayList<>();
            for (int i = 0; i < pedidosPorUsuario; i++) {
                for (String usuario : List.of("productorA", "productorB")) {
                    Callable<Void> tarea = () -> {
                        // Todos salen juntos para que los requests de los dos usuarios se intercalen.
                        largada.await();
                        MvcResult resultado = mockMvc.perform(get("/api/v1/animales")
                                        .param("establecimientoId", establecimientos.get(usuario).toString())
                                        .header("Authorization", tokens.get(usuario)))
                                .andReturn();
                        if (resultado.getResponse().getStatus() != 200) {
                            problemas.add(usuario + ": status " + resultado.getResponse().getStatus());
                            return null;
                        }
                        Set<Long> vistos = new HashSet<>();
                        leer(resultado).forEach(animal -> vistos.add(animal.get("id").asLong()));
                        if (!vistos.equals(propios.get(usuario))) {
                            problemas.add(usuario + " vio " + vistos + " y le corresponden " + propios.get(usuario));
                        }
                        return null;
                    };
                    tareas.add(pool.submit(tarea));
                }
            }
            largada.countDown();
            for (Future<?> tarea : tareas) {
                tarea.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(problemas).isEmpty();
    }

    @Test
    @DisplayName("H0035-05 - Aislamiento - Stream del mapa de otro establecimiento")
    void H0035_05_streamDelMapaDeOtroEstablecimiento() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/animales/mapa/stream")
                        .param("establecimientoId", idEstablecimiento(EST_B).toString())
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", bearer("productorA")))
                .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
    }

    private Set<Long> ids(String clave, String recurso, Long establecimientoId) throws Exception {
        MvcResult resultado = mockMvc.perform(get(recurso)
                        .param("establecimientoId", establecimientoId.toString())
                        .header("Authorization", bearer(clave)))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).as("GET %s", recurso).isEqualTo(200);
        Set<Long> ids = new HashSet<>();
        JsonNode cuerpo = leer(resultado);
        cuerpo.forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    private Map<String, Object> idsDelEscenario() {
        Map<String, Object> ids = new HashMap<>();
        ids.put("estA", idEstablecimiento(EST_A));
        ids.put("estB", idEstablecimiento(EST_B));
        ids.put("animalB", idsAnimales(EST_B).get(0));
        ids.put("alertaB", idsAlertas(EST_B).get(0));
        ids.put("rodeoB", idRodeo(EST_B));
        ids.put("perimetroB", idPerimetro(EST_B));
        ids.put("camperoB", idUsuario(email("camperoB")));
        return ids;
    }

    private static String resolver(String plantilla, Map<String, Object> ids) {
        String resultado = plantilla;
        for (Map.Entry<String, Object> id : ids.entrySet()) {
            resultado = resultado.replace("{" + id.getKey() + "}", id.getValue().toString());
        }
        return resultado;
    }
}
