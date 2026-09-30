package dev.vigia.backend.service;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.PerimetroRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeofenceVerificationService {

    private final PerimetroRepository perimetroRepository;
    private final AlertaRepository alertaRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${vigia.geofence.cooldown-hours:6}")
    private int cooldownHours = 6;

    @Transactional
    public void verificarPosicion(Animal animal, Double latitud, Double longitud, LocalDateTime fechaHora) {
        if (animal == null || latitud == null || longitud == null) {
            return;
        }

        RodeoLote lote = animal.getRodeoLote();
        if (lote == null) {
            return;
        }

        List<Perimetro> perimetrosActivos = perimetroRepository.findPerimetrosActivosByLoteId(lote.getId());
        if (perimetrosActivos.isEmpty()) {
            return;
        }

        if (estaDentroDeAlgunPerimetro(perimetrosActivos, lote.getId(), latitud, longitud)) {
            log.debug("Animal {} en posición ({}, {}) se encuentra dentro del perímetro (o su tolerancia) del lote {}",
                    animal.getId(), latitud, longitud, lote.getId());
            return;
        }

        LocalDateTime limiteCooldown = LocalDateTime.now().minusHours(cooldownHours);
        Optional<Alerta> alertaReciente = alertaRepository
                .findFirstByAnimalAndDisparadorAndTsGeneracionAfterOrderByTsGeneracionDesc(
                        animal, DisparadorAlerta.FUERA_DE_PERIMETRO, limiteCooldown);

        if (alertaReciente.isPresent()) {
            Alerta activa = alertaReciente.get();
            if (activa.getEstado() == EstadoAlerta.PENDIENTE) {
                activa.setLatitud(latitud);
                activa.setLongitud(longitud);
                alertaRepository.save(activa);
                log.info("Animal {} sigue fuera de perímetro. Se actualiza la posición de la alerta {}.",
                        animal.getId(), activa.getId());
            } else {
                log.info(
                        "Animal {} está fuera de perímetro pero ya posee una alerta reciente de salida (últimas {}hs). Se omite duplicado.",
                        animal.getId(), cooldownHours);
            }
            return;
        }

        log.warn("¡ALERTA! Animal {} (Caravana: {}) fuera del perímetro asignado para el lote '{}'",
                animal.getId(),
                animal.getCaravana() != null ? animal.getCaravana().getCodigo() : "N/A",
                lote.getNombre());

        LocalDateTime tsAlerta = fechaHora != null ? fechaHora : LocalDateTime.now();

        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setTipo("Fuera de Perímetro");
        alerta.setSeveridad(SeveridadAlerta.ALTA);
        alerta.setTsGeneracion(tsAlerta);
        alerta.setLatitud(latitud);
        alerta.setLongitud(longitud);
        alerta.setDisparador(DisparadorAlerta.FUERA_DE_PERIMETRO);
        alerta.setValorUmbral(1.0);
        alerta.setValorDetectado(0.0);
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setAccionRecomendada("Verificar ubicación del animal en el mapa y retornarlo al perímetro autorizado.");
        alerta.setPosibleCausa("Animal fuera del perímetro asignado para el lote " + lote.getNombre());

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
                .accionRecomendada(alerta.getAccionRecomendada())
                .posibleCausa(alerta.getPosibleCausa())
                .latitud(alerta.getLatitud())
                .longitud(alerta.getLongitud())
                .build();

        rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", eventDTO);
        log.info("Alerta de salida de perímetro emitida con ID: {} para animal {}", alerta.getId(), animal.getId());
    }

    @SuppressWarnings("null")
    private boolean estaDentroDeAlgunPerimetro(List<Perimetro> perimetrosActivos, Long loteId,
            double latitud, double longitud) {
        Map<Long, Perimetro> perimetrosPorId = perimetrosActivos.stream()
                .collect(Collectors.toMap(Perimetro::getId, Function.identity(), (a, b) -> a));

        return perimetroRepository.calcularDistanciasAPerimetrosActivos(loteId, latitud, longitud).stream()
                .anyMatch(d -> {
                    Perimetro perimetro = perimetrosPorId.get(d.getPerimetroId());
                    return perimetro != null && d.getDistanciaMetros() != null
                            && perimetro.admiteDistancia(d.getDistanciaMetros());
                });
    }
}
