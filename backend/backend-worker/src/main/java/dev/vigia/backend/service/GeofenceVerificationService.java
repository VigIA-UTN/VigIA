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
            // Animal sin lote asignado, no posee geocercas activas configuradas
            return;
        }

        // 1. Verificar si el lote tiene perímetros activos asignados
        List<Perimetro> perimetrosActivos = perimetroRepository.findPerimetrosActivosByLoteId(lote.getId());
        if (perimetrosActivos.isEmpty()) {
            return;
        }

        // 2. Ejecutar chequeo espacial ST_Contains optimizado en DB (Point-in-polygon)
        List<Perimetro> perimetrosContenedores = perimetroRepository.findPerimetrosQueContienen(
                lote.getId(), latitud, longitud);

        // Si el punto está contenido en al menos uno de los perímetros activos del lote, está dentro
        if (!perimetrosContenedores.isEmpty()) {
            log.debug("Animal {} en posición ({}, {}) se encuentra dentro del perímetro del lote {}",
                    animal.getId(), latitud, longitud, lote.getId());
            return;
        }

        // 3. Animal FUERA de perímetro. Aplicar política anti-flooding / cooldown
        LocalDateTime limiteCooldown = LocalDateTime.now().minusHours(cooldownHours);
        boolean tieneAlertaReciente = alertaRepository.existsByAnimalAndDisparadorAndTsGeneracionAfter(
                animal, DisparadorAlerta.FUERA_DE_PERIMETRO, limiteCooldown);

        if (tieneAlertaReciente) {
            log.info("Animal {} está fuera de perímetro pero ya posee una alerta reciente de salida (últimas {}hs). Se omite duplicado.",
                    animal.getId(), cooldownHours);
            return;
        }

        // 4. Crear Alerta de salida de perímetro
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
        alerta.setDisparador(DisparadorAlerta.FUERA_DE_PERIMETRO);
        alerta.setValorUmbral(1.0);
        alerta.setValorDetectado(0.0);
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setAccionRecomendada("Verificar ubicación del animal en el mapa y retornarlo al perímetro autorizado.");
        alerta.setPosibleCausa("Animal fuera del perímetro asignado para el lote " + lote.getNombre());

        alerta = alertaRepository.save(alerta);

        // 5. Publicar evento en RabbitMQ para notificaciones y websockets SSE
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
                .build();

        rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", eventDTO);
        log.info("Alerta de salida de perímetro emitida con ID: {} para animal {}", alerta.getId(), animal.getId());
    }
}
