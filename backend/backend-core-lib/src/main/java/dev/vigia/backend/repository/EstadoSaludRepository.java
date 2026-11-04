package dev.vigia.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.EstadoSalud;

@Repository
public interface EstadoSaludRepository extends JpaRepository<EstadoSalud, Long> {

    List<EstadoSalud> findByAnimalIdOrderByFechaRegistroDesc(Long animalId);

    /** Estado de salud actual (el último registrado) de cada animal, en una sola consulta. */
    @Query(value = "SELECT DISTINCT ON (animal_id) animal_id AS animalId, estado AS estado FROM estados_salud "
            + "WHERE animal_id IN (:animalIds) ORDER BY animal_id, fecha_registro DESC, estado_salud_id DESC",
            nativeQuery = true)
    List<UltimoEstadoSalud> findUltimoPorAnimal(@Param("animalIds") Collection<Long> animalIds);

    interface UltimoEstadoSalud {
        Long getAnimalId();

        String getEstado();
    }

    Optional<EstadoSalud> findFirstByAnimalIdOrderByFechaRegistroDesc(Long animalId);
}
