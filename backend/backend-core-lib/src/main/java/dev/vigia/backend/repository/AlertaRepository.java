package dev.vigia.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.SeveridadAlerta;

@Repository
public interface AlertaRepository extends JpaRepository<Alerta, Long> {

       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.animal.establecimiento.id = :establecimientoId " +
                     "AND a.disparador = dev.vigia.backend.model.DisparadorAlerta.CELO " +
                     "ORDER BY a.tsGeneracion DESC")
       List<Alerta> findCeloByEstablecimiento(@Param("establecimientoId") Long establecimientoId);

       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.animal.establecimiento.id = :establecimientoId " +
                     "AND a.disparador = dev.vigia.backend.model.DisparadorAlerta.CELO " +
                     "AND a.estado = :estado " +
                     "ORDER BY a.tsGeneracion DESC")
       List<Alerta> findCeloByEstablecimientoAndEstado(
                     @Param("establecimientoId") Long establecimientoId,
                     @Param("estado") EstadoAlerta estado);

       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.animal.establecimiento.id = :establecimientoId " +
                     "AND a.disparador = dev.vigia.backend.model.DisparadorAlerta.SALUD " +
                     "ORDER BY a.tsGeneracion DESC")
       List<Alerta> findSaludByEstablecimiento(@Param("establecimientoId") Long establecimientoId);

       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.animal.establecimiento.id = :establecimientoId " +
                     "AND a.disparador = dev.vigia.backend.model.DisparadorAlerta.SALUD " +
                     "AND a.estado = :estado " +
                     "ORDER BY a.tsGeneracion DESC")
       List<Alerta> findSaludByEstablecimientoAndEstado(
                     @Param("establecimientoId") Long establecimientoId,
                     @Param("estado") EstadoAlerta estado);

       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.id = :id " +
                     "AND a.animal.establecimiento.id = :establecimientoId")
       Optional<Alerta> findByIdAndEstablecimiento(
                     @Param("id") Long id,
                     @Param("establecimientoId") Long establecimientoId);

       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT a FROM Alerta a WHERE a.id = :id")
       Optional<Alerta> findByIdForUpdate(@Param("id") Long id);

       /**
        * Bloquea la fila de la alerta hasta el fin de la transacción y devuelve su estado leído de la base
        * (no del contexto de persistencia, que puede tener la alerta cargada desde antes del bloqueo).
        */
       @Query(value = "SELECT estado FROM alertas WHERE id = :id FOR UPDATE", nativeQuery = true)
       String bloquearYObtenerEstado(@Param("id") Long id);

       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.animal.establecimiento.id = :establecimientoId " +
                     "AND a.id > :lastId " +
                     "ORDER BY a.id ASC")
       List<Alerta> findByEstablecimientoAndIdGreaterThanOrderByIdAsc(
                     @Param("establecimientoId") Long establecimientoId,
                     @Param("lastId") Long lastId);

       boolean existsByAnimalAndDisparadorAndTsGeneracionAfter(
                     Animal animal,
                     DisparadorAlerta disparador,
                     LocalDateTime tsGeneracion);

       Optional<Alerta> findFirstByAnimalAndDisparadorAndTsGeneracionAfterOrderByTsGeneracionDesc(
                     Animal animal,
                     DisparadorAlerta disparador,
                     LocalDateTime tsGeneracion);

       @Query("SELECT a.disparador, COUNT(a) FROM Alerta a " +
                     "WHERE a.animal.establecimiento.id = :establecimientoId " +
                     "AND a.estado = dev.vigia.backend.model.EstadoAlerta.PENDIENTE " +
                     "GROUP BY a.disparador")
       List<Object[]> countAlertasActivasPorDisparador(@Param("establecimientoId") Long establecimientoId);

       /** Alertas pendientes del animal: las propias y las de aislamiento que lo listan (aunque ya haya vuelto). */
       @Query("SELECT a FROM Alerta a WHERE (a.animal.id = :animalId "
                     + "OR EXISTS (SELECT r.id FROM AlertaAnimalAislado r WHERE r.alerta = a AND r.animal.id = :animalId)) "
                     + "AND a.estado = 'PENDIENTE' ORDER BY CASE WHEN a.severidad = 'ALTA' THEN 1 WHEN a.severidad = 'MEDIA' THEN 2 ELSE 3 END ASC")
       List<Alerta> alertasAnimalActivas(@Param("animalId") Long animalId);

       /** Detector de anestro: las alertas de un tipo de todo el establecimiento en una consulta, la más nueva primero. */
       List<Alerta> findByAnimal_Establecimiento_IdAndTipoOrderByTsGeneracionDesc(Long establecimientoId, String tipo);

       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.animal.id = :animalId " +
                     "AND a.disparador IN :disparadores " +
                     "AND (cast(:desde as timestamp) IS NULL OR a.tsGeneracion >= :desde) " +
                     "AND (cast(:hasta as timestamp) IS NULL OR a.tsGeneracion <= :hasta) " +
                     "ORDER BY a.tsGeneracion ASC")
       List<Alerta> findByAnimalConFiltros(
                     @Param("animalId") Long animalId,
                     @Param("disparadores") Collection<DisparadorAlerta> disparadores,
                     @Param("desde") LocalDateTime desde,
                     @Param("hasta") LocalDateTime hasta);

       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.animal.establecimiento.id = :establecimientoId " +
                     "AND (:estado IS NULL OR a.estado = :estado) " +
                     "AND a.disparador IN :disparadores " +
                     "AND (:severidad IS NULL OR a.severidad = :severidad) " +
                     "ORDER BY " +
                     "  CASE WHEN a.severidad = 'ALTA' THEN 1 " +
                     "       WHEN a.severidad = 'MEDIA' THEN 2 " +
                     "       WHEN a.severidad = 'BAJA' THEN 3 " +
                     "       ELSE 4 END ASC, " +
                     "  a.tsGeneracion DESC")
       List<Alerta> findByFiltros(
                     @Param("establecimientoId") Long establecimientoId,
                     @Param("estado") EstadoAlerta estado,
                     @Param("disparadores") Collection<DisparadorAlerta> disparadores,
                     @Param("severidad") SeveridadAlerta severidad);

       @Query("SELECT a FROM Alerta a " +
              "WHERE a.estado = dev.vigia.backend.model.EstadoAlerta.PENDIENTE " +
              "AND a.disparador = dev.vigia.backend.model.DisparadorAlerta.CELO " +
              "AND a.ventanaFinIns < :now")
       List<Alerta> findAlertasCeloPendientesVencidas(@Param("now") LocalDateTime now);
}
