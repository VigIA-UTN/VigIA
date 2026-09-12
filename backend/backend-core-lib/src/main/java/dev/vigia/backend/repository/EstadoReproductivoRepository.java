package dev.vigia.backend.repository;

import dev.vigia.backend.model.EstadoReproductivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EstadoReproductivoRepository extends JpaRepository<EstadoReproductivo, Long> {
    List<EstadoReproductivo> findByAnimalId(Long animalId);

    Optional<EstadoReproductivo> findFirstByAnimalIdOrderByFechaRegistroDesc(Long animalId);
}
