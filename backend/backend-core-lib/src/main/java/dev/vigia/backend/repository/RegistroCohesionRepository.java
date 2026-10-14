package dev.vigia.backend.repository;

import dev.vigia.backend.model.RegistroCohesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RegistroCohesionRepository extends JpaRepository<RegistroCohesion, Long> {

    List<RegistroCohesion> findByEstablecimientoId(Long establecimientoId);

    List<RegistroCohesion> findByEstablecimientoIdAndAnimalId(Long establecimientoId, Long animalId);

    List<RegistroCohesion> findByEstablecimientoIdAndAnimalIdAndFechaBetweenOrderByFechaAsc(
            Long establecimientoId, Long animalId, LocalDateTime desde, LocalDateTime hasta);

    /**
     * Promedio del índice de los animales de un rodeo en cada análisis del rango:
     * filas [fecha (LocalDateTime), promedio (Double)] ordenadas por fecha.
     */
    @Query("SELECT r.fecha, AVG(r.indiceCohesion) FROM RegistroCohesion r " +
            "WHERE r.establecimiento.id = :establecimientoId " +
            "AND r.animal.rodeoLote.id = :rodeoLoteId " +
            "AND r.fecha BETWEEN :desde AND :hasta " +
            "GROUP BY r.fecha ORDER BY r.fecha")
    List<Object[]> promedioPorFechaDelRodeo(
            @Param("establecimientoId") Long establecimientoId,
            @Param("rodeoLoteId") Long rodeoLoteId,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    /** Igual que {@link #promedioPorFechaDelRodeo} para los animales sin rodeo (que ml-geo analiza como un grupo). */
    @Query("SELECT r.fecha, AVG(r.indiceCohesion) FROM RegistroCohesion r " +
            "WHERE r.establecimiento.id = :establecimientoId " +
            "AND r.animal.rodeoLote IS NULL " +
            "AND r.fecha BETWEEN :desde AND :hasta " +
            "GROUP BY r.fecha ORDER BY r.fecha")
    List<Object[]> promedioPorFechaSinRodeo(
            @Param("establecimientoId") Long establecimientoId,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}
