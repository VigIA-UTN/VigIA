package dev.vigia.backend.repository;

import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.Caravana;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TelemetriaRepository extends JpaRepository<Telemetria, Long> {
    List<Telemetria> findByEstadoInferencia(EstadoInferencia estadoInferencia);
    List<Telemetria> findByCaravanaAndEstadoInferenciaAndFechaHoraAfter(Caravana caravana, EstadoInferencia estadoInferencia, LocalDateTime fechaHora);
    List<Telemetria> findByCaravanaAndFechaHoraAfterOrderByFechaHoraAsc(Caravana caravana, LocalDateTime fechaHora);
}
