package dev.vigia.backend.repository;

import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.Caravana;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TelemetriaRepository extends JpaRepository<Telemetria, Long> {
        List<Telemetria> findByEstadoInferenciaCelo(EstadoInferencia estadoInferenciaCelo);

        List<Telemetria> findByCaravanaAndEstadoInferenciaCeloAndFechaHoraAfter(Caravana caravana,
                        EstadoInferencia estadoInferenciaCelo, LocalDateTime fechaHora);

        List<Telemetria> findByCaravanaAndEstadoInferenciaSaludAndFechaHoraAfter(Caravana caravana,
                        EstadoInferencia estadoInferenciaSalud, LocalDateTime fechaHora);

        List<Telemetria> findByCaravanaAndFechaHoraAfterOrderByFechaHoraAsc(Caravana caravana, LocalDateTime fechaHora);

        Optional<Telemetria> findFirstByCaravanaOrderByFechaHoraDesc(Caravana caravana);

        List<Telemetria> findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(
                        Caravana caravana, LocalDateTime desde, LocalDateTime hasta);

        @Query("SELECT t FROM Telemetria t " +
                        "WHERE t.caravana.establecimiento.id = :establecimientoId " +
                        "AND t.fechaHora BETWEEN :desde AND :hasta " +
                        "AND (:animalId IS NULL OR t.caravana.animal.id = :animalId) " +
                        "ORDER BY t.fechaHora ASC")
        List<Telemetria> findByEstablecimientoAndFechaHoraBetween(
                        @Param("establecimientoId") Long establecimientoId,
                        @Param("desde") LocalDateTime desde,
                        @Param("hasta") LocalDateTime hasta,
                        @Param("animalId") Long animalId);

        @Query("SELECT t FROM Telemetria t " +
                        "WHERE t.caravana.establecimiento.id = :establecimientoId " +
                        "AND t.fechaHora BETWEEN :desde AND :hasta " +
                        "AND t.caravana.animal.id IN :animalIds " +
                        "ORDER BY t.fechaHora ASC")
        List<Telemetria> findByEstablecimientoAndAnimalesAndFechaHoraBetween(
                        @Param("establecimientoId") Long establecimientoId,
                        @Param("desde") LocalDateTime desde,
                        @Param("hasta") LocalDateTime hasta,
                        @Param("animalIds") Collection<Long> animalIds);
}
