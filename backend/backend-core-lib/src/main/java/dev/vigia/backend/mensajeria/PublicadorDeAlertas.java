package dev.vigia.backend.mensajeria;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Único punto de salida de los eventos de alertas hacia {@link RabbitMQConfig#ALERTAS_EXCHANGE}
 * (fanout: el api los reenvía por SSE). Publica recién cuando commitea la transacción actual:
 * quien consume el evento relee la alerta de la base y tiene que encontrarla, y si la
 * transacción hace rollback no sale una alerta que nunca existió. Sin transacción activa
 * publica en el momento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicadorDeAlertas {

    private final RabbitTemplate rabbitTemplate;

    public void publicar(AlertaEventDTO evento) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            enviar(evento);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enviar(evento);
            }
        });
    }

    /** Los datos ya están commiteados: si Rabbit falla se pierde el aviso en vivo, no la alerta. */
    private void enviar(AlertaEventDTO evento) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", evento);
        } catch (RuntimeException e) {
            log.error("No se pudo publicar el evento de la alerta {}: {}", evento.getId(), e.getMessage());
        }
    }
}
