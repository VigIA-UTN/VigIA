package dev.vigia.backend.service.ml;

import dev.vigia.backend.client.MlCeloClient;
import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.dto.ml.PuntoTelemetriaDTO;
import dev.vigia.backend.dto.ml.CeloPredictResponseDTO;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.model.OrigenEstado;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CeloDetectorStrategyImpl implements DetectorStrategy {

        private final MlCeloClient mlCeloClient;
        private final TelemetriaRepository telemetriaRepository;
        private final AlertaRepository alertaRepository;
        private final EstadoReproductivoRepository estadoReproductivoRepository;
        private final RabbitTemplate rabbitTemplate;

        public CeloDetectorStrategyImpl(
                        MlCeloClient mlCeloClient,
                        TelemetriaRepository telemetriaRepository,
                        AlertaRepository alertaRepository,
                        EstadoReproductivoRepository estadoReproductivoRepository,
                        RabbitTemplate rabbitTemplate) {
                this.mlCeloClient = mlCeloClient;
                this.telemetriaRepository = telemetriaRepository;
                this.alertaRepository = alertaRepository;
                this.estadoReproductivoRepository = estadoReproductivoRepository;
                this.rabbitTemplate = rabbitTemplate;
        }

        @Override
        public void process(List<Telemetria> telemetriaList, String correlationId) {
                if (telemetriaList == null || telemetriaList.isEmpty()) {
                        return;
                }
                try {
                        procesar(telemetriaList, correlationId);
                } catch (RuntimeException e) {
                        marcarInferenciaPendiente(telemetriaList, correlationId, e);
                }
        }

        @SuppressWarnings("null")
        private void procesar(List<Telemetria> telemetriaList, String correlationId) {
                Animal animal = telemetriaList.get(0).getCaravana().getAnimal();
                if (animal != null && alertaRepository.existsByAnimalAndDisparadorAndTsGeneracionAfter(animal,
                                DisparadorAlerta.CELO, java.time.LocalDateTime.now().minusHours(24))) {
                        log.info("Omitiendo inferencia de celo para animal {} porque ya tiene una alerta reciente (últimas 24hs).",
                                        animal.getId());
                        List<Telemetria> pendientes = telemetriaList.stream()
                                        .filter(t -> t.getEstadoInferenciaCelo() == EstadoInferencia.PENDIENTE)
                                        .collect(Collectors.toList());
                        pendientes.forEach(t -> t.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO));
                        telemetriaRepository.saveAll(pendientes);
                        return;
                }

                List<PuntoTelemetriaDTO> requestDTO = buildRequest(telemetriaList);

                CeloPredictResponseDTO response = mlCeloClient.predict(requestDTO, correlationId);

                log.info("Respuesta de microservicio de celo recibida: {}", response);

                List<Telemetria> pendientes = telemetriaList.stream()
                                .filter(t -> t.getEstadoInferenciaCelo() == EstadoInferencia.PENDIENTE)
                                .collect(Collectors.toList());
                pendientes.forEach(t -> t.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO));
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

                        alerta.setAccionRecomendada(
                                        "Planificar inseminación artificial dentro de la ventana óptima (entre 6 y 24 horas posteriores a la detección: de "
                                                        + alerta.getTsGeneracion().plusHours(6)
                                                                        .format(DateTimeFormatter
                                                                                        .ofPattern("dd/MM/yyyy HH:mm"))
                                                        + " a "
                                                        + alerta.getTsGeneracion().plusHours(24)
                                                                        .format(DateTimeFormatter
                                                                                        .ofPattern("dd/MM/yyyy HH:mm"))
                                                        + ")");

                        alerta.setVentanaInicioIns(alerta.getTsGeneracion().plusHours(6));
                        alerta.setVentanaFinIns(alerta.getTsGeneracion().plusHours(24));

                        alerta = alertaRepository.save(alerta);

                        EstadoReproductivo nuevoEstado = new EstadoReproductivo();
                        nuevoEstado.setAnimal(animal);
                        nuevoEstado.setEstado(TipoReproductivo.POSIBLE_CELO);
                        nuevoEstado.setOrigen(OrigenEstado.AUTOMATICO);
                        nuevoEstado.setFechaRegistro(java.time.LocalDateTime.now());

                        if (animal != null && animal.getEstadosReproductivos() != null
                                        && !animal.getEstadosReproductivos().isEmpty()) {
                                EstadoReproductivo ultimoEstado = animal.getEstadosReproductivos().get(0);
                                nuevoEstado.setFechaUltParto(ultimoEstado.getFechaUltParto());
                                nuevoEstado.setFechaUltInseminacion(ultimoEstado.getFechaUltInseminacion());
                        }

                        estadoReproductivoRepository.save(nuevoEstado);

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

        /** ml-celo no respondió (o falló el procesamiento): se deja la telemetría para reintentar. */
        void marcarInferenciaPendiente(List<Telemetria> telemetriaList, String correlationId, Throwable t) {
                log.error(
                                "Fallo al comunicarse con el microservicio ML de celo (Correlation-ID: {}). Marcando como inferencia pendiente. Motivo: {}",
                                correlationId, t.getMessage());
                telemetriaList.forEach(telemetria -> telemetria
                                .setEstadoInferenciaCelo(EstadoInferencia.INFERENCIA_PENDIENTE));
                telemetriaRepository.saveAll(telemetriaList);
        }

        private List<PuntoTelemetriaDTO> buildRequest(List<Telemetria> telemetriaList) {
                return telemetriaList.stream()
                                .filter(t -> t.getLatitud() != null && t.getLongitud() != null
                                                && t.getMoveState() != null)
                                .map(t -> PuntoTelemetriaDTO.builder()
                                                .moveState(t.getMoveState())
                                                .latitud(t.getLatitud())
                                                .longitud(t.getLongitud())
                                                .build())
                                .collect(Collectors.toList());
        }
}
