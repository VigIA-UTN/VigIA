package dev.vigia.backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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

       /**
        * Lista alertas de celo (disparador = CELO) de un establecimiento concreto,
        * ordenadas por tsGeneracion descendente.
        */
       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.animal.establecimiento.id = :establecimientoId " +
                     "AND a.disparador = dev.vigia.backend.model.DisparadorAlerta.CELO " +
                     "ORDER BY a.tsGeneracion DESC")
       List<Alerta> findCeloByEstablecimiento(@Param("establecimientoId") Long establecimientoId);

       /**
        * Lista alertas de celo filtradas por estado, ordenadas por tsGeneracion
        * descendente.
        */
       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.animal.establecimiento.id = :establecimientoId " +
                     "AND a.disparador = dev.vigia.backend.model.DisparadorAlerta.CELO " +
                     "AND a.estado = :estado " +
                     "ORDER BY a.tsGeneracion DESC")
       List<Alerta> findCeloByEstablecimientoAndEstado(
                     @Param("establecimientoId") Long establecimientoId,
                     @Param("estado") EstadoAlerta estado);

       /**
        * Busca una alerta verificando que pertenece al establecimiento indicado.
        * Útil para validar aislamiento antes de operar sobre ella.
        */
       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.id = :id " +
                     "AND a.animal.establecimiento.id = :establecimientoId")
       Optional<Alerta> findByIdAndEstablecimiento(
                     @Param("id") Long id,
                     @Param("establecimientoId") Long establecimientoId);

       /**
        * Lista alertas de celo para recuperar mensajes perdidos mediante
        * Last-Event-ID.
        */
       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.animal.establecimiento.id = :establecimientoId " +
                     "AND a.disparador = dev.vigia.backend.model.DisparadorAlerta.CELO " +
                     "AND a.id > :lastId " +
                     "ORDER BY a.id ASC")
       List<Alerta> findCeloByEstablecimientoAndIdGreaterThanOrderByIdAsc(
                     @Param("establecimientoId") Long establecimientoId,
                     @Param("lastId") Long lastId);

       /**
        * Verifica si existe una alerta reciente para un animal y un disparador en
        * particular.
        * Útil para implementar períodos de enfriamiento (cooldown) y no repetir
        * alertas.
        */
       boolean existsByAnimalAndDisparadorAndTsGeneracionAfter(
                     Animal animal,
                     DisparadorAlerta disparador,
                     LocalDateTime tsGeneracion);

       /**
        * Cuenta las alertas activas (estado PENDIENTE) agrupadas por tipo (disparador)
        * para un establecimiento específico.
        */
       @Query("SELECT a.disparador, COUNT(a) FROM Alerta a " +
                     "WHERE a.animal.establecimiento.id = :establecimientoId " +
                     "AND a.estado = dev.vigia.backend.model.EstadoAlerta.PENDIENTE " +
                     "GROUP BY a.disparador")
       List<Object[]> countAlertasActivasPorDisparador(@Param("establecimientoId") Long establecimientoId);

       @Query("SELECT a, CASE WHEN a.severidad = 'ALTA' THEN 1 WHEN a.severidad = 'MEDIA' THEN 2 ELSE 3 END as cardinalidad FROM Alerta a WHERE a.animal.id = :animalId AND a.estado = 'PENDIENTE' ORDER BY cardinalidad ASC")
       List<Alerta> alertasAnimalActivas(@Param("animalId") Long animalId);

       /**
        * Lista las alertas de un establecimiento concreto aplicando filtros
        * opcionales.
        * Ordena por severidad descendente (ALTA, MEDIA, BAJA) y luego por fecha.
        */
       @Query("SELECT a FROM Alerta a " +
                     "WHERE a.animal.establecimiento.id = :establecimientoId " +
                     "AND (:estado IS NULL OR a.estado = :estado) " +
                     "AND (:disparador IS NULL OR a.disparador = :disparador) " +
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
                     @Param("disparador") DisparadorAlerta disparador,
                     @Param("severidad") SeveridadAlerta severidad);
}
