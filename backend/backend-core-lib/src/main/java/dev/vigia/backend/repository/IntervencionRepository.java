package dev.vigia.backend.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.Intervencion;

@Repository
public interface IntervencionRepository extends JpaRepository<Intervencion, Long> {
    List<Intervencion> findByAnimalIdOrderByFechaHoraDesc(Long animalId);

    boolean existsByAlertaId(Long alertaId);

    /**
     * Lista intervenciones de un animal en un rango de fechas opcional.
     * Ordenadas cronológicamente (fechaHora ASC).
     * Utilizada por el endpoint de línea de tiempo de la ficha del animal.
     *
     * @param animalId ID del animal
     * @param desde    inicio del rango, inclusive (null = sin límite inferior)
     * @param hasta    fin del rango, inclusive (null = sin límite superior)
     */
    @Query("SELECT i FROM Intervencion i " +
           "WHERE i.animal.id = :animalId " +
           "AND (cast(:desde as timestamp) IS NULL OR i.fechaHora >= :desde) " +
           "AND (cast(:hasta as timestamp) IS NULL OR i.fechaHora <= :hasta) " +
           "ORDER BY i.fechaHora ASC")
    List<Intervencion> findByAnimalConRango(
            @Param("animalId") Long animalId,
            @Param("desde")    LocalDateTime desde,
            @Param("hasta")    LocalDateTime hasta);
}

