package dev.vigia.backend.service;

import dev.vigia.backend.dto.ml.TelemetriaEventDTO;
import dev.vigia.backend.repository.EstablecimientoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseTelemetriaPublisher {

    private final EstablecimientoRepository establecimientoRepository;

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Solo los miembros activos del establecimiento reciben sus posiciones en tiempo real (misma regla que
     * {@link SseAlertaPublisher#puedeSuscribirse}).
     */
    public boolean puedeSuscribirse(String email, Long establecimientoId) {
        return establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(email).stream()
                .anyMatch(e -> e.getId().equals(establecimientoId));
    }

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

    /** Mismo heartbeat que {@link SseAlertaPublisher#enviarHeartbeat()} (ahí está el porqué). */
    @Scheduled(fixedRate = 20_000)
    public void enviarHeartbeat() {
        emitters.values().forEach(list -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                list.remove(emitter);
            }
        }));
    }

    /** Para tests. */
    int clientesConectados(Long establecimientoId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(establecimientoId);
        return list == null ? 0 : list.size();
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
                log.debug("Cliente SSE desconectado o error de red ({}). Removiendo emitter.", e.getMessage());
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
