package dev.vigia.backend.controller;

import dev.vigia.backend.dto.IngestaRequestDTO;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/ingesta")
public class IngestaController {

    private final TelemetriaRepository telemetriaRepository;
    private final CaravanaRepository caravanaRepository;
    private final ApplicationEventPublisher eventPublisher;

    public IngestaController(TelemetriaRepository telemetriaRepository, CaravanaRepository caravanaRepository, ApplicationEventPublisher eventPublisher) {
        this.telemetriaRepository = telemetriaRepository;
        this.caravanaRepository = caravanaRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping("/uplink")
    public ResponseEntity<String> receiveUplink(@RequestBody IngestaRequestDTO request) {
        Optional<Caravana> caravanaOpt = caravanaRepository.findByCodigo(request.getDevice_id());
        if (caravanaOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Dispositivo no registrado");
        }

        Telemetria telemetria = new Telemetria();
        telemetria.setCaravana(caravanaOpt.get());
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

        return ResponseEntity.ok("OK");
    }
}
