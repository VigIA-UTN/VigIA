package dev.vigia.backend;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.TelemetriaEventDTO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Worker sobre TimescaleDB y RabbitMQ reales")
class WorkerIntegracionIT extends IntegracionBase {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private FanoutExchange telemetriaExchange;

    @Test
    @DisplayName("Las migraciones de backend-api se aplican y las entidades del worker validan contra ellas")
    void migracionesAplicadas() {
        // Que el contexto haya levantado ya prueba ddl-auto: validate contra el esquema migrado.
        assertThat(flyway.info().pending()).isEmpty();
        Integer hypertables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM timescaledb_information.hypertables WHERE hypertable_name = 'telemetria'",
                Integer.class);
        assertThat(hypertables).isEqualTo(1);
    }

    @Test
    @DisplayName("Las tablas de Spring Batch existen en la base")
    void tablasDeSpringBatchCreadas() {
        Integer tablas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_name = 'batch_job_execution'",
                Integer.class);
        assertThat(tablas).isEqualTo(1);
    }

    @Test
    @DisplayName("La telemetría publicada en el exchange llega a una cola ligada (como la de cada réplica de la API)")
    void telemetriaLlegaAlExchange() {
        Queue cola = new AnonymousQueue();
        amqpAdmin.declareQueue(cola);
        amqpAdmin.declareBinding(BindingBuilder.bind(cola).to(telemetriaExchange));
        try {
            TelemetriaEventDTO evento = TelemetriaEventDTO.builder()
                    .animalId(7L)
                    .establecimientoId(3L)
                    .latitud(-34.6)
                    .longitud(-58.4)
                    .fechaHora(LocalDateTime.now())
                    .build();

            rabbitTemplate.convertAndSend(RabbitMQConfig.TELEMETRIA_EXCHANGE, "", evento);

            TelemetriaEventDTO recibido = rabbitTemplate.receiveAndConvert(
                    cola.getName(), 5000, new ParameterizedTypeReference<TelemetriaEventDTO>() {});
            assertThat(recibido).isEqualTo(evento);
        } finally {
            amqpAdmin.deleteQueue(cola.getName());
        }
    }
}
