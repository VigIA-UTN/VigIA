package dev.vigia.backend.repository;

import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.Caravana;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TelemetriaRepository extends JpaRepository<Telemetria, Long> {
        List<Telemetria> findByEstadoInferenciaCelo(EstadoInferencia estadoInferenciaCelo);

        List<Telemetria> findByCaravanaAndEstadoInferenciaCeloAndFechaHoraAfter(Caravana caravana,
                        EstadoInferencia estadoInferenciaCelo, LocalDateTime fechaHora);

        List<Telemetria> findByCaravanaAndEstadoInferenciaSaludAndFechaHoraAfter(Caravana caravana,
                        EstadoInferencia estadoInferenciaSalud, LocalDateTime fechaHora);

        /** Hay telemetría del establecimiento todavía no analizada por ml-geo desde {@code desde}. */
        @Query("SELECT COUNT(t) > 0 FROM Telemetria t " +
                        "WHERE t.caravana.establecimiento.id = :establecimientoId " +
                        "AND t.estadoInferenciaGeo = :estado " +
                        "AND t.fechaHora > :desde")
        boolean existsPendientesGeoByEstablecimiento(
                        @Param("establecimientoId") Long establecimientoId,
                        @Param("estado") EstadoInferencia estado,
                        @Param("desde") LocalDateTime desde);

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

        // Mapa de calor (API): estas dos traen caravana y animal en la misma consulta, así el service no
        // los carga de a uno ni necesita una transacción abierta mientras espera a ml-geo.
        @Query("SELECT t FROM Telemetria t JOIN FETCH t.caravana c JOIN FETCH c.animal a " +
                        "WHERE c.establecimiento.id = :establecimientoId " +
                        "AND t.fechaHora BETWEEN :desde AND :hasta " +
                        "ORDER BY t.fechaHora ASC")
        List<Telemetria> findConAnimalByEstablecimientoAndFechaHoraBetween(
                        @Param("establecimientoId") Long establecimientoId,
                        @Param("desde") LocalDateTime desde,
                        @Param("hasta") LocalDateTime hasta);

        @Query("SELECT t FROM Telemetria t JOIN FETCH t.caravana c JOIN FETCH c.animal a " +
                        "WHERE c.establecimiento.id = :establecimientoId " +
                        "AND t.fechaHora BETWEEN :desde AND :hasta " +
                        "AND a.id IN :animalIds " +
                        "ORDER BY t.fechaHora ASC")
        List<Telemetria> findByEstablecimientoAndAnimalesAndFechaHoraBetween(
                        @Param("establecimientoId") Long establecimientoId,
                        @Param("desde") LocalDateTime desde,
                        @Param("hasta") LocalDateTime hasta,
                        @Param("animalIds") java.util.List<Long> animalIds);
}
