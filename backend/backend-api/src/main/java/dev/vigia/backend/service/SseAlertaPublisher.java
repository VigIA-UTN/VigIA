package dev.vigia.backend.service;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
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

    private final AnimalRepository animalRepository;

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
                        sendToEmitter(emitter, alerta.getId(), alerta.getAnimal().getId(), alerta.getTipo(),
                                alerta.getSeveridad() != null ? alerta.getSeveridad().name() : null,
                                alerta.getEstado(),
                                alerta.getTsGeneracion());
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

        if (alertaEvent.getAnimalId() == null) {
            log.warn("El evento de alerta ID {} no tiene animal asociado.", alertaEvent.getId());
            return;
        }

        // Buscamos el Animal para obtener el establecimientoId. 
        // No buscamos la Alerta porque podria no estar commiteada aun en la BD del worker!
        Optional<Animal> animalOpt = animalRepository.findById(alertaEvent.getAnimalId());
        if (animalOpt.isEmpty() || animalOpt.get().getEstablecimiento() == null) {
            log.warn("El animal con ID {} no fue encontrado o no tiene establecimiento.", alertaEvent.getAnimalId());
            return;
        }

        Long establecimientoId = animalOpt.get().getEstablecimiento().getId();

        CopyOnWriteArrayList<SseEmitter> list = emitters.get(establecimientoId);
        if (list == null || list.isEmpty()) {
            log.info(
                    "No hay clientes SSE conectados localmente para el establecimiento {}. Ignorando mensaje de deduplicación.",
                    establecimientoId);
            return;
        }

        log.info("Enviando evento de alerta ID {} a {} clientes del establecimiento {}", alertaEvent.getId(), list.size(),
                establecimientoId);

        for (SseEmitter emitter : list) {
            try {
                sendToEmitter(emitter, alertaEvent.getId(), alertaEvent.getAnimalId(), alertaEvent.getTipo(),
                        alertaEvent.getSeveridad() != null ? alertaEvent.getSeveridad().name() : null,
                        alertaEvent.getEstado(),
                        alertaEvent.getTsGeneracion());
            } catch (Exception e) {
                log.error("Error enviando alerta por SSE a un cliente, removiendo emitter.", e);
                list.remove(emitter);
            }
        }
    }

    /**
     * Enviar evento construido
     */
    @SuppressWarnings("null")
    private void sendToEmitter(SseEmitter emitter, Long id, Long animalId, String tipo, String severidad, EstadoAlerta estado, java.time.LocalDateTime tsGeneracion) throws IOException {
        AlertaResponse response = AlertaResponse.builder()
                .id(id)
                .animalId(animalId)
                .tipo(tipo)
                .severidad(severidad)
                .estado(estado)
                .tsGeneracion(tsGeneracion)
                .build();

        emitter.send(SseEmitter.event()
                .id(String.valueOf(id))
                .data(response));
    }
}
