package dev.vigia.backend.service.ml;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.dto.ml.PuntoTelemetriaDTO;
import dev.vigia.backend.dto.ml.CeloPredictResponseDTO;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CeloDetectorStrategyImpl implements DetectorStrategy {

    private final RestClient restClient;
    private final TelemetriaRepository telemetriaRepository;
    private final AlertaRepository alertaRepository;
    private final RabbitTemplate rabbitTemplate;

    @SuppressWarnings("null")
    public CeloDetectorStrategyImpl(
            RestClient.Builder restClientBuilder,
            @Value("${vigia.ml.celo-url}") String celoUrl,
            TelemetriaRepository telemetriaRepository,
            AlertaRepository alertaRepository,
            RabbitTemplate rabbitTemplate) {
        this.restClient = restClientBuilder.baseUrl(celoUrl).build();
        this.telemetriaRepository = telemetriaRepository;
        this.alertaRepository = alertaRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @CircuitBreaker(name = "celoML", fallbackMethod = "fallbackProcess")
    @Retry(name = "celoML", fallbackMethod = "fallbackProcess")
    @SuppressWarnings("null")
    public void process(List<Telemetria> telemetriaList, String correlationId) {
        if (telemetriaList == null || telemetriaList.isEmpty()) {
            return;
        }

        Animal animal = telemetriaList.get(0).getCaravana().getAnimal();
        if (animal != null && alertaRepository.existsByAnimalAndDisparadorAndTsGeneracionAfter(animal, DisparadorAlerta.CELO, java.time.LocalDateTime.now().minusHours(24))) {
            log.info("Omitiendo inferencia de celo para animal {} porque ya tiene una alerta reciente (últimas 24hs).", animal.getId());
            List<Telemetria> pendientes = telemetriaList.stream()
                    .filter(t -> t.getEstadoInferencia() == EstadoInferencia.PENDIENTE)
                    .collect(Collectors.toList());
            pendientes.forEach(t -> t.setEstadoInferencia(EstadoInferencia.COMPLETADO));
            telemetriaRepository.saveAll(pendientes);
            return;
        }

        List<PuntoTelemetriaDTO> requestDTO = buildRequest(telemetriaList);

        log.info("Llamando a microservicio de celo con correlationId: {}", correlationId);

        CeloPredictResponseDTO response = restClient.post()
                .uri("/api/internal/ml/celo/predict")
                .header("X-Correlation-ID", correlationId)
                .body(requestDTO)
                .retrieve()
                .body(CeloPredictResponseDTO.class);

        log.info("Respuesta de microservicio de celo recibida: {}", response);

        List<Telemetria> pendientes = telemetriaList.stream()
                .filter(t -> t.getEstadoInferencia() == EstadoInferencia.PENDIENTE)
                .collect(Collectors.toList());
        pendientes.forEach(t -> t.setEstadoInferencia(EstadoInferencia.COMPLETADO));
        telemetriaRepository.saveAll(pendientes);

        if (response != null && response.isEventoCelo()) {
            log.info("¡Evento de celo detectado con confianza {}!", response.getConfianzaModelo());

            Alerta alerta = new Alerta();
            alerta.setAnimal(animal);
            alerta.setTipo("Celo Detectado");
            alerta.setSeveridad(SeveridadAlerta.ALTA);
            alerta.setTsGeneracion(java.time.LocalDateTime.now());
            alerta.setDisparador(DisparadorAlerta.CELO);
            alerta.setValorUmbral(0.5);
            alerta.setValorDetectado((double) response.getConfianzaModelo());
            alerta.setEstado(EstadoAlerta.PENDIENTE);
            alerta.setConfianzaModelo(response.getConfianzaModelo());
            alerta.setVentanaOptimaHoras(response.getVentanaOptimaHoras());
            
            // TODO: REVISAR con el equipo si esta es la formula final para las ventanas
            alerta.setVentanaInicioIns(alerta.getTsGeneracion());
            alerta.setVentanaFinIns(alerta.getTsGeneracion().plusHours(response.getVentanaOptimaHoras()));

            alerta = alertaRepository.save(alerta);

            AlertaEventDTO eventDTO = AlertaEventDTO.builder()
                    .id(alerta.getId())
                    .animalId(animal != null ? animal.getId() : null)
                    .tipo(alerta.getTipo())
                    .severidad(alerta.getSeveridad())
                    .tsGeneracion(alerta.getTsGeneracion())
                    .disparador(alerta.getDisparador())
                    .valorUmbral(alerta.getValorUmbral())
                    .valorDetectado(alerta.getValorDetectado())
                    .estado(alerta.getEstado())
                    .confianzaModelo(alerta.getConfianzaModelo())
                    .ventanaOptimaHoras(alerta.getVentanaOptimaHoras())
                    .build();

            rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", eventDTO);
            log.info("Alerta publicada en RabbitMQ con ID: {}", alerta.getId());
        }
    }

    public void fallbackProcess(List<Telemetria> telemetriaList, String correlationId, Throwable t) {
        log.error(
                "Fallo al comunicarse con el microservicio ML de celo (Correlation-ID: {}). Marcando como inferencia pendiente. Motivo: {}",
                correlationId, t.getMessage());
        telemetriaList.forEach(telemetria -> telemetria.setEstadoInferencia(EstadoInferencia.INFERENCIA_PENDIENTE));
        telemetriaRepository.saveAll(telemetriaList);
    }

    private List<PuntoTelemetriaDTO> buildRequest(List<Telemetria> telemetriaList) {
        return telemetriaList.stream()
                .map(t -> PuntoTelemetriaDTO.builder()
                        .moveState(t.getMoveState())
                        .latitud(t.getLatitud())
                        .longitud(t.getLongitud())
                        .build())
                .collect(Collectors.toList());
    }
}
