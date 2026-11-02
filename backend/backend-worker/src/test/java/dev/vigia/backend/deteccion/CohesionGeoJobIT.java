package dev.vigia.backend.deteccion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Job geoespacial contra ml-geo simulado. En Fixture A, el Rodeo A1 tiene a A-101, 102, 104, 105 y 106 y el Rodeo A2
 * solo a A-103; A-104 ya tiene una alerta de aislamiento pendiente con su episodio abierto (a ~280 m del rodeo).
 * ml-geo contesta por cada animal que recibe: por defecto, dentro del grupo; cada caso carga los que se alejan.
 */
@DisplayName("H0026 - Índice de cohesión social / alerta de aislamiento (job geo) — IT")
class CohesionGeoJobIT extends DeteccionItBase {

    @Autowired
    @Qualifier("geoJob")
    private Job geoJob;

    /** Por animal, lo que contesta ml-geo; los que no están, dentro del grupo. */
    private final Map<Long, Map<String, Object>> resultados = new HashMap<>();

    private Long a101;
    private Long a103;
    private Long a104;

    @BeforeEach
    void simularMlGeo() {
        a101 = idAnimal(EST_A, "A-101");
        a103 = idAnimal(EST_A, "A-103");
        a104 = idAnimal(EST_A, "A-104");
        resultados.clear();
        ML.responder(MlSimulado.GEO_ANALYZE, this::analizar);
        // Telemetría nueva en Fixture A; Fixture B no tiene nada pendiente y no se analiza.
        marcarUltimaLecturaPendiente(a101, "geo");
    }

    @Test
    @DisplayName("H0026-01 - Cohesión - Se guarda el índice de cohesión que calcula ml-geo")
    void H0026_01_indicePersistido() throws Exception {
        resultados.put(a101, resultado(0.92, 15.0, false, 0, 0));
        resultados.put(a103, resultado(0.75, 40.0, false, 0, 0));
        resultados.put(a104, resultado(0.20, 280.0, true, 3 * 3600, 3 * 3600));

        correr(geoJob);

        // Una consulta por rodeo, cada una con sus animales y el umbral del establecimiento (100 m por defecto).
        List<String> pedidos = ML.recibidos(MlSimulado.GEO_ANALYZE);
        assertThat(pedidos).hasSize(2);
        List<Set<String>> grupos = new ArrayList<>();
        for (String pedido : pedidos) {
            JsonNode cuerpo = json(pedido);
            assertThat(cuerpo.get("umbral_aislamiento_m").asDouble()).isEqualTo(100.0);
            grupos.add(animalesDe(cuerpo));
        }
        assertThat(grupos).containsExactlyInAnyOrder(
                Set.of(a101.toString(), idAnimal(EST_A, "A-102").toString(), a104.toString(),
                        idAnimal(EST_A, "A-105").toString(), idAnimal(EST_A, "A-106").toString()),
                Set.of(a103.toString()));

        // Un registro por animal de A, con lo que devolvió ml-geo.
        assertThat(cantidadRegistrosCohesion(EST_A)).isEqualTo(6);
        assertThat(cantidadRegistrosCohesion(EST_B)).isZero();
        Map<String, Object> registro = jdbcTemplate.queryForMap(
                "SELECT * FROM registros_cohesion WHERE animal_id = ?", a101);
        assertThat(((Number) registro.get("indice_cohesion")).doubleValue()).isCloseTo(0.92, within(1e-9));
        assertThat(((Number) registro.get("distancia_centroide_m")).doubleValue()).isCloseTo(15.0, within(1e-9));
        assertThat(jdbcTemplate.queryForObject("SELECT indice_cohesion FROM registros_cohesion WHERE animal_id = ?",
                Double.class, a103)).isCloseTo(0.75, within(1e-9));

        assertThat(lecturasSinEvaluar(a101, "geo")).isZero();
    }

    @Test
    @DisplayName("H0026-03 - Aislamiento - Alerta nueva por animal alejado del rodeo")
    void H0026_03_alertaNuevaPorAnimalAlejado() throws Exception {
        jdbcTemplate.update("UPDATE establecimientos SET umbral_aislamientom = 150 WHERE nombre = ?", EST_A);
        // A-103 lleva 20 min seguidos a 250 m de su rodeo (A2, sin alerta): abre una alerta nueva.
        resultados.put(a103, resultado(0.10, 250.0, true, 1500, 1200));
        // A-101 se alejó hace 5 min: menos de los 15 min que filtran el GPS errático, no se suma a la alerta de A1.
        resultados.put(a101, resultado(0.40, 180.0, true, 300, 300));
        resultados.put(a104, resultado(0.20, 280.0, true, 3 * 3600, 3 * 3600));

        correr(geoJob);

        // El umbral configurado en el establecimiento viaja en cada consulta.
        for (String pedido : ML.recibidos(MlSimulado.GEO_ANALYZE)) {
            assertThat(json(pedido).get("umbral_aislamiento_m").asDouble()).isEqualTo(150.0);
        }
        List<Map<String, Object>> alertas = alertas(a103, "AISLAMIENTO_SOCIAL");
        assertThat(alertas).hasSize(1);
        Map<String, Object> alerta = alertas.get(0);
        assertThat(alerta.get("estado")).isEqualTo("PENDIENTE");
        // 250 m no llega al doble del umbral (300 m): MEDIA.
        assertThat(alerta.get("severidad")).isEqualTo("MEDIA");
        assertThat(((Number) alerta.get("valor_umbral")).doubleValue()).isEqualTo(150.0);
        assertThat(((Number) alerta.get("valor_detectado")).doubleValue()).isEqualTo(250.0);
        assertThat(alerta.get("latitud")).isNotNull();
        assertThat(alerta.get("longitud")).isNotNull();

        Map<String, Object> episodio = jdbcTemplate.queryForMap(
                "SELECT * FROM alerta_animales_aislados WHERE alerta_id = ?", alerta.get("id"));
        assertThat(((Number) episodio.get("animal_id")).longValue()).isEqualTo(a103);
        assertThat(((Number) episodio.get("segundos_fuera")).longValue()).isEqualTo(1500);
        assertThat(((Number) episodio.get("distancia_grupo_m")).doubleValue()).isEqualTo(250.0);
        assertThat(episodio.get("ts_regreso")).isNull();

        // La alerta del Rodeo A1 sigue listando solo a A-104.
        assertThat(jdbcTemplate.queryForList("SELECT aa.animal_id FROM alerta_animales_aislados aa JOIN alertas al "
                + "ON al.id = aa.alerta_id WHERE al.animal_id = ?", Long.class, a104)).containsExactly(a104);
        assertThat(alertas(a101, "AISLAMIENTO_SOCIAL")).isEmpty();
    }

    @Test
    @DisplayName("H0026-04 - Aislamiento - Sin alerta duplicada mientras sigue alejado y cierre al regresar")
    void H0026_04_sinDuplicarYRegreso() throws Exception {
        resultados.put(a104, resultado(0.15, 300.0, true, 4 * 3600, 4 * 3600));

        correr(geoJob);

        // Mismo aislamiento: se actualiza el episodio abierto, sin alerta nueva.
        assertThat(cantidadAlertasAislamiento()).isEqualTo(1);
        Map<String, Object> episodio = episodioDe(a104);
        assertThat(((Number) episodio.get("segundos_fuera")).longValue()).isEqualTo(4 * 3600);
        assertThat(((Number) episodio.get("distancia_grupo_m")).doubleValue()).isEqualTo(300.0);
        assertThat(episodio.get("ts_regreso")).isNull();

        // Sin telemetría nueva no se vuelve a consultar a ml-geo ni se guarda otro índice.
        int consultas = ML.llamadas(MlSimulado.GEO_ANALYZE);
        int registros = cantidadRegistrosCohesion(EST_A);
        correr(geoJob);
        assertThat(ML.llamadas(MlSimulado.GEO_ANALYZE)).isEqualTo(consultas);
        assertThat(cantidadRegistrosCohesion(EST_A)).isEqualTo(registros);

        // Llega una lectura nueva con A-104 de vuelta en el grupo: se cierra el episodio.
        resultados.remove(a104);
        insertarLecturaNueva(a104);
        correr(geoJob);

        episodio = episodioDe(a104);
        assertThat(episodio.get("ts_regreso")).isNotNull();
        assertThat(episodio.get("motivo_cierre")).isEqualTo("REGRESO");
        // La alerta no se resuelve sola: la sigue revisando el productor.
        assertThat(alertas(a104, "AISLAMIENTO_SOCIAL").get(0).get("estado")).isEqualTo("PENDIENTE");
        assertThat(cantidadAlertasAislamiento()).isEqualTo(1);
    }

    // ── ml-geo simulado ─────────────────────────────────────────────────────

    private String analizar(String cuerpo) {
        try {
            List<Map<String, Object>> alertas = new ArrayList<>();
            for (String animalId : animalesDe(json(cuerpo))) {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("animal_id", animalId);
                fila.putAll(resultados.getOrDefault(Long.valueOf(animalId), resultado(0.9, 20.0, false, 0, 0)));
                alertas.add(fila);
            }
            return objectMapper.writeValueAsString(
                    Map.of("mapa_calor", List.of(), "alertas", alertas, "animales_alejados", List.of()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> resultado(double indice, double distancia, boolean alejado,
            double fueraTotalS, double fueraContinuoS) {
        return Map.of("indice_cohesion", indice, "distancia_centroide_m", distancia, "alerta_aislamiento", alejado,
                "tiempo_fuera_grupo_s", fueraTotalS, "tiempo_fuera_continuo_s", fueraContinuoS);
    }

    private static Set<String> animalesDe(JsonNode pedido) {
        Set<String> ids = new LinkedHashSet<>();
        pedido.get("historial_7d").forEach(punto -> ids.add(punto.get("animal_id").asText()));
        return ids;
    }

    // ── Consultas ───────────────────────────────────────────────────────────

    private int cantidadRegistrosCohesion(String establecimiento) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM registros_cohesion r JOIN establecimientos e "
                + "ON e.id = r.establecimiento_id WHERE e.nombre = ?", Integer.class, establecimiento);
    }

    private int cantidadAlertasAislamiento() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM alertas al JOIN animales a ON a.id = al.animal_id "
                + "JOIN establecimientos e ON e.id = a.establecimiento_id "
                + "WHERE e.nombre = ? AND al.disparador = 'AISLAMIENTO_SOCIAL'", Integer.class, EST_A);
    }

    private Map<String, Object> episodioDe(Long animalId) {
        return jdbcTemplate.queryForMap("SELECT * FROM alerta_animales_aislados WHERE animal_id = ?", animalId);
    }
}
