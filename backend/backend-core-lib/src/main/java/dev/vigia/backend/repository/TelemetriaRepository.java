package dev.vigia.backend.repository;

import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.Caravana;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TelemetriaRepository extends JpaRepository<Telemetria, Long> {
    List<Telemetria> findByEstadoInferenciaCelo(EstadoInferencia estadoInferenciaCelo);

    List<Telemetria> findByCaravanaAndEstadoInferenciaCeloAndFechaHoraAfter(Caravana caravana,
            EstadoInferencia estadoInferenciaCelo, LocalDateTime fechaHora);

    // Version "salud" del metodo de arriba: usa estado_inferencia_salud, el
    // flag independiente del de celo (ver comentario en Telemetria.java).
    List<Telemetria> findByCaravanaAndEstadoInferenciaSaludAndFechaHoraAfter(Caravana caravana,
            EstadoInferencia estadoInferenciaSalud, LocalDateTime fechaHora);

    List<Telemetria> findByCaravanaAndFechaHoraAfterOrderByFechaHoraAsc(Caravana caravana, LocalDateTime fechaHora);

    Optional<Telemetria> findFirstByCaravanaOrderByFechaHoraDesc(Caravana caravana);

    List<Telemetria> findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(
            Caravana caravana, LocalDateTime desde, LocalDateTime hasta);
}
