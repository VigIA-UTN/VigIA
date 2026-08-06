package dev.vigia.backend.controller;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.IngestaRequestDTO;
import dev.vigia.backend.dto.ml.TelemetriaEventDTO;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/ingesta")
public class IngestaController {

    private final TelemetriaRepository telemetriaRepository;
    private final CaravanaRepository caravanaRepository;
    private final AnimalRepository animalRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RabbitTemplate rabbitTemplate;

    public IngestaController(TelemetriaRepository telemetriaRepository, CaravanaRepository caravanaRepository, AnimalRepository animalRepository, ApplicationEventPublisher eventPublisher, RabbitTemplate rabbitTemplate) {
        this.telemetriaRepository = telemetriaRepository;
        this.caravanaRepository = caravanaRepository;
        this.animalRepository = animalRepository;
        this.eventPublisher = eventPublisher;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/uplink")
    public ResponseEntity<String> receiveUplink(@RequestBody IngestaRequestDTO request) {
        Optional<Caravana> caravanaOpt = caravanaRepository.findByCodigo(request.getDevice_id());
        if (caravanaOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Dispositivo no registrado");
        }

        Caravana caravana = caravanaOpt.get();

        Telemetria telemetria = new Telemetria();
        telemetria.setCaravana(caravana);
        telemetria.setFechaHora(request.getTimestamp());
        telemetria.setLatitud(request.getPayload().getLatitud());
        telemetria.setLongitud(request.getPayload().getLongitud());
        telemetria.setMoveState(request.getPayload().getMoveState());
        telemetria.setNivelRumia(request.getPayload().getNivelRumia());
        telemetria.setBateria(request.getPayload().getBateria());
        telemetria.setEstadoInferencia(EstadoInferencia.PENDIENTE);

        telemetriaRepository.save(telemetria);
        
        // El pipeline (CeloBatchConfig) lee de la base de datos periódicamente.
        // Opcional: publicar evento de spring
        eventPublisher.publishEvent("NUEVA_TELEMETRIA");

        // Buscar el animal para obtener el establecimientoId
        Optional<Animal> animalOpt = animalRepository.findByCaravanaId(caravana.getId());
        if (animalOpt.isPresent()) {
            Animal animal = animalOpt.get();
            TelemetriaEventDTO dto = TelemetriaEventDTO.builder()
                .animalId(animal.getId())
                .establecimientoId(animal.getEstablecimiento().getId())
                .latitud(telemetria.getLatitud())
                .longitud(telemetria.getLongitud())
                .fechaHora(telemetria.getFechaHora())
                .build();
            rabbitTemplate.convertAndSend(RabbitMQConfig.TELEMETRIA_EXCHANGE, "", dto);
        }

        return ResponseEntity.ok("OK");
    }
}
