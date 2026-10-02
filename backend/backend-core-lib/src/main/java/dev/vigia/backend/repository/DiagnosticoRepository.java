package dev.vigia.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.Diagnostico;

@Repository
public interface DiagnosticoRepository extends JpaRepository<Diagnostico, Long> {

    /** Diagnósticos de los establecimientos indicados, opcionalmente de un solo animal; más recientes primero. */
    @Query("SELECT d FROM Diagnostico d " +
           "JOIN FETCH d.animal a " +
           "JOIN FETCH a.establecimiento e " +
           "JOIN FETCH d.alerta " +
           "JOIN FETCH d.veterinario " +
           "WHERE e.id IN :ids " +
           "AND (:animalId IS NULL OR a.id = :animalId) " +
           "ORDER BY d.fecha DESC, d.id DESC")
    List<Diagnostico> findVisibles(
            @Param("ids") Collection<Long> establecimientoIds,
            @Param("animalId") Long animalId);
}
