package dev.vigia.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertaArchivadorService {

    private final AlertaRepository alertaRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedRateString = "3600000") // 1 hora
    @Transactional
    public void archivarAlertasVencidas() {
        log.info("Iniciando job de archivado de alertas de celo vencidas");
        LocalDateTime now = LocalDateTime.now();
        List<Alerta> vencidas = alertaRepository.findAlertasCeloPendientesVencidas(now);

        if (vencidas.isEmpty()) {
            log.info("No se encontraron alertas de celo pendientes y vencidas para archivar.");
            return;
        }

        log.info("Encontradas {} alertas para archivar.", vencidas.size());
        for (Alerta alerta : vencidas) {
            alerta.setEstado(EstadoAlerta.ARCHIVADA);
            alerta.setMotivoDescarte("oportunidad_no_aprovechada");
            alerta.setTsConfirmacion(now);
            log.debug("Archivando alerta ID {}", alerta.getId());
            
            AlertaEventDTO eventDTO = AlertaEventDTO.builder()
                .id(alerta.getId())
                .animalId(alerta.getAnimal().getId())
                .estado(alerta.getEstado())
                .tipo(alerta.getTipo())
                .tsGeneracion(alerta.getTsGeneracion())
                .build();
            rabbitTemplate.convertAndSend("alertas.exchange", "alerta.update", eventDTO);
        }
        alertaRepository.saveAll(vencidas);
        log.info("Archivado de alertas finalizado exitosamente.");
    }
}
