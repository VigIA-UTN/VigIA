package dev.vigia.backend.deteccion;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.Job;
import org.springframework.core.ParameterizedTypeReference;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Job de celo contra ml-celo simulado. A-101 tiene 8 días de telemetría a 5 min (una ventana de 24 h completa) y
 * ninguna alerta; A-102 ya tiene una alerta de celo de hace 1 h.
 */
@DisplayName("H0008 - Alerta de celo (job de celo) — IT")
class AlertaCeloJobIT extends DeteccionItBase {

    private static final String CELO_POSITIVO =
            "{\"evento_celo\": true, \"confianza_modelo\": 0.91, \"ventana_optima_horas\": 18}";
    private static final String CELO_NEGATIVO =
            "{\"evento_celo\": false, \"confianza_modelo\": 0.12, \"ventana_optima_horas\": 0}";

    @Autowired
    @Qualifier("celoJob")
    private Job celoJob;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    @Qualifier("alertasExchange")
    private FanoutExchange alertasExchange;

    @Test
    @DisplayName("H0008-01 - Alerta de celo - Predicción positiva genera la alerta")
    void H0008_01_prediccionPositivaGeneraAlerta() throws Exception {
        ML.responder(MlSimulado.CELO_PREDICT, CELO_POSITIVO);
        Long animal = idAnimal(EST_A, "A-101");
        marcarUltimaLecturaPendiente(animal, "celo");

        // Como la cola de cada réplica de la API: el evento sale por el exchange de alertas al commitear.
        Queue cola = new AnonymousQueue();
        amqpAdmin.declareQueue(cola);
        amqpAdmin.declareBinding(BindingBuilder.bind(cola).to(alertasExchange));
        try {
            correr(celoJob);

            // ml-celo recibe la ventana de 24 h a 5 min que exige el modelo.
            assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isEqualTo(1);
            JsonNode serie = json(ML.recibidos(MlSimulado.CELO_PREDICT).get(0));
            assertThat(serie).hasSize(288);
            assertThat(serie.get(0).has("moveState")).isTrue();
            assertThat(serie.get(0).get("latitud").isNumber()).isTrue();
            assertThat(serie.get(0).get("longitud").isNumber()).isTrue();

            List<Map<String, Object>> alertas = alertas(animal, "CELO");
            assertThat(alertas).hasSize(1);
            Map<String, Object> alerta = alertas.get(0);
            assertThat(alerta.get("tipo")).isEqualTo("Celo Detectado");
            assertThat(alerta.get("severidad")).isEqualTo("ALTA");
            assertThat(alerta.get("estado")).isEqualTo("PENDIENTE");
            assertThat(((Number) alerta.get("confianza_modelo")).doubleValue()).isCloseTo(0.91, within(0.001));
            assertThat(((Number) alerta.get("valor_detectado")).doubleValue()).isCloseTo(0.91, within(0.001));
            assertThat(alerta.get("ventana_optima_horas")).isEqualTo(18);
            LocalDateTime generada = fecha(alerta, "ts_generacion");
            assertThat(generada).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES));
            assertThat(fecha(alerta, "ventana_inicio_ins")).isEqualTo(generada.plusHours(6));
            assertThat(fecha(alerta, "ventana_fin_ins")).isEqualTo(generada.plusHours(24));
            assertThat((String) alerta.get("accion_recomendada")).contains("entre 6 y 24 horas");

            // Estado reproductivo automático, con las fechas del último estado (GESTANTE, inseminada hace 60 días).
            Map<String, Object> estado = jdbcTemplate.queryForMap("SELECT * FROM estados_reproductivos "
                    + "WHERE animal_id = ? ORDER BY fecha_registro DESC, id DESC LIMIT 1", animal);
            assertThat(estado.get("estado")).isEqualTo("POSIBLE_CELO");
            assertThat(estado.get("origen")).isEqualTo("AUTOMATICO");
            assertThat(((java.sql.Date) estado.get("fecha_ultima_inseminacion")).toLocalDate())
                    .isEqualTo(LocalDate.now().minusDays(60));

            assertThat(lecturasSinEvaluar(animal, "celo")).isZero();

            AlertaEventDTO evento = rabbitTemplate.receiveAndConvert(cola.getName(), 5000,
                    new ParameterizedTypeReference<AlertaEventDTO>() {});
            assertThat(evento).isNotNull();
            assertThat(evento.getId()).isEqualTo(((Number) alerta.get("id")).longValue());
            assertThat(evento.getAnimalId()).isEqualTo(animal);
            assertThat(evento.isNueva()).isTrue();
        } finally {
            amqpAdmin.deleteQueue(cola.getName());
        }
    }

    @Test
    @DisplayName("H0008-02 - Alerta de celo - Predicción negativa y ventana incompleta no generan alerta")
    void H0008_02_prediccionNegativaYVentanaIncompleta() throws Exception {
        ML.responder(MlSimulado.CELO_PREDICT, CELO_NEGATIVO);
        Long completa = idAnimal(EST_A, "A-101");
        Long incompleta = idAnimal(EST_A, "A-103");
        // A-103 pierde 2 h de uplinks: el hueco supera la hora que se rellena y la ventana no se manda a ml-celo.
        LocalDateTime ahora = LocalDateTime.now();
        jdbcTemplate.update("DELETE FROM telemetria WHERE caravana_id = ? AND fecha_hora BETWEEN ? AND ?",
                idCaravana(incompleta), Timestamp.valueOf(ahora.minusHours(6)), Timestamp.valueOf(ahora.minusHours(4)));
        marcarUltimaLecturaPendiente(completa, "celo");
        marcarUltimaLecturaPendiente(incompleta, "celo");
        int estadosAntes = cantidadEstadosReproductivos(completa);

        correr(celoJob);

        // Solo A-101 llega a ml-celo; ninguna de las dos queda con alerta ni con un POSIBLE_CELO nuevo.
        assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isEqualTo(1);
        assertThat(alertas(completa, "CELO")).isEmpty();
        assertThat(alertas(incompleta, "CELO")).isEmpty();
        assertThat(cantidadEstadosReproductivos(completa)).isEqualTo(estadosAntes);
        // Las dos quedan evaluadas: la incompleta se reintenta con la próxima lectura, no en cada corrida.
        assertThat(lecturasSinEvaluar(completa, "celo")).isZero();
        assertThat(lecturasSinEvaluar(incompleta, "celo")).isZero();
    }

    @Test
    @DisplayName("H0008-03 - Alerta de celo - Sin alertas duplicadas ni telemetría procesada dos veces")
    void H0008_03_sinDuplicarNiReprocesar() throws Exception {
        ML.responder(MlSimulado.CELO_PREDICT, CELO_POSITIVO);
        Long animal = idAnimal(EST_A, "A-101");
        Long conAlerta = idAnimal(EST_A, "A-102");
        marcarUltimaLecturaPendiente(animal, "celo");
        marcarUltimaLecturaPendiente(conAlerta, "celo");

        correr(celoJob);

        // A-102 ya tiene una alerta de celo en las últimas 24 h: ni se consulta a ml-celo.
        assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isEqualTo(1);
        assertThat(alertas(animal, "CELO")).hasSize(1);
        assertThat(alertas(conAlerta, "CELO")).hasSize(1);
        assertThat(lecturasSinEvaluar(conAlerta, "celo")).isZero();

        // Sin telemetría nueva, la segunda corrida no vuelve a mandar la misma ventana.
        correr(celoJob);
        assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isEqualTo(1);

        // Con una lectura nueva, la alerta de hace un momento evita una segunda alerta del mismo celo.
        insertarLecturaNueva(animal);
        correr(celoJob);
        assertThat(ML.llamadas(MlSimulado.CELO_PREDICT)).isEqualTo(1);
        assertThat(alertas(animal, "CELO")).hasSize(1);
        assertThat(lecturasSinEvaluar(animal, "celo")).isZero();
    }

    private int cantidadEstadosReproductivos(Long animalId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM estados_reproductivos WHERE animal_id = ?",
                Integer.class, animalId);
    }

    private static LocalDateTime fecha(Map<String, Object> fila, String columna) {
        return ((Timestamp) fila.get(columna)).toLocalDateTime();
    }
}
