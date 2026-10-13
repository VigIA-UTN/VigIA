package dev.vigia.backend.service.ml;

import dev.vigia.backend.client.MlGeoClient;
import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.dto.ml.geo.AlertaAnimalDTO;
import dev.vigia.backend.dto.ml.geo.CoordenadaGPSDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.RegistroCohesionRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cohesión y aislamiento social (RF de ml-geo). Recibe la telemetría de la
 * ventana de un establecimiento y, por cada rodeo (los animales sin rodeo forman
 * su propio grupo), le pide a ml-geo el índice de cohesión de cada animal usando
 * el umbral de aislamiento del establecimiento. Guarda un {@link RegistroCohesion}
 * por animal y genera una alerta AISLAMIENTO_SOCIAL para los que quedaron
 * aislados del grupo.
 */
@Slf4j
@Service
public class GeoDetectorStrategyImpl implements DetectorStrategy {

    static final double UMBRAL_AISLAMIENTO_DEFAULT = 100.0;

    private final MlGeoClient mlGeoClient;
    private final TelemetriaRepository telemetriaRepository;
    private final AlertaRepository alertaRepository;
    private final AnimalRepository animalRepository;
    private final RegistroCohesionRepository registroCohesionRepository;
    private final RabbitTemplate rabbitTemplate;
    private final int cooldownHoras;

    public GeoDetectorStrategyImpl(
            MlGeoClient mlGeoClient,
            TelemetriaRepository telemetriaRepository,
            AlertaRepository alertaRepository,
            AnimalRepository animalRepository,
            RegistroCohesionRepository registroCohesionRepository,
            RabbitTemplate rabbitTemplate,
            @Value("${vigia.geo.cooldown-horas:6}") int cooldownHoras) {
        this.mlGeoClient = mlGeoClient;
        this.telemetriaRepository = telemetriaRepository;
        this.alertaRepository = alertaRepository;
        this.animalRepository = animalRepository;
        this.registroCohesionRepository = registroCohesionRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.cooldownHoras = cooldownHoras;
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

    private void procesar(List<Telemetria> telemetriaList, String correlationId) {
        List<Telemetria> telemetriasValidas = telemetriaList.stream()
                .filter(t -> t.getLatitud() != null && t.getLongitud() != null
                        && t.getCaravana() != null && t.getCaravana().getAnimal() != null)
                .collect(Collectors.toList());

        if (telemetriasValidas.isEmpty()) {
            log.info("No hay telemetría válida con coordenadas GPS para análisis geoespacial (correlationId: {}).",
                    correlationId);
            marcarCompletados(telemetriaList);
            return;
        }

        Establecimiento establecimiento = resolverEstablecimiento(telemetriasValidas);
        double umbral = establecimiento != null && establecimiento.getUmbralAislamientoM() != null
                ? establecimiento.getUmbralAislamientoM()
                : UMBRAL_AISLAMIENTO_DEFAULT;

        // La cohesión se mide contra el propio rodeo: con un centroide único por
        // establecimiento, un rodeo entero en otro potrero quedaría "aislado".
        Map<Optional<Long>, List<Telemetria>> porRodeo = telemetriasValidas.stream()
                .collect(Collectors.groupingBy(
                        t -> Optional.ofNullable(t.getCaravana().getAnimal().getRodeoLote()).map(RodeoLote::getId),
                        LinkedHashMap::new, Collectors.toList()));

        LocalDateTime ahora = LocalDateTime.now();
        for (List<Telemetria> grupo : porRodeo.values()) {
            analizarGrupo(grupo, establecimiento, umbral, ahora, correlationId);
        }

        marcarCompletados(telemetriaList);
    }

    private void analizarGrupo(List<Telemetria> grupo, Establecimiento establecimiento, double umbral,
            LocalDateTime ahora, String correlationId) {
        List<CoordenadaGPSDTO> coordenadas = grupo.stream()
                .map(t -> CoordenadaGPSDTO.builder()
                        .animalId(t.getCaravana().getAnimal().getId().toString())
                        .lat(t.getLatitud())
                        .lng(t.getLongitud())
                        .timestamp(t.getFechaHora().toString())
                        .build())
                .collect(Collectors.toList());

        GeoAnalyzeRequestDTO request = GeoAnalyzeRequestDTO.builder()
                .historial7d(coordenadas)
                .umbralAislamientoM(umbral)
                .build();

        GeoAnalyzeResponseDTO response = mlGeoClient.analyze(request, correlationId);
        if (response == null || response.getAlertas() == null) {
            return;
        }

        Map<Long, Animal> animales = grupo.stream()
                .map(t -> t.getCaravana().getAnimal())
                .collect(Collectors.toMap(Animal::getId, Function.identity(), (a, b) -> a));

        for (AlertaAnimalDTO alertaAnimal : response.getAlertas()) {
            Long animalId;
            try {
                animalId = Long.parseLong(alertaAnimal.getAnimalId());
            } catch (NumberFormatException e) {
                log.warn("ID de animal no numérico devuelto por ml-geo: {}", alertaAnimal.getAnimalId());
                continue;
            }
            Animal animal = animales.containsKey(animalId)
                    ? animales.get(animalId)
                    : animalRepository.findById(animalId).orElse(null);
            if (animal == null) {
                log.warn("Animal con ID {} no encontrado al procesar el análisis de cohesión.", animalId);
                continue;
            }

            if (alertaAnimal.getIndiceCohesion() != null && establecimiento != null) {
                registroCohesionRepository.save(RegistroCohesion.builder()
                        .animal(animal)
                        .establecimiento(establecimiento)
                        .fecha(ahora)
                        .indiceCohesion(alertaAnimal.getIndiceCohesion())
                        .tiempoFueraGrupoS(alertaAnimal.getTiempoFueraGrupoS() != null
                                ? alertaAnimal.getTiempoFueraGrupoS().longValue()
                                : 0L)
                        .distanciaCentroideM(alertaAnimal.getDistanciaCentroideM())
                        .build());
            }

            if (Boolean.TRUE.equals(alertaAnimal.getAlertaAislamiento())) {
                Telemetria ultima = grupo.stream()
                        .filter(t -> t.getCaravana().getAnimal().getId().equals(animalId))
                        .max(Comparator.comparing(Telemetria::getFechaHora))
                        .orElse(null);
                procesarAlertaAislamiento(animal, alertaAnimal, ultima, umbral);
            }
        }
    }

    private Establecimiento resolverEstablecimiento(List<Telemetria> telemetrias) {
        for (Telemetria t : telemetrias) {
            if (t.getCaravana().getEstablecimiento() != null) {
                return t.getCaravana().getEstablecimiento();
            }
            if (t.getCaravana().getAnimal().getEstablecimiento() != null) {
                return t.getCaravana().getAnimal().getEstablecimiento();
            }
        }
        return null;
    }

    @SuppressWarnings("null")
    private void procesarAlertaAislamiento(Animal animal, AlertaAnimalDTO alertaAnimal, Telemetria ultima,
            double umbral) {
        Double latitud = ultima != null ? ultima.getLatitud() : null;
        Double longitud = ultima != null ? ultima.getLongitud() : null;
        Double distancia = alertaAnimal.getDistanciaCentroideM();

        // Sigue aislado y la alerta sigue abierta: se actualiza su posición en vez de duplicarla.
        Optional<Alerta> pendiente = alertaRepository.alertasAnimalActivas(animal.getId()).stream()
                .filter(a -> a.getDisparador() == DisparadorAlerta.AISLAMIENTO_SOCIAL)
                .findFirst();
        if (pendiente.isPresent()) {
            Alerta activa = pendiente.get();
            activa.setLatitud(latitud);
            activa.setLongitud(longitud);
            activa.setValorDetectado(distancia != null ? distancia : umbral);
            alertaRepository.save(activa);
            log.info("Animal {} sigue aislado del rodeo. Se actualiza la alerta {}.", animal.getId(), activa.getId());
            return;
        }

        // Recién confirmada o descartada: no se vuelve a alertar dentro del cooldown.
        if (alertaRepository.findFirstByAnimalAndDisparadorAndTsGeneracionAfterOrderByTsGeneracionDesc(
                animal, DisparadorAlerta.AISLAMIENTO_SOCIAL, LocalDateTime.now().minusHours(cooldownHoras))
                .isPresent()) {
            log.info("Animal {} aislado pero con una alerta de aislamiento resuelta en las últimas {} hs. Se omite.",
                    animal.getId(), cooldownHoras);
            return;
        }

        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setDisparador(DisparadorAlerta.AISLAMIENTO_SOCIAL);
        alerta.setTipo("AISLAMIENTO_SOCIAL");
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setSeveridad(SeveridadAlerta.ALTA);
        alerta.setTsGeneracion(LocalDateTime.now());
        alerta.setLatitud(latitud);
        alerta.setLongitud(longitud);
        alerta.setValorUmbral(umbral);
        alerta.setValorDetectado(distancia != null ? distancia : umbral);
        alerta.setPosibleCausa("Animal aislado del rodeo a "
                + Math.round(distancia != null ? distancia : 0.0) + " metros del centroide del grupo");
        alerta.setAccionRecomendada("Recorrer la zona y verificar el estado de los animales alejados del rodeo.");

        alerta = alertaRepository.save(alerta);
        log.info("Generada alerta AISLAMIENTO_SOCIAL {} para animal {}", alerta.getId(), animal.getId());

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
                .accionRecomendada(alerta.getAccionRecomendada())
                .posibleCausa(alerta.getPosibleCausa())
                .latitud(alerta.getLatitud())
                .longitud(alerta.getLongitud())
                .build();
        rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", eventDTO);
    }

    private void marcarCompletados(List<Telemetria> telemetriaList) {
        List<Telemetria> pendientes = telemetriaList.stream()
                .filter(t -> t.getEstadoInferenciaGeo() == EstadoInferencia.PENDIENTE)
                .collect(Collectors.toList());
        if (!pendientes.isEmpty()) {
            pendientes.forEach(t -> t.setEstadoInferenciaGeo(EstadoInferencia.COMPLETADO));
            telemetriaRepository.saveAll(pendientes);
        }
    }

    /** ml-geo no respondió (o falló el procesamiento): se deja la telemetría para reintentar. */
    void marcarInferenciaPendiente(List<Telemetria> telemetriaList, String correlationId, Throwable t) {
        log.error("Fallo en el análisis geoespacial (Correlation-ID: {}). Marcando como inferencia pendiente. Motivo: {}",
                correlationId, t.getMessage());
        List<Telemetria> pendientes = telemetriaList.stream()
                .filter(t1 -> t1.getEstadoInferenciaGeo() == EstadoInferencia.PENDIENTE)
                .collect(Collectors.toList());
        if (!pendientes.isEmpty()) {
            pendientes.forEach(t1 -> t1.setEstadoInferenciaGeo(EstadoInferencia.INFERENCIA_PENDIENTE));
            telemetriaRepository.saveAll(pendientes);
        }
    }
}
