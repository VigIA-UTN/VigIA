package dev.vigia.backend.deteccion;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * INT02 - Contrato entre el backend-worker y los microservicios ml-celo, ml-salud y ml-geo, del lado de los jobs: la
 * respuesta de cada microservicio se mapea y persiste, y cuando un microservicio no está disponible la telemetría queda
 * pendiente sin perder el dato crudo y se reprocesa al recuperarse. Corre sobre TimescaleDB y RabbitMQ reales
 * (Testcontainers), con los microservicios simulados por {@link MlSimulado}; los timeouts y el reintento de cada
 * cliente los prueba {@code dev.vigia.backend.client.ContratoMlClientesTest}.
 * <p>
 * Cada test parte del escenario de {@code fixtures/escenario}: A-101 (Fixture A, Rodeo A1) tiene 8 días de telemetría a
 * 5 min y ninguna alerta; A-103 está solo en el Rodeo A2. El circuit breaker vive en el contexto de Spring, que comparten
 * todos los IT: cada caso lo deja cerrado al terminar.
 * <p>
 * Los nombres de los tests llevan el caso de la hoja "Ciclo 1" del documento de Casos de Prueba (INT02-01 a INT02-03 e
 * INT02-12 a INT02-14).
 */
@DisplayName("INT02 - Pipeline del worker con los microservicios ML (persistencia, circuito abierto y reproceso) — IT")
class PipelineMlIT extends SaludItBase {

    private static final String CELO_POSITIVO =
            "{\"evento_celo\": true, \"confianza_modelo\": 0.91, \"ventana_optima_horas\": 18}";

    @Autowired
    @Qualifier("celoJob")
    private Job celoJob;

    @Autowired
    @Qualifier("geoJob")
    private Job geoJob;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private Long a101;

    @BeforeEach
    void prepararPipeline() {
        cerrarCircuitos();
        a101 = idAnimal(EST_A, "A-101");
    }

    @AfterEach
    void cerrarCircuitos() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    // ── Respuesta correcta: se mapea y persiste ──────────────────────────────

    @Test
    @DisplayName("INT02-01 - ml-celo responde - La predicción se guarda como alerta de celo y estado reproductivo")
    void INT02_01_respuestaDeCeloSePersiste() throws Exception {
        ML.responder(MlSimulado.CELO_PREDICT, CELO_POSITIVO);
        marcarUltimaLecturaPendiente(a101, "celo");

        correr(celoJob);

        assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isEqualTo(1);
        List<Map<String, Object>> alertas = alertas(a101, "CELO");
        assertThat(alertas).hasSize(1);
        Map<String, Object> alerta = alertas.get(0);
        assertThat(((Number) alerta.get("confianza_modelo")).doubleValue()).isCloseTo(0.91, within(0.001));
        assertThat(alerta.get("ventana_optima_horas")).isEqualTo(18);
        assertThat(alerta.get("estado")).isEqualTo("PENDIENTE");
        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM estados_reproductivos WHERE animal_id = ? "
                + "ORDER BY fecha_registro DESC, id DESC LIMIT 1", String.class, a101)).isEqualTo("POSIBLE_CELO");
        assertThat(estadoUltimaLectura(a101, "celo")).isEqualTo("COMPLETADO");
    }

    @Test
    @DisplayName("INT02-02 - ml-salud responde - El desvío se guarda como alerta sanitaria con severidad y causa")
    void INT02_02_respuestaDeSaludSePersiste() throws Exception {
        cargarPerfil(a101);
        ML.responder(MlSimulado.SALUD_PREDICT, desvio(2, "Inapetencia", 0.8));
        marcarUltimaLecturaPendiente(a101, "salud");

        correr(saludJob);

        assertThat(ML.llamadas(MlSimulado.SALUD_PREDICT)).isEqualTo(1);
        List<Map<String, Object>> alertas = alertas(a101, "SALUD");
        assertThat(alertas).hasSize(1);
        Map<String, Object> alerta = alertas.get(0);
        assertThat(alerta.get("severidad")).isEqualTo("MEDIA");
        assertThat(alerta.get("posible_causa")).isEqualTo("Inapetencia");
        assertThat(((Number) alerta.get("confianza_modelo")).doubleValue()).isCloseTo(0.8, within(0.001));
        assertThat(estadoUltimaLectura(a101, "salud")).isEqualTo("COMPLETADO");
    }

    @Test
    @DisplayName("INT02-03 - ml-geo responde - El índice de cohesión y el aislamiento se guardan")
    void INT02_03_respuestaDeGeoSePersiste() throws Exception {
        Long a103 = idAnimal(EST_A, "A-103");
        // A-103 lleva 20 min seguidos a 250 m de su rodeo; los demás, dentro del grupo.
        ML.responder(MlSimulado.GEO_ANALYZE, cuerpo -> respuestaGeo(cuerpo, a103));
        marcarUltimaLecturaPendiente(a101, "geo");

        correr(geoJob);

        assertThat(jdbcTemplate.queryForObject("SELECT indice_cohesion FROM registros_cohesion WHERE animal_id = ?",
                Double.class, a101)).isCloseTo(0.9, within(1e-9));
        assertThat(jdbcTemplate.queryForObject("SELECT indice_cohesion FROM registros_cohesion WHERE animal_id = ?",
                Double.class, a103)).isCloseTo(0.1, within(1e-9));
        List<Map<String, Object>> alertas = alertas(a103, "AISLAMIENTO_SOCIAL");
        assertThat(alertas).hasSize(1);
        assertThat(((Number) alertas.get(0).get("valor_detectado")).doubleValue()).isEqualTo(250.0);
        assertThat(estadoUltimaLectura(a101, "geo")).isEqualTo("COMPLETADO");
    }

    // ── Circuito abierto, reproceso y rechazo ────────────────────────────────

    @Test
    @DisplayName("INT02-12 - Circuito abierto - La telemetría queda PENDIENTE sin pérdida del dato crudo")
    void INT02_12_circuitoAbiertoDejaPendiente() throws Exception {
        ML.responder(MlSimulado.CELO_PREDICT, CELO_POSITIVO);
        marcarUltimaLecturaPendiente(a101, "celo");
        Map<String, Object> crudoAntes = ultimaLecturaCruda(a101);
        int lecturasAntes = cantidadLecturas(a101);
        circuitBreakerRegistry.circuitBreaker("celoML").transitionToOpenState();

        correr(celoJob);

        // Con el circuito abierto no se llama a ml-celo, el job termina bien y no se genera nada.
        assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isZero();
        assertThat(alertas(a101, "CELO")).isEmpty();
        assertThat(estadoUltimaLectura(a101, "celo")).isEqualTo("PENDIENTE");
        // El dato crudo sigue intacto: ninguna lectura borrada ni modificada.
        assertThat(cantidadLecturas(a101)).isEqualTo(lecturasAntes);
        assertThat(ultimaLecturaCruda(a101)).isEqualTo(crudoAntes);
    }

    @Test
    @DisplayName("INT02-13 - Recuperación del servicio - La telemetría pendiente se reprocesa y genera la alerta")
    void INT02_13_alRecuperarseSeReprocesa() throws Exception {
        ML.responder(MlSimulado.CELO_PREDICT, CELO_POSITIVO);
        marcarUltimaLecturaPendiente(a101, "celo");
        CircuitBreaker circuito = circuitBreakerRegistry.circuitBreaker("celoML");
        circuito.transitionToOpenState();
        correr(celoJob);
        assertThat(estadoUltimaLectura(a101, "celo")).isEqualTo("PENDIENTE");

        // Pasado el tiempo de espera del circuito abierto, deja pasar llamadas de prueba (semiabierto).
        circuito.transitionToHalfOpenState();
        correr(celoJob);

        // El ciclo siguiente toma la misma lectura pendiente, sin que llegue telemetría nueva.
        assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isEqualTo(1);
        assertThat(alertas(a101, "CELO")).hasSize(1);
        assertThat(estadoUltimaLectura(a101, "celo")).isEqualTo("COMPLETADO");
        assertThat(circuito.getState()).isNotEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("INT02-14 - El microservicio rechaza los datos (4xx) - La lectura pasa a ERROR y no se reintenta")
    void INT02_14_rechazoDejaErrorSinReintentar() throws Exception {
        ML.responder(MlSimulado.CELO_PREDICT, 422,
                "{\"error\": \"Payload Invalido\", \"mensaje\": \"serie: debe tener exactamente 288 puntos\"}", 0);
        marcarUltimaLecturaPendiente(a101, "celo");

        correr(celoJob);

        assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isEqualTo(1);
        assertThat(alertas(a101, "CELO")).isEmpty();
        assertThat(estadoUltimaLectura(a101, "celo")).isEqualTo("ERROR");
        // Un rechazo no es una caída: no cuenta para abrir el circuito.
        assertThat(circuitBreakerRegistry.circuitBreaker("celoML").getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // El ciclo siguiente no vuelve a mandar la misma ventana.
        correr(celoJob);
        assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isEqualTo(1);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private String estadoUltimaLectura(Long animalId, String pipeline) {
        return jdbcTemplate.queryForObject("SELECT estado_inferencia_" + pipeline + " FROM telemetria "
                + "WHERE caravana_id = ? ORDER BY fecha_hora DESC LIMIT 1", String.class, idCaravana(animalId));
    }

    private Map<String, Object> ultimaLecturaCruda(Long animalId) {
        return jdbcTemplate.queryForMap("SELECT id, fecha_hora, latitud, longitud, move_state, bateria FROM telemetria "
                + "WHERE caravana_id = ? ORDER BY fecha_hora DESC LIMIT 1", idCaravana(animalId));
    }

    private int cantidadLecturas(Long animalId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM telemetria WHERE caravana_id = ?", Integer.class,
                idCaravana(animalId));
    }

    /** ml-geo contesta por cada animal del pedido: el aislado lejos del grupo, los demás dentro. */
    private String respuestaGeo(String cuerpo, Long aislado) {
        try {
            List<Map<String, Object>> filas = new ArrayList<>();
            List<String> vistos = new ArrayList<>();
            for (JsonNode punto : json(cuerpo).get("historial_7d")) {
                String animalId = punto.get("animal_id").asText();
                if (vistos.contains(animalId)) {
                    continue;
                }
                vistos.add(animalId);
                boolean alejado = animalId.equals(aislado.toString());
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("animal_id", animalId);
                fila.put("indice_cohesion", alejado ? 0.1 : 0.9);
                fila.put("distancia_centroide_m", alejado ? 250.0 : 20.0);
                fila.put("alerta_aislamiento", alejado);
                fila.put("tiempo_fuera_grupo_s", alejado ? 1500.0 : 0.0);
                fila.put("tiempo_fuera_continuo_s", alejado ? 1200.0 : 0.0);
                filas.add(fila);
            }
            return objectMapper.writeValueAsString(
                    Map.of("mapa_calor", List.of(), "alertas", filas, "animales_alejados", List.of()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
