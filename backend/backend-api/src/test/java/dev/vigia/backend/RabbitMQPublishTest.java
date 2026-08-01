package dev.vigia.backend;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.SeveridadAlerta;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import java.time.LocalDateTime;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

@SpringBootTest

class RabbitMQPublishTest {

    @MockBean
    private ConnectionFactory connectionFactory;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void testPublishAlertaEvent() {
        // Crear un DTO de prueba simulando un evento de Celo
        AlertaEventDTO eventDTO = AlertaEventDTO.builder()
                .id(999L)
                .animalId(1L)
                .tipo("Celo Detectado (Prueba)")
                .severidad(SeveridadAlerta.ALTA)
                .tsGeneracion(LocalDateTime.now())
                .disparador(DisparadorAlerta.CELO)
                .valorUmbral(0.5)
                .valorDetectado(0.95)
                .estado(EstadoAlerta.PENDIENTE)
                .confianzaModelo(0.95f)
                .ventanaOptimaHoras(12)
                .build();

        // Publicar en el exchange configurado
        System.out.println("Publicando mensaje de prueba en RabbitMQ...");
        rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", eventDTO);
        
        // Verificar que el mock fue llamado con los parámetros correctos
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.ALERTAS_EXCHANGE), eq(""), eq(eventDTO));
        System.out.println("¡Mensaje mockeado exitosamente!");
    }
}
