package dev.vigia.backend.service;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertaWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = RabbitMQConfig.ALERTAS_QUEUE)
    public void handleAlertaEvent(AlertaEventDTO alertaEvent) {
        log.info("Recibida alerta desde RabbitMQ para WebSocket (Alerta ID: {})", alertaEvent.getId());

        messagingTemplate.convertAndSend("/topic/alertas", alertaEvent);
    }
}
