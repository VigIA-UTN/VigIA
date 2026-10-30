package dev.vigia.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.vigia.backend.repository.EstablecimientoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseTelemetriaPublisherTest {

    private final SseTelemetriaPublisher publisher = new SseTelemetriaPublisher(mock(EstablecimientoRepository.class));

    @Test
    void heartbeat_clienteQueYaSeFue_loSacaDeLaLista() {
        publisher.registrarClienteSse(1L);
        SseEmitter cerrado = publisher.registrarClienteSse(1L);
        cerrado.complete(); // enviarle a un emitter terminado falla, igual que a un cliente que cortó la conexión

        publisher.enviarHeartbeat();

        assertThat(publisher.clientesConectados(1L)).isEqualTo(1);
    }
}
