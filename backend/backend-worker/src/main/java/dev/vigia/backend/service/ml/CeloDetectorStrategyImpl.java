package dev.vigia.backend.service.ml;

import dev.vigia.backend.mensajeria.PublicadorDeAlertas;
import dev.vigia.backend.client.MlCeloClient;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static dev.vigia.backend.service.ml.VentanaTelemetria.SAMPLES_PER_DAY;
import static dev.vigia.backend.service.ml.VentanaTelemetria.puntosParaMl;

@Slf4j
@Service
public class CeloDetectorStrategyImpl implements DetectorStrategy {

        private final MlCeloClient mlCeloClient;
        private final TelemetriaRepository telemetriaRepository;
        private final AlertaRepository alertaRepository;
        private final EstadoReproductivoRepository estadoReproductivoRepository;
        private final PublicadorDeAlertas publicadorDeAlertas;
        private final double coberturaMinima;
        private final int huecoMaximoMinutos;

        public CeloDetectorStrategyImpl(
                        MlCeloClient mlCeloClient,
                        TelemetriaRepository telemetriaRepository,
                        AlertaRepository alertaRepository,
                        EstadoReproductivoRepository estadoReproductivoRepository,
                        PublicadorDeAlertas publicadorDeAlertas,
                        @Value("${vigia.celo.cobertura-minima:0.9}") double coberturaMinima,
                        @Value("${vigia.celo.hueco-maximo-minutos:60}") int huecoMaximoMinutos) {
                this.mlCeloClient = mlCeloClient;
                this.telemetriaRepository = telemetriaRepository;
                this.alertaRepository = alertaRepository;
                this.estadoReproductivoRepository = estadoReproductivoRepository;
                this.publicadorDeAlertas = publicadorDeAlertas;
                this.coberturaMinima = coberturaMinima;
                this.huecoMaximoMinutos = huecoMaximoMinutos;
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
                if (animal != null && alertaRepository.existsByAnimalAndTipoAndTsGeneracionAfter(animal,
                                Alerta.TIPO_CELO, java.time.LocalDateTime.now().minusHours(24))) {
                        log.info("Omitiendo inferencia de celo para animal {} porque ya tiene una alerta reciente de celo (últimas 24hs).",
                                        animal.getId());
                        marcarCompletados(telemetriaList);
                        return;
                }

                // La LSTM de ml-celo se entrenó con ventanas de SAMPLES_PER_DAY (288) puntos, 24hs a 5 min, y
                // ml-celo rechaza otro largo con 422. Mismo criterio que salud: telemetriaList son todas las
                // lecturas de las ultimas 24hs (CeloBatchConfig), ordenadas por fechaHora; con mas de 288 van
                // las 288 mas recientes y con menos (uplinks perdidos) puntosParaMl rellena los huecos o, si
                // faltan demasiados, se reintenta con la proxima lectura.
                List<PuntoTelemetriaDTO> serie = puntosParaMl(telemetriaList, LocalDateTime.now().minusHours(24),
                                SAMPLES_PER_DAY, coberturaMinima, huecoMaximoMinutos);
                if (serie.isEmpty()) {
                        log.info(
                                        "Animal {} todavia no tiene una ventana completa de 24hs para evaluar celo ({} lecturas; se exige el {} % de los slots de 5 min y ningun hueco de mas de {} min); se reintenta con la proxima lectura.",
                                        animal != null ? animal.getId() : null, telemetriaList.size(),
                                        Math.round(coberturaMinima * 100), huecoMaximoMinutos);
                        marcarCompletados(telemetriaList);
                        return;
                }
                if (serie.size() > SAMPLES_PER_DAY) {
                        serie = serie.subList(serie.size() - SAMPLES_PER_DAY, serie.size());
                }

                CeloPredictResponseDTO response = mlCeloClient.predict(serie, correlationId);

                log.info("Respuesta de microservicio de celo recibida: {}", response);

                marcarCompletados(telemetriaList);

                if (response != null && response.isEventoCelo()) {
                        log.info("¡Evento de celo detectado con confianza {}!", response.getConfianzaModelo());

                        Alerta alerta = new Alerta();
                        alerta.setAnimal(animal);
                        alerta.setTipo(Alerta.TIPO_CELO);
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

                        publicadorDeAlertas.publicar(eventDTO);
                        log.info("Alerta de celo {} generada; se publica al commitear", alerta.getId());
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

        /** Solo las lecturas que estaban pendientes: las demás ya se evaluaron en otro ciclo. */
        private void marcarCompletados(List<Telemetria> telemetriaList) {
                List<Telemetria> pendientes = telemetriaList.stream()
                                .filter(t -> t.getEstadoInferenciaCelo() == EstadoInferencia.PENDIENTE)
                                .collect(Collectors.toList());
                pendientes.forEach(t -> t.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO));
                telemetriaRepository.saveAll(pendientes);
        }
}
