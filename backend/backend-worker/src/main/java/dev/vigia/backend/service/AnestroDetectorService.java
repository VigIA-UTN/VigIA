package dev.vigia.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnestroDetectorService {

    private final EstablecimientoRepository establecimientoRepository;
    private final AnimalRepository animalRepository;
    private final AlertaRepository alertaRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(cron = "${vigia.batch.anestro-cron:0 0 2 * * *}") // Por defecto, corre a las 2 AM
    @Transactional
    public void detectarAnestro() {
        log.info("Iniciando detección programada de Anestro");
        List<Establecimiento> establecimientos = establecimientoRepository.findAll();
        for (Establecimiento est : establecimientos) {
            if (!est.isActivo() || est.getDiasDeteccionAnestro() == null) {
                continue;
            }

            int diasUmbral = est.getDiasDeteccionAnestro();
            List<Animal> animales = animalRepository.findByEstablecimientoIdInAndEstadoActualNot(java.util.Collections.singletonList(est.getId()), EstadoAnimal.INACTIVO);

            for (Animal animal : animales) {
                if (animal.getFechaNacimiento() != null && ChronoUnit.DAYS.between(animal.getFechaNacimiento(), LocalDate.now()) < 365) {
                    continue; // No evaluar anestro en animales menores a 1 año
                }

                // Obtener el último estado reproductivo
                List<EstadoReproductivo> estados = animal.getEstadosReproductivos();
                EstadoReproductivo ultimoEstado = (estados != null && !estados.isEmpty()) ? estados.get(0) : null;

                if (ultimoEstado != null && (ultimoEstado.getEstado() == TipoReproductivo.GESTANTE || ultimoEstado.getEstado() == TipoReproductivo.INSEMINADA)) {
                    continue; // Gestantes e inseminadas no ciclan/no se evalúan para anestro
                }

                if (ultimoEstado != null && ultimoEstado.getEstado() == TipoReproductivo.ANESTRO) {
                    // Ya está en anestro, podemos omitir o regenerar alerta si la anterior se archivó?
                    // Asumimos que si ya está en estado ANESTRO, ya se emitió la alerta en su momento.
                    // Para evitar spamear, validamos si tiene una alerta pendiente.
                    continue;
                }

                LocalDate fechaReferencia = null;

                if (ultimoEstado != null) {
                    if (ultimoEstado.getFechaUltParto() != null) {
                        fechaReferencia = ultimoEstado.getFechaUltParto();
                    } else if (ultimoEstado.getFechaUltInseminacion() != null) {
                        fechaReferencia = ultimoEstado.getFechaUltInseminacion();
                    } else {
                        fechaReferencia = ultimoEstado.getFechaRegistro().toLocalDate();
                    }
                } else {
                    // Si no tiene registros, usar la fecha de nacimiento + 1 año, o fecha de alta
                    if (animal.getFechaNacimiento() != null) {
                        fechaReferencia = animal.getFechaNacimiento().plusYears(1);
                    } else if (animal.getFechaAlta() != null) {
                        fechaReferencia = animal.getFechaAlta();
                    } else {
                        fechaReferencia = LocalDate.now();
                    }
                }

                long diasSinCiclo = ChronoUnit.DAYS.between(fechaReferencia, LocalDate.now());

                if (diasSinCiclo >= diasUmbral) {
                    // Verificar si ya tiene una alerta de anestro pendiente
                    boolean tieneAlertaPendiente = false;
                    List<Alerta> alertasActivas = alertaRepository.alertasAnimalActivas(animal.getId());
                    for (Alerta a : alertasActivas) {
                        if ("Anestro Detectado".equals(a.getTipo())) {
                            tieneAlertaPendiente = true;
                            break;
                        }
                    }

                    if (!tieneAlertaPendiente) {
                        generarAlertaAnestro(animal, est, diasSinCiclo);
                    }
                }
            }
        }
        log.info("Detección programada de Anestro finalizada");
    }

    private void generarAlertaAnestro(Animal animal, Establecimiento est, long diasSinCiclo) {
        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setTipo("Anestro Detectado");
        alerta.setSeveridad(est.getSeveridadAnestro());
        alerta.setTsGeneracion(LocalDateTime.now());
        alerta.setDisparador(DisparadorAlerta.CELO);
        alerta.setValorUmbral((double) est.getDiasDeteccionAnestro());
        alerta.setValorDetectado((double) diasSinCiclo);
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        
        alerta.setAccionRecomendada("Se requiere revisión veterinaria inmediata. El animal lleva " + diasSinCiclo + " días sin ciclo registrado.");
        alerta.setPosibleCausa("Anestro prolongado (>" + est.getDiasDeteccionAnestro() + " días)");

        alerta = alertaRepository.save(alerta);

        // Publicar evento en RabbitMQ
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
                .build();

        rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", eventDTO);
        log.info("Alerta de anestro generada para el animal {} ({} días sin ciclo)", animal.getId(), diasSinCiclo);
    }
}
