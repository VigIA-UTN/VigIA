package dev.vigia.backend.service;

import dev.vigia.backend.dto.ml.TelemetriaEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseTelemetriaPublisher {

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter registrarClienteSse(Long establecimientoId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(establecimientoId,
                k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        log.info("Nuevo cliente SSE de telemetría conectado para establecimiento {}", establecimientoId);

        emitter.onCompletion(() -> {
            list.remove(emitter);
        });
        emitter.onTimeout(() -> {
            emitter.complete();
            list.remove(emitter);
        });
        emitter.onError((e) -> {
            emitter.complete();
            list.remove(emitter);
        });

        return emitter;
    }

    @RabbitListener(queues = "#{telemetriaQueue.name}")
    @SuppressWarnings({ "null" })
    public void handleTelemetriaEvent(TelemetriaEventDTO telemetriaEvent) {
        Long establecimientoId = telemetriaEvent.getEstablecimientoId();

        CopyOnWriteArrayList<SseEmitter> list = emitters.get(establecimientoId);
        if (list == null || list.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : list) {
            try {
                sendToEmitter(emitter, telemetriaEvent);
            } catch (Exception e) {
                log.error("Error enviando telemetria por SSE a un cliente, removiendo emitter.", e);
                list.remove(emitter);
            }
        }
    }

    @SuppressWarnings("null")
    private void sendToEmitter(SseEmitter emitter, TelemetriaEventDTO telemetriaEvent) throws IOException {
        emitter.send(SseEmitter.event()
                .id(telemetriaEvent.getAnimalId().toString())
                .data(telemetriaEvent));
    }
}
