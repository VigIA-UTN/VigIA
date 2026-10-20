package dev.vigia.backend.controller;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.UltimaPosicionResponseDTO;
import dev.vigia.backend.dto.ml.TelemetriaEventDTO;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.service.GeofenceVerificationService;
import dev.vigia.backend.dto.ttn.TTNWebhookRequestDTO;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/ingesta")
@Slf4j
public class IngestaController {

    private final TelemetriaRepository telemetriaRepository;
    private final CaravanaRepository caravanaRepository;
    private final AnimalRepository animalRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RabbitTemplate rabbitTemplate;
    private final GeofenceVerificationService geofenceVerificationService;

    public IngestaController(TelemetriaRepository telemetriaRepository, CaravanaRepository caravanaRepository,
            AnimalRepository animalRepository, ApplicationEventPublisher eventPublisher, RabbitTemplate rabbitTemplate,
            dev.vigia.backend.service.GeofenceVerificationService geofenceVerificationService) {
        this.telemetriaRepository = telemetriaRepository;
        this.caravanaRepository = caravanaRepository;
        this.animalRepository = animalRepository;
        this.eventPublisher = eventPublisher;
        this.rabbitTemplate = rabbitTemplate;
        this.geofenceVerificationService = geofenceVerificationService;
    }

    /**
     * POST /api/v1/ingesta/uplink
     * <p>
     * Recibe el webhook de uplink de The Things Network (TTN) con la telemetría
     * cruda de una caravana (posición GNSS o heartbeat), la persiste, publica el
     * evento en RabbitMQ para los consumidores de inferencia y dispara la
     * verificación de perímetro. Es idempotente por frame counter (fCnt).
     *
     * @param requestRaw payload del webhook TTN (puede venir envuelto en
     *                   {@code data} o directo)
     * @return 200 siempre; el cuerpo indica si se procesó o el motivo por el que
     *         se ignoró (sin dev_eui, dispositivo no registrado, sin animal
     *         asociado, frame duplicado, tipo desconocido)
     */
    @PostMapping("/uplink")
    @SuppressWarnings("null")
    public ResponseEntity<String> receiveUplink(@RequestBody TTNWebhookRequestDTO requestRaw) {
        TTNWebhookRequestDTO payload = requestRaw.getData() != null ? requestRaw.getData() : requestRaw;

        if (payload.getEndDeviceIds() == null || payload.getEndDeviceIds().getDevEui() == null) {
            log.warn("Payload TTN recibido sin dev_eui.");
            return ResponseEntity.ok("Ignorado: Sin dev_eui");
        }

        String devEui = payload.getEndDeviceIds().getDevEui();
        Optional<Caravana> caravanaOpt = caravanaRepository.findByDevEui(devEui);

        if (caravanaOpt.isEmpty()) {
            log.warn("Webhook TTN recibido para devEui {} no registrado en el sistema.", devEui);
            return ResponseEntity.ok("Ignorado: Dispositivo no registrado");
        }

        Caravana caravana = caravanaOpt.get();
        Optional<Animal> animalOpt = animalRepository.findConLoteYCaravanaByCaravanaId(caravana.getId());
        if (animalOpt.isEmpty()) {
            log.warn("Webhook TTN recibido para devEui {} que no está asociado a ningún animal.", devEui);
            return ResponseEntity.ok("Ignorado: Sin animal asociado");
        }
        Animal animal = animalOpt.get();

        TTNWebhookRequestDTO.UplinkMessage uplink = payload.getUplinkMessage();
        if (uplink == null || uplink.getDecodedPayload() == null) {
            log.warn("Webhook TTN recibido para devEui {} sin payload decodificado.", devEui);
            return ResponseEntity.ok("Ignorado: Sin decoded_payload");
        }

        // TTN omite f_cnt cuando vale 0 (p. ej. el Registration posterior a un join).
        int frameCounter = uplink.getFCnt() != null ? uplink.getFCnt() : 0;
        // Un rejoin OTAA cambia la session key y reinicia el f_cnt: es una sesión nueva,
        // no un duplicado, aunque el contador sea menor al último guardado.
        String sessionKeyId = uplink.getSessionKeyId();
        boolean sesionNueva = sessionKeyId != null && !sessionKeyId.equals(caravana.getUltimaSessionKeyId());
        if (!sesionNueva && caravana.getUltimoFrameCounter() != null
                && frameCounter <= caravana.getUltimoFrameCounter()) {
            log.info(
                    "Frame duplicado o retransmitido para devEui {} (fCnt={}, ultimoFrameCounter={}), ignorado por idempotencia.",
                    devEui, frameCounter, caravana.getUltimoFrameCounter());
            return ResponseEntity.ok("Ignorado: frame duplicado (idempotencia)");
        }

        TTNWebhookRequestDTO.DecodedPayload decoded = uplink.getDecodedPayload();

        Optional<Telemetria> ultimaOpt = telemetriaRepository.findFirstByCaravanaOrderByFechaHoraDesc(caravana);

        Telemetria telemetria;
        boolean esNueva = false;

        if ("Heartbeat".equalsIgnoreCase(decoded.getType())) {
            if (ultimaOpt.isPresent()) {
                // Actualizamos la fila existente para no inyectar coordenadas duplicadas en la
                // serie de tiempo
                telemetria = ultimaOpt.get();
                if (decoded.getMoveState() != null) {
                    telemetria.setMoveState(decoded.getMoveState());
                }
                if (decoded.getBattery() != null) {
                    try {
                        telemetria.setBateria(Double.parseDouble(decoded.getBattery().replace("%", "")));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            } else {
                // Si no hay última posición, no podemos hacer mucho con un heartbeat sin
                // coordenadas,
                // pero lo guardamos para tener la batería.
                telemetria = new Telemetria();
                telemetria.setCaravana(caravana);
                telemetria.setFechaHora(java.time.LocalDateTime.now());
                telemetria.setMoveState(decoded.getMoveState());
                if (decoded.getBattery() != null) {
                    try {
                        telemetria.setBateria(Double.parseDouble(decoded.getBattery().replace("%", "")));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                telemetria.setEstadoInferenciaCelo(EstadoInferencia.PENDIENTE);
                telemetria.setEstadoInferenciaSalud(EstadoInferencia.PENDIENTE);
                esNueva = true;
            }
        } else if ("GNSSPosition".equalsIgnoreCase(decoded.getType())) {
            // Un fix fallido llega como GNSSPosition con lat/lon ~0 (flag 0x1 en el tag);
            // no es una posición real y no debe llegar a la serie de tiempo.
            if (decoded.getLatitude() != null && decoded.getLongitude() != null
                    && Math.abs(decoded.getLatitude()) < 1e-6 && Math.abs(decoded.getLongitude()) < 1e-6) {
                log.info("Fix GNSS fallido para devEui {} (coordenadas ~0), ignorado.", devEui);
                return ResponseEntity.ok("Ignorado: fix GNSS fallido");
            }
            // GNSSPosition o similar: creamos nueva fila, a menos que la última fila sea un
            // Heartbeat sin coordenadas creado hace instantes (llegó desordenado).
            if (ultimaOpt.isPresent() && ultimaOpt.get().getLatitud() == null &&
                    ultimaOpt.get().getFechaHora().isAfter(java.time.LocalDateTime.now().minusMinutes(5))) {
                telemetria = ultimaOpt.get();
                esNueva = false;
            } else {
                telemetria = new Telemetria();
                telemetria.setCaravana(caravana);
                esNueva = true;
            }

            try {
                telemetria.setFechaHora(java.time.Instant.parse(uplink.getReceivedAt())
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            } catch (Exception e) {
                // Solo pisamos la fecha si es una fila nueva, para no arruinar la del heartbeat
                // si actualizamos
                if (esNueva)
                    telemetria.setFechaHora(java.time.LocalDateTime.now());
            }

            telemetria.setLatitud(decoded.getLatitude());
            telemetria.setLongitud(decoded.getLongitude());

            // Heredamos batería y moveState del último si no vienen en este payload
            telemetria.setMoveState(decoded.getMoveState() != null ? decoded.getMoveState()
                    : ultimaOpt.map(Telemetria::getMoveState).orElse(null));
            if (decoded.getBattery() != null) {
                try {
                    telemetria.setBateria(Double.parseDouble(decoded.getBattery().replace("%", "")));
                } catch (NumberFormatException e) {
                    telemetria.setBateria(ultimaOpt.map(Telemetria::getBateria).orElse(null));
                }
            } else {
                telemetria.setBateria(ultimaOpt.map(Telemetria::getBateria).orElse(null));
            }
            telemetria.setEstadoInferenciaCelo(EstadoInferencia.PENDIENTE);
            telemetria.setEstadoInferenciaSalud(EstadoInferencia.PENDIENTE);
        } else {
            log.warn("Webhook TTN recibido para devEui {} con tipo desconocido: {}.", devEui, decoded.getType());
            return ResponseEntity.ok("Ignorado: Tipo desconocido");
        }

        telemetriaRepository.save(telemetria);

        caravana.setUltimoFrameCounter(frameCounter);
        if (sessionKeyId != null) {
            caravana.setUltimaSessionKeyId(sessionKeyId);
        }
        caravana.setUltimaSenial(telemetria.getFechaHora());
        if (telemetria.getBateria() != null) {
            caravana.setBateria(telemetria.getBateria());
        }
        caravanaRepository.save(caravana);

        if (esNueva) {
            eventPublisher.publishEvent("NUEVA_TELEMETRIA");
        }

        TelemetriaEventDTO dto = TelemetriaEventDTO.builder()
                .animalId(animal.getId())
                .establecimientoId(animal.getEstablecimiento().getId())
                .latitud(telemetria.getLatitud())
                .longitud(telemetria.getLongitud())
                .fechaHora(telemetria.getFechaHora())
                .build();
        rabbitTemplate.convertAndSend(RabbitMQConfig.TELEMETRIA_EXCHANGE, "", dto);

        if (telemetria.getLatitud() != null && telemetria.getLongitud() != null) {
            geofenceVerificationService.verificarPosicion(
                    animal, telemetria.getLatitud(), telemetria.getLongitud(), telemetria.getFechaHora());
        }

        return ResponseEntity.ok("OK");
    }

    /**
     * GET /api/v1/ingesta/ultima-posicion/{deviceId}
     * <p>
     * Devuelve la última posición/batería conocida de una caravana. Usado por el
     * simulador (ver {@code simulador/simulador.py}) para arrancar
     * continuando la última posición real que ya tiene el animal en la base
     * -sembrada por {@code DataInitializer} o dejada por una corrida anterior del
     * propio simulador- en vez de coordenadas hardcodeadas que pueden
     * desincronizarse (un salto de
     * posición desconectado de la última fila real dispara una alerta de celo
     * falsa por la distancia anómala entre el punto sembrado y el primer paquete
     * en vivo).
     *
     * @param deviceId código de la caravana
     * @return 200 con la última posición, 404 si la caravana o su telemetría no
     *         existen
     */
    @GetMapping("/ultima-posicion/{deviceId}")
    public ResponseEntity<?> getUltimaPosicion(@PathVariable("deviceId") String deviceId) {
        Optional<Caravana> caravanaOpt = caravanaRepository.findByCodigo(deviceId);
        if (caravanaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<Telemetria> ultimaOpt = telemetriaRepository
                .findFirstByCaravanaOrderByFechaHoraDesc(caravanaOpt.get());
        if (ultimaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Telemetria t = ultimaOpt.get();
        UltimaPosicionResponseDTO dto = new UltimaPosicionResponseDTO(
                t.getLatitud(), t.getLongitud(), t.getBateria(), t.getMoveState(), t.getFechaHora());
        return ResponseEntity.ok(dto);
    }
}
