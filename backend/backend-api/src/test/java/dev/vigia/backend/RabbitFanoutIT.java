package dev.vigia.backend;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.SeveridadAlerta;
import org.junit.jupiter.api.AfterEach;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fanout de alertas en RabbitMQ")
class RabbitFanoutIT extends IntegracionBase {

    private static final long ESPERA_MS = 5000;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private FanoutExchange alertasExchange;

    private final List<Queue> colasDePrueba = new ArrayList<>();

    @AfterEach
    void borrarColas() {
        colasDePrueba.forEach(cola -> amqpAdmin.deleteQueue(cola.getName()));
        colasDePrueba.clear();
    }

    @Test
    @DisplayName("Una alerta publicada llega a todas las colas ligadas al exchange (una por réplica de la API)")
    void alertaLlegaATodasLasReplicas() {
        Queue replicaA = colaLigadaA(alertasExchange);
        Queue replicaB = colaLigadaA(alertasExchange);

        AlertaEventDTO evento = AlertaEventDTO.builder()
                .id(4242L)
                .tipo("Celo Detectado (IT)")
                .severidad(SeveridadAlerta.ALTA)
                .tsGeneracion(LocalDateTime.now())
                .disparador(DisparadorAlerta.CELO)
                .valorUmbral(0.5)
                .valorDetectado(0.95)
                .estado(EstadoAlerta.PENDIENTE)
                .build();

        rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", evento);

        for (Queue replica : List.of(replicaA, replicaB)) {
            AlertaEventDTO recibido = rabbitTemplate.receiveAndConvert(
                    replica.getName(), ESPERA_MS, new ParameterizedTypeReference<AlertaEventDTO>() {});
            assertThat(recibido).as("mensaje en %s", replica.getName()).isEqualTo(evento);
        }
    }

    private Queue colaLigadaA(FanoutExchange exchange) {
        Queue cola = new AnonymousQueue();
        amqpAdmin.declareQueue(cola);
        amqpAdmin.declareBinding(BindingBuilder.bind(cola).to(exchange));
        colasDePrueba.add(cola);
        return cola;
    }
}
