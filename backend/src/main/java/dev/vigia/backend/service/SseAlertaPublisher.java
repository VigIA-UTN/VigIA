package dev.vigia.backend.service;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.repository.AlertaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseAlertaPublisher {

    private final AlertaRepository alertaRepository;

    // Map: establecimientoId -> list of active SseEmitters
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Registers a new SSE client for a specific establecimiento.
     * If lastEventId is provided, fetches and sends missed alerts immediately.
     */
    public SseEmitter registrarClienteSse(Long establecimientoId, Long lastEventId) {
        // Timeout of 30 minutes. Spring Boot will keep the connection open.
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(establecimientoId,
                k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        log.info("Nuevo cliente SSE conectado para establecimiento {}", establecimientoId);

        emitter.onCompletion(() -> {
            log.info("Cliente SSE completado para establecimiento {}", establecimientoId);
            list.remove(emitter);
        });
        emitter.onTimeout(() -> {
            log.info("Cliente SSE timeout para establecimiento {}", establecimientoId);
            emitter.complete();
            list.remove(emitter);
        });
        emitter.onError((e) -> {
            log.error("Error en cliente SSE para establecimiento {}", establecimientoId, e);
            emitter.complete();
            list.remove(emitter);
        });

        // Send missed alerts if lastEventId > 0
        if (lastEventId != null && lastEventId > 0) {
            List<Alerta> perdidas = alertaRepository
                    .findCeloByEstablecimientoAndIdGreaterThanOrderByIdAsc(establecimientoId, lastEventId);
            if (!perdidas.isEmpty()) {
                log.info("Enviando {} alertas perdidas al cliente reconectado del establecimiento {}", perdidas.size(),
                        establecimientoId);
                for (Alerta alerta : perdidas) {
                    try {
                        sendToEmitter(emitter, alerta);
                    } catch (Exception e) {
                        log.error("Fallo enviando alerta perdida, cancelando el resto. {}", e.getMessage());
                        list.remove(emitter);
                        break;
                    }
                }
            }
        }

        return emitter;
    }

    /**
     * Listens to the fanout exchange using the auto-delete queue defined in
     * RabbitMQConfig.
     */
    @RabbitListener(queues = "#{alertasQueue.name}")
    @SuppressWarnings({ "null" })
    public void handleAlertaEvent(AlertaEventDTO alertaEvent) {
        log.info("Recibida alerta desde RabbitMQ para SSE (Alerta ID: {})", alertaEvent.getId());

        // Find the Alerta to get the establecimientoId
        Optional<Alerta> alertaOpt = alertaRepository.findById(alertaEvent.getId());
        if (alertaOpt.isEmpty()) {
            log.warn("La alerta con ID {} no fue encontrada en la BD, se ignora el evento", alertaEvent.getId());
            return;
        }

        Alerta alerta = alertaOpt.get();
        Long establecimientoId = alerta.getAnimal().getEstablecimiento().getId();

        CopyOnWriteArrayList<SseEmitter> list = emitters.get(establecimientoId);
        if (list == null || list.isEmpty()) {
            log.info(
                    "No hay clientes SSE conectados localmente para el establecimiento {}. Ignorando mensaje de deduplicación.",
                    establecimientoId);
            return;
        }

        log.info("Enviando evento de alerta ID {} a {} clientes del establecimiento {}", alerta.getId(), list.size(),
                establecimientoId);

        for (SseEmitter emitter : list) {
            try {
                sendToEmitter(emitter, alerta);
            } catch (Exception e) {
                log.error("Error enviando alerta por SSE a un cliente, removiendo emitter.", e);
                list.remove(emitter);
            }
        }
    }

    /**
     * Enviar missed alerts
     */

    @SuppressWarnings("null")
    private void sendToEmitter(SseEmitter emitter, Alerta alerta) throws IOException {
        // Enviar el DTO que el frontend espera (AlertaResponse)
        // Para simplificar, armamos el DTO manualmente similar a como hace el
        // AlertaMapper
        AlertaResponse response = AlertaResponse.builder()
                .id(alerta.getId())
                .animalId(alerta.getAnimal().getId())
                .tipo(alerta.getTipo())
                .severidad(alerta.getSeveridad().name())
                .estado(alerta.getEstado())
                .tsGeneracion(alerta.getTsGeneracion())
                .build();

        emitter.send(SseEmitter.event()
                .id(String.valueOf(alerta.getId())) // Set Last-Event-ID for the client
                .data(response));
    }
}
