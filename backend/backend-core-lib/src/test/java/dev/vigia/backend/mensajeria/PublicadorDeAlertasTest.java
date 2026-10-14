package dev.vigia.backend.mensajeria;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;

@ExtendWith(MockitoExtension.class)
class PublicadorDeAlertasTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PublicadorDeAlertas publicador;
    private final AlertaEventDTO evento = AlertaEventDTO.builder().id(7L).build();

    @BeforeEach
    void setUp() {
        publicador = new PublicadorDeAlertas(rabbitTemplate);
    }

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sinTransaccion_publicaEnElMomento() {
        publicador.publicar(evento);

        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", evento);
    }

    @Test
    void conTransaccion_publicaRecienAlCommitear() {
        TransactionSynchronizationManager.initSynchronization();

        publicador.publicar(evento);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", evento);
    }

    @Test
    void conRollback_noPublica() {
        TransactionSynchronizationManager.initSynchronization();

        publicador.publicar(evento);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void siRabbitFalla_noPropagaElError() {
        doThrow(new AmqpException("rabbit caído")).when(rabbitTemplate)
                .convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", evento);

        assertThatCode(() -> publicador.publicar(evento)).doesNotThrowAnyException();
    }
}
