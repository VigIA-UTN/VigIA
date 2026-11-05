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

        /**
         * Último dato de cada caravana, en una sola consulta (mapa de posiciones). En TimescaleDB el DISTINCT ON
         * se resuelve con SkipScan sobre idx_telemetria_caravana_fecha: no lee toda la serie de cada caravana.
         */
        @Query(value = "SELECT DISTINCT ON (caravana_id) * FROM telemetria WHERE caravana_id IN (:caravanaIds) "
                        + "ORDER BY caravana_id, fecha_hora DESC", nativeQuery = true)
        List<Telemetria> findUltimaPorCaravana(@Param("caravanaIds") Collection<Long> caravanaIds);

        /** Primer dato de la caravana desde {@code desde}: donde empieza la primera calibración de salud. */
        Optional<Telemetria> findFirstByCaravanaAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(
                        Caravana caravana, LocalDateTime desde);

        /**
         * Mapa: primer dato de cada caravana desde el día en que se asignó (donde empieza la calibración de salud),
         * en una consulta. Misma regla que {@link #findFirstByCaravanaAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc}.
         */
        @Query(value = "SELECT DISTINCT ON (t.caravana_id) t.caravana_id AS caravanaId, t.fecha_hora AS fechaHora "
                        + "FROM telemetria t JOIN caravanas c ON c.id = t.caravana_id "
                        + "WHERE t.caravana_id IN (:caravanaIds) "
                        + "AND t.fecha_hora >= COALESCE(CAST(c.fecha_asignacion AS TIMESTAMP), TIMESTAMP '1970-01-01 00:00:00') "
                        + "ORDER BY t.caravana_id, t.fecha_hora ASC", nativeQuery = true)
        List<PrimerDato> findPrimerDatoDesdeAsignacion(@Param("caravanaIds") Collection<Long> caravanaIds);

        interface PrimerDato {
                Long getCaravanaId();

                LocalDateTime getFechaHora();
        }

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
