package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Trabajo con varios establecimientos y aislamiento entre ellos. Es la evidencia de RNF03: un usuario solo ve los
 * datos del establecimiento al que pertenece. Los accesos cruzados por id o por {@code establecimientoId} (H0035-03)
 * están en {@link AccesoCruzadoIT}.
 * <p>
 * VigIA no guarda un "establecimiento activo" en el servidor: el frontend manda el establecimiento elegido en cada
 * request. Cambiar de contexto es, entonces, pedir con otro {@code establecimientoId}.
 */
@DisplayName("H0035 - Trabajar con varios establecimientos (cambio de contexto y aislamiento) — IT")
class MultiEstablecimientoIT extends SeguridadItBase {

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
}
