package dev.vigia.backend.service.ml;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.dto.ml.PerfilCalibracionRequestDTO;
import dev.vigia.backend.dto.ml.PerfilCalibracionResponseDTO;
import dev.vigia.backend.dto.ml.PuntoTelemetriaDTO;
import dev.vigia.backend.dto.SerieComparativaSnapshot;
import dev.vigia.backend.dto.ml.SaludPredictRequestDTO;
import dev.vigia.backend.dto.ml.SaludPredictResponseDTO;
import dev.vigia.backend.util.SerieComparativaBuilder;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.PerfilReferencia;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.PerfilReferenciaRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Detección de desvíos sanitarios por PERFIL INDIVIDUAL (RF03).
 *
 * A diferencia de {@link CeloDetectorStrategyImpl} (evento puntual,
 * comparado contra la población general, un solo endpoint), este
 * detector trabaja en dos pasos porque el modelo de salud compara a
 * cada animal contra SU PROPIO perfil de referencia:
 *
 * 1. Si el animal todavía no completó los 7 días de calibración (RF03),
 * no se genera ninguna predicción ni alerta -- se marca la
 * telemetría pendiente como procesada y se espera al próximo ciclo.
 * 2. Si el animal ya calibró pero no tiene un perfil persistido (o el
 * que tiene quedó viejo, ver RECALIBRACION_DIAS), se llama a
 * POST /api/internal/ml/salud/perfil UNA vez con la telemetría cruda
 * de calibración, y el resultado (un puñado de números, no la serie
 * completa) se persiste en una entidad PerfilReferencia propia (1:1 con el
 * animal). Esto evita
 * reenviar y recalcular miles de puntos de calibración en cada
 * corrida del batch -- ver la justificación completa en
 * ml-training/salud/features_salud.py.
 * 3. Con un perfil válido y fresco, se llama a
 * POST /api/internal/ml/salud/predict con la ventana de telemetría a
 * evaluar MÁS el perfil ya calculado (no telemetría cruda de
 * calibración).
 */
@Slf4j
@Service
public class SaludDetectorStrategyImpl implements DetectorStrategy {

    private final RestClient restClient;
    private final TelemetriaRepository telemetriaRepository;
    private final AlertaRepository alertaRepository;
    private final PerfilReferenciaRepository perfilReferenciaRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final int calibracionDias;
    private final int recalibracionDias;

    @SuppressWarnings("null")
    public SaludDetectorStrategyImpl(
            RestClient.Builder restClientBuilder,
            @Value("${vigia.ml.salud-url}") String saludUrl,
            TelemetriaRepository telemetriaRepository,
            AlertaRepository alertaRepository,
            PerfilReferenciaRepository perfilReferenciaRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${vigia.salud.calibracion-dias:7}") int calibracionDias,
            @Value("${vigia.salud.recalibracion-dias:30}") int recalibracionDias) {
        this.restClient = restClientBuilder.baseUrl(saludUrl).build();
        this.telemetriaRepository = telemetriaRepository;
        this.alertaRepository = alertaRepository;
        this.perfilReferenciaRepository = perfilReferenciaRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.calibracionDias = calibracionDias;
        this.recalibracionDias = recalibracionDias;
    }

    @Override
    @CircuitBreaker(name = "saludML", fallbackMethod = "fallbackProcess")
    @Retry(name = "saludML", fallbackMethod = "fallbackProcess")
    @SuppressWarnings("null")
    public void process(List<Telemetria> telemetriaList, String correlationId) {
        if (telemetriaList == null || telemetriaList.isEmpty()) {
            return;
        }

        Caravana caravana = telemetriaList.get(0).getCaravana();
        Animal animal = caravana != null ? caravana.getAnimal() : null;
        LocalDateTime ahora = LocalDateTime.now();

        if (animal == null) {
            log.warn("Caravana sin animal asociado, no se puede evaluar salud (correlationId: {}).", correlationId);
            marcarCompletados(telemetriaList);
            return;
        }

        if (alertaRepository.existsByAnimalAndDisparadorAndTsGeneracionAfter(animal,
                DisparadorAlerta.SALUD, ahora.minusHours(24))) {
            log.info("Omitiendo inferencia de salud para animal {} porque ya tiene una alerta reciente (últimas 24hs).",
                    animal.getId());
            marcarCompletados(telemetriaList);
            return;
        }

        LocalDate fechaBase = animal.getFechaAlta();
        if (fechaBase == null) {
            log.warn("Animal {} sin fecha de alta, no se puede calcular su ventana de calibración (RF03).",
                    animal.getId());
            marcarCompletados(telemetriaList);
            return;
        }

        if (animal.getCaravana() != null && animal.getCaravana().getFechaAsignacion() != null) {
            if (animal.getCaravana().getFechaAsignacion().isAfter(fechaBase)) {
                fechaBase = animal.getCaravana().getFechaAsignacion();
            }
        }

        LocalDateTime finCalibracionInicial = fechaBase.atStartOfDay().plusDays(calibracionDias);
        if (ahora.isBefore(finCalibracionInicial)) {
            log.info("Animal {} todavía en período de calibración (RF03, termina {}). No se generan predicciones.",
                    animal.getId(), finCalibracionInicial);
            marcarCompletados(telemetriaList);
            return;
        }

        Optional<PerfilReferencia> perfilExistente = perfilReferenciaRepository.findByAnimal(animal);
        boolean necesitaPerfilNuevo = perfilExistente.isEmpty()
                || perfilExistente.get().getFechaCalculo().isBefore(ahora.minusDays(recalibracionDias));

        PerfilReferencia perfilReferencia;
        if (necesitaPerfilNuevo) {
            perfilReferencia = calcularYPersistirPerfil(animal, perfilExistente, caravana, fechaBase, ahora,
                    correlationId);
            if (perfilReferencia == null) {
                // Todavía no hay suficiente telemetría para armar una
                // ventana de calibración completa (ej. el dispositivo
                // tuvo caídas). Se reintenta en el próximo ciclo del
                // batch, no es un error.
                marcarCompletados(telemetriaList);
                return;
            }
        } else {
            perfilReferencia = perfilExistente.get();
        }

        Map<String, Object> perfil = deserializarPerfil(perfilReferencia.getPerfilJson());

        // ml-salud exige que la ventana a evaluar tenga EXACTAMENTE
        // SAMPLES_PER_DAY (288) puntos -- 24hs a 5 min, ver
        // features_salud.py::compute_features. telemetriaList son TODAS
        // las lecturas de las ultimas 24hs (SaludBatchConfig), que pueden
        // ser mas de 288 si el dispositivo/simulador mando con mas
        // frecuencia que cada 5 min, o menos si todavia no se completo un
        // dia entero. Antes se mandaba telemetriaList tal cual, y
        // /predict fallaba con 503 apenas el conteo se corria de 288 (algo
        // que pasa solo, ya que el loop en vivo sigue sumando puntos por
        // encima de los del historial). Nos quedamos con los 288 mas
        // recientes (telemetriaList viene ordenada ascendente por
        // fechaHora) y si todavia no hay 288, se reintenta en el proximo
        // ciclo en vez de mandar una ventana invalida.
        final int SAMPLES_PER_DAY = 288;
        if (telemetriaList.size() < SAMPLES_PER_DAY) {
            log.info(
                    "Animal {} todavia no tiene una ventana completa de 24hs para evaluar ({} de {} puntos); se reintenta en el proximo ciclo.",
                    animal.getId(), telemetriaList.size(), SAMPLES_PER_DAY);
            marcarCompletados(telemetriaList);
            return;
        }
        List<Telemetria> ventanaEvaluar = telemetriaList.size() > SAMPLES_PER_DAY
                ? telemetriaList.subList(telemetriaList.size() - SAMPLES_PER_DAY, telemetriaList.size())
                : telemetriaList;

        List<PuntoTelemetriaDTO> serie = ventanaEvaluar.stream()
                .map(t -> PuntoTelemetriaDTO.builder()
                        .moveState(t.getMoveState())
                        .latitud(t.getLatitud())
                        .longitud(t.getLongitud())
                        .build())
                .collect(Collectors.toList());

        log.info("Llamando a microservicio de salud (predict) con correlationId: {}", correlationId);
        SaludPredictResponseDTO response = restClient.post()
                .uri("/api/internal/ml/salud/predict")
                .header("X-Correlation-ID", correlationId)
                .body(SaludPredictRequestDTO.builder().serie(serie).perfil(perfil).build())
                .retrieve()
                .body(SaludPredictResponseDTO.class);

        log.info("Respuesta de microservicio de salud recibida: {}", response);
        marcarCompletados(telemetriaList);

        if (response != null && response.isDesvioDetectado()) {
            generarAlerta(animal, response, ventanaEvaluar, perfil, perfilReferencia.getFechaCalculo());
        }
    }

    @SuppressWarnings("null")
    private PerfilReferencia calcularYPersistirPerfil(Animal animal, Optional<PerfilReferencia> perfilExistente,
            Caravana caravana, LocalDate fechaBase, LocalDateTime ahora, String correlationId) {
        boolean esPrimerCalculo = perfilExistente.isEmpty();

        LocalDateTime desde;
        LocalDateTime hasta;
        if (esPrimerCalculo) {
            // Primera calibración: los 7 días originales desde el alta
            // del animal (RF03).
            desde = fechaBase.atStartOfDay();
            hasta = desde.plusDays(calibracionDias);
        } else {
            // Recalibración periódica: los últimos N días "recientes",
            // para que el perfil se adapte a cambios legítimos de
            // comportamiento con el tiempo (ver discusión de deriva del
            // perfil).
            hasta = ahora;
            desde = ahora.minusDays(calibracionDias);
        }

        List<Telemetria> calibracion = telemetriaRepository
                .findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(caravana, desde, hasta);

        if (calibracion.isEmpty()) {
            log.warn(
                    "Animal {} sin telemetría en la ventana de calibración [{}, {}), se reintenta en el próximo ciclo.",
                    animal.getId(), desde, hasta);
            return null;
        }

        List<PuntoTelemetriaDTO> puntos = calibracion.stream()
                .map(t -> PuntoTelemetriaDTO.builder()
                        .moveState(t.getMoveState())
                        .latitud(t.getLatitud())
                        .longitud(t.getLongitud())
                        .build())
                .collect(Collectors.toList());

        log.info("Calculando perfil de salud para animal {} ({} puntos, primera calibración: {}, correlationId: {})",
                animal.getId(), puntos.size(), esPrimerCalculo, correlationId);

        PerfilCalibracionResponseDTO response = restClient.post()
                .uri("/api/internal/ml/salud/perfil")
                .header("X-Correlation-ID", correlationId)
                .body(PerfilCalibracionRequestDTO.builder().calibracion(puntos).build())
                .retrieve()
                .body(PerfilCalibracionResponseDTO.class);

        if (response == null || response.getPerfil() == null) {
            log.warn("ml-salud no devolvió un perfil válido para el animal {} (correlationId: {}).",
                    animal.getId(), correlationId);
            return null;
        }

        try {
            PerfilReferencia perfilReferencia = perfilExistente.orElseGet(PerfilReferencia::new);
            perfilReferencia.setAnimal(animal);
            perfilReferencia.setPerfilJson(objectMapper.writeValueAsString(response.getPerfil()));
            perfilReferencia.setFechaCalculo(ahora);
            return perfilReferenciaRepository.save(perfilReferencia);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el perfil de salud del animal " + animal.getId(), e);
        }
    }

    private Map<String, Object> deserializarPerfil(String perfilJson) {
        try {
            return objectMapper.readValue(perfilJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Perfil de salud persistido inválido/corrupto", e);
        }
    }

    private void generarAlerta(Animal animal, SaludPredictResponseDTO response, List<Telemetria> ventanaEvaluar,
            Map<String, Object> perfil, LocalDateTime perfilCalculadoEn) {
        SeveridadAlerta severidad = mapearSeveridad(response.getSeveridad());
        if (severidad == null) {
            // severidad 0 = sin desvío, response.desvioDetectado() ya
            // filtra esto, pero se deja la guarda por las dudas ante
            // una respuesta inconsistente del microservicio.
            return;
        }

        log.info("¡Desvío sanitario detectado para animal {}! severidad={} confianza={}",
                animal.getId(), response.getSeveridad(), response.getConfianzaModelo());

        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setTipo("SALUD");
        alerta.setSeveridad(severidad);
        alerta.setTsGeneracion(LocalDateTime.now());
        alerta.setDisparador(DisparadorAlerta.SALUD);
        alerta.setValorUmbral(0.5);
        alerta.setValorDetectado((double) response.getConfianzaModelo());
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setConfianzaModelo(response.getConfianzaModelo());
        alerta.setDesvioDetectado(true);
        alerta.setPosibleCausa(response.getPosibleCausa());
        alerta.setSerieComparativaJson(serializarSerieComparativa(animal, ventanaEvaluar, perfil, perfilCalculadoEn,
                severidad.name(), response.getPosibleCausa()));

        alerta = alertaRepository.save(alerta);

        AlertaEventDTO eventDTO = AlertaEventDTO.builder()
                .id(alerta.getId())
                .animalId(animal.getId())
                .tipo(alerta.getTipo())
                .severidad(alerta.getSeveridad())
                .tsGeneracion(alerta.getTsGeneracion())
                .disparador(alerta.getDisparador())
                .valorUmbral(alerta.getValorUmbral())
                .valorDetectado(alerta.getValorDetectado())
                .estado(alerta.getEstado())
                .confianzaModelo(alerta.getConfianzaModelo())
                .desvioDetectado(alerta.getDesvioDetectado())
                .posibleCausa(alerta.getPosibleCausa())
                .build();

        rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", eventDTO);
        log.info("Alerta de salud publicada en RabbitMQ con ID: {}", alerta.getId());
    }

    private String serializarSerieComparativa(Animal animal, List<Telemetria> ventanaEvaluar,
            Map<String, Object> perfil, LocalDateTime perfilCalculadoEn, String severidad, String posibleCausa) {
        try {
            SerieComparativaSnapshot snapshot = SerieComparativaBuilder.build(
                    ventanaEvaluar, perfil, perfilCalculadoEn, severidad, posibleCausa);
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("No se pudo serializar la serie comparativa para el animal {}: {}",
                    animal.getId(), e.getMessage());
            return null;
        }
    }

    private SeveridadAlerta mapearSeveridad(int severidad) {
        switch (severidad) {
            case 1:
                return SeveridadAlerta.BAJA;
            case 2:
                return SeveridadAlerta.MEDIA;
            case 3:
                return SeveridadAlerta.ALTA;
            default:
                return null;
        }
    }

    private void marcarCompletados(List<Telemetria> telemetriaList) {
        // Toca estadoInferenciaSalud (propio de este pipeline), no
        // estadoInferenciaCelo (propio de celo) -- ver comentario en
        // Telemetria.java sobre por que estan separados.
        List<Telemetria> pendientes = telemetriaList.stream()
                .filter(t -> t.getEstadoInferenciaSalud() == EstadoInferencia.PENDIENTE)
                .collect(Collectors.toList());
        pendientes.forEach(t -> t.setEstadoInferenciaSalud(EstadoInferencia.COMPLETADO));
        telemetriaRepository.saveAll(pendientes);
    }

    public void fallbackProcess(List<Telemetria> telemetriaList, String correlationId, Throwable t) {
        log.error(
                "Fallo al comunicarse con el microservicio ML de salud (Correlation-ID: {}). Marcando como inferencia pendiente. Motivo: {}",
                correlationId, t.getMessage());
        telemetriaList.forEach(telemetria -> telemetria.setEstadoInferenciaSalud(EstadoInferencia.INFERENCIA_PENDIENTE));
        telemetriaRepository.saveAll(telemetriaList);
    }
}
