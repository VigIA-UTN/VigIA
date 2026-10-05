package dev.vigia.backend.service;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseAlertaPublisher {

    private final AlertaRepository alertaRepository;

    private final AnimalRepository animalRepository;

    private final CeloDatoDesactualizadoEvaluator datoDesactualizadoEvaluator;

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter registrarClienteSse(Long establecimientoId, Long lastEventId) {
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

        if (lastEventId != null && lastEventId > 0) {
            List<Alerta> perdidas = alertaRepository
                    .findByEstablecimientoAndIdGreaterThanOrderByIdAsc(establecimientoId, lastEventId);
            if (!perdidas.isEmpty()) {
                log.info("Enviando {} alertas perdidas al cliente reconectado del establecimiento {}", perdidas.size(),
                        establecimientoId);
                for (Alerta alerta : perdidas) {
                    try {
                        sendToEmitter(emitter, alerta.getId(), alerta.getAnimal().getId(),
                                alerta.getDisparador(),
                                alerta.getTipo(),
                                alerta.getSeveridad() != null ? alerta.getSeveridad().name() : null,
                                alerta.getEstado(),
                                alerta.getTsGeneracion(),
                                alerta.getLatitud(),
                                alerta.getLongitud(),
                                alerta.getAccionRecomendada(),
                                alerta.getAnimal());
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

    @RabbitListener(queues = "#{alertasQueue.name}")
    @Transactional(readOnly = true)
    @SuppressWarnings({ "null" })
    public void handleAlertaEvent(AlertaEventDTO alertaEvent) {
        log.info("Recibida alerta desde RabbitMQ para SSE (Alerta ID: {})", alertaEvent.getId());

        if (alertaEvent.getAnimalId() == null) {
            log.warn("El evento de alerta ID {} no tiene animal asociado.", alertaEvent.getId());
            return;
        }

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

        log.info("Enviando evento de alerta ID {} a {} clientes del establecimiento {}", alertaEvent.getId(),
                list.size(),
                establecimientoId);

        for (SseEmitter emitter : list) {
            try {
                sendToEmitter(emitter, alertaEvent.getId(), alertaEvent.getAnimalId(),
                        alertaEvent.getDisparador(),
                        alertaEvent.getTipo(),
                        alertaEvent.getSeveridad() != null ? alertaEvent.getSeveridad().name() : null,
                        alertaEvent.getEstado(),
                        alertaEvent.getTsGeneracion(),
                        alertaEvent.getLatitud(),
                        alertaEvent.getLongitud(),
                        alertaEvent.getAccionRecomendada(),
                        animalOpt.get());
            } catch (Exception e) {
                log.error("Error enviando alerta por SSE a un cliente, removiendo emitter.", e);
                list.remove(emitter);
            }
        }
    }

    @SuppressWarnings("null")
    private void sendToEmitter(SseEmitter emitter, Long id, Long animalId, DisparadorAlerta disparador,
            String subtipo, String severidad, EstadoAlerta estado, LocalDateTime tsGeneracion,
            Double latitud, Double longitud, String accionRecomendada, Animal animal)
            throws IOException {
        LocalDateTime ultimaSenial = datoDesactualizadoEvaluator.aplica(disparador, subtipo, estado)
                && animal != null && animal.getCaravana() != null
                        ? animal.getCaravana().getUltimaSenial()
                        : null;
        AlertaResponse response = AlertaResponse.builder()
                .id(id)
                .animalId(animalId)
                .tipo(disparador != null ? disparador.name() : subtipo)
                .subtipo(subtipo)
                .severidad(severidad)
                .estado(estado)
                .tsGeneracion(tsGeneracion)
                .latitud(latitud)
                .longitud(longitud)
                .accionRecomendada(accionRecomendada)
                .caravanaUltimaSenial(ultimaSenial)
                .datoPosiblementeDesactualizado(
                        datoDesactualizadoEvaluator.esDatoDesactualizado(disparador, subtipo, estado, ultimaSenial))
                .horasUmbralDatoDesactualizado(datoDesactualizadoEvaluator.getHorasUmbral())
                .build();

        emitter.send(SseEmitter.event()
                .id(String.valueOf(id))
                .data(response));
    }
}
