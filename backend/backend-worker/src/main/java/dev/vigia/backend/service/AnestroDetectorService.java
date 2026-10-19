package dev.vigia.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.vigia.backend.mensajeria.PublicadorDeAlertas;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AnestroDetectorService {

    private final EstablecimientoRepository establecimientoRepository;
    private final AnimalRepository animalRepository;
    private final AlertaRepository alertaRepository;
    private final PublicadorDeAlertas publicadorDeAlertas;
    private final PlatformTransactionManager transactionManager;

    private static final Set<TipoReproductivo> ESTADOS_DE_CICLO = EnumSet.of(
            TipoReproductivo.CELO, TipoReproductivo.POSIBLE_CELO, TipoReproductivo.INSEMINADA, TipoReproductivo.GESTANTE,
            TipoReproductivo.PARIDA);

    // Días sin volver a alertar tras descartar un anestro.
    @Value("${vigia.batch.anestro-silencio-dias:21}")
    private int silencioDias;

    // Por defecto todos los días a las 2 AM de Argentina, sea cual sea la zona horaria del contenedor: el umbral se
    // cuenta en días, correrlo más seguido no adelanta nada. El compose local lo pone cada minuto para la demo.
    @Scheduled(cron = "${vigia.batch.anestro-cron:0 0 2 * * *}", zone = "America/Argentina/Buenos_Aires")
    public void detectarAnestro() {
        log.info("Iniciando detección programada de Anestro");
        // Una transacción por establecimiento: el contexto de persistencia guarda los animales de uno solo, no los de
        // todos (con 25.000 en una sola transacción la corrida no terminaba: OutOfMemoryError a los 12 minutos), y
        // si uno falla se deshace solo ese y la corrida sigue con el resto.
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
        for (Establecimiento est : establecimientoRepository.findAll()) {
            if (!est.isActivo() || est.getDiasDeteccionAnestro() == null) {
                continue;
            }
            try {
                transaccion.executeWithoutResult(estado -> detectarEn(est));
            } catch (RuntimeException e) {
                log.error("Detección de anestro fallida en el establecimiento {}; sigue con el resto", est.getId(), e);
            }
        }
        log.info("Detección programada de Anestro finalizada");
    }

    private void detectarEn(Establecimiento est) {
        int diasUmbral = est.getDiasDeteccionAnestro();
        List<Animal> animales = animalRepository.findConEstadosByEstablecimientoIdAndEstadoActualNot(est.getId(),
                EstadoAnimal.INACTIVO);
        // Las alertas de anestro de todo el establecimiento en una consulta, no una por animal. Vienen de la más
        // nueva a la más vieja: la primera de cada animal es su última.
        Map<Long, Alerta> ultimaAlertaPorAnimal = new HashMap<>();
        for (Alerta alerta : alertaRepository.findByAnimal_Establecimiento_IdAndTipoOrderByTsGeneracionDesc(est.getId(),
                Alerta.TIPO_ANESTRO)) {
            ultimaAlertaPorAnimal.putIfAbsent(alerta.getAnimal().getId(), alerta);
        }

        for (Animal animal : animales) {
            if (animal.getFechaNacimiento() != null && ChronoUnit.DAYS.between(animal.getFechaNacimiento(), LocalDate.now()) < 365) {
                continue; // No evaluar anestro en animales menores a 1 año
            }

            // Obtener el último estado reproductivo
            List<EstadoReproductivo> estados = animal.getEstadosReproductivos();
            EstadoReproductivo ultimoEstado = (estados != null && !estados.isEmpty()) ? estados.get(0) : null;

            if (ultimoEstado != null && ultimoEstado.getEstado() == TipoReproductivo.ANESTRO) {
                continue; // Anestro ya confirmado: no se re-alerta hasta que registren otro estado
            }

            // Gestantes e inseminadas no ciclan: no califican para anestro
            boolean gestanteOInseminada = ultimoEstado != null
                    && (ultimoEstado.getEstado() == TipoReproductivo.GESTANTE || ultimoEstado.getEstado() == TipoReproductivo.INSEMINADA);
            long diasSinCiclo = gestanteOInseminada ? 0
                    : ChronoUnit.DAYS.between(fechaReferencia(animal, estados), LocalDate.now());
            Optional<Alerta> ultimaAlerta = Optional.ofNullable(ultimaAlertaPorAnimal.get(animal.getId()));

            if (diasSinCiclo < diasUmbral) {
                // Ya no califica (registraron celo, inseminación, preñez...): se cierra el anestro pendiente
                ultimaAlerta.filter(a -> a.getEstado() == EstadoAlerta.PENDIENTE).ifPresent(this::archivarPorCiclo);
            } else if (!silenciada(ultimaAlerta)) {
                generarAlertaAnestro(animal, est, diasSinCiclo);
            }
        }
    }

    /**
     * Última evidencia de que el animal cicla, en todo el historial: parto, inseminación o un estado
     * CELO/POSIBLE_CELO/INSEMINADA/GESTANTE/PARIDA. Sin evidencia, cae al último registro, nacimiento + 1 año o alta.
     */
    private LocalDate fechaReferencia(Animal animal, List<EstadoReproductivo> estados) {
        LocalDate ref = null;
        for (EstadoReproductivo e : estados == null ? List.<EstadoReproductivo>of() : estados) {
            ref = masReciente(ref, e.getFechaUltParto());
            ref = masReciente(ref, e.getFechaUltInseminacion());
            if (ESTADOS_DE_CICLO.contains(e.getEstado()) && e.getFechaRegistro() != null) {
                ref = masReciente(ref, e.getFechaRegistro().toLocalDate());
            }
        }
        if (ref != null) {
            return ref;
        }
        if (estados != null && !estados.isEmpty() && estados.get(0).getFechaRegistro() != null) {
            return estados.get(0).getFechaRegistro().toLocalDate();
        }
        if (animal.getFechaNacimiento() != null) {
            return animal.getFechaNacimiento().plusYears(1);
        }
        return animal.getFechaAlta() != null ? animal.getFechaAlta() : LocalDate.now();
    }

    private static LocalDate masReciente(LocalDate a, LocalDate b) {
        return a == null || (b != null && b.isAfter(a)) ? b : a;
    }

    /** No alertar si ya hay un anestro pendiente o si el último se descartó hace menos de silencioDias. */
    private boolean silenciada(Optional<Alerta> ultimaAlerta) {
        return ultimaAlerta
                .map(a -> a.getEstado() == EstadoAlerta.PENDIENTE
                        || (a.getEstado() == EstadoAlerta.DESCARTADA && a.getTsConfirmacion() != null
                                && a.getTsConfirmacion().isAfter(LocalDateTime.now().minusDays(silencioDias))))
                .orElse(false);
    }

    private void archivarPorCiclo(Alerta alerta) {
        alerta.setEstado(EstadoAlerta.ARCHIVADA);
        alerta.setMotivoDescarte("ciclo_registrado");
        alerta.setTsConfirmacion(LocalDateTime.now());
        alertaRepository.save(alerta);

        publicadorDeAlertas.publicar(AlertaEventDTO.builder()
                .id(alerta.getId())
                .animalId(alerta.getAnimal().getId())
                .tipo(alerta.getTipo())
                .disparador(alerta.getDisparador())
                .severidad(alerta.getSeveridad())
                .estado(alerta.getEstado())
                .tsGeneracion(alerta.getTsGeneracion())
                .accionRecomendada(alerta.getAccionRecomendada())
                .build());
        log.info("Alerta de anestro {} archivada: el animal {} registró un ciclo", alerta.getId(), alerta.getAnimal().getId());
    }

    private void generarAlertaAnestro(Animal animal, Establecimiento est, long diasSinCiclo) {
        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setTipo(Alerta.TIPO_ANESTRO);
        alerta.setSeveridad(est.getSeveridadAnestro());
        alerta.setTsGeneracion(LocalDateTime.now());
        alerta.setDisparador(DisparadorAlerta.CELO);
        alerta.setValorUmbral((double) est.getDiasDeteccionAnestro());
        alerta.setValorDetectado((double) diasSinCiclo);
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        
        alerta.setAccionRecomendada("Se recomienda revisión veterinaria. El animal lleva " + diasSinCiclo + " días sin ciclo registrado.");
        alerta.setPosibleCausa("Anestro prolongado (>" + est.getDiasDeteccionAnestro() + " días)");

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
                .build();

        publicadorDeAlertas.publicar(eventDTO);
        log.info("Alerta de anestro generada para el animal {} ({} días sin ciclo)", animal.getId(), diasSinCiclo);
    }
}
