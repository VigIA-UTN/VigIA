package dev.vigia.backend.repository;

import dev.vigia.backend.model.EstadoReproductivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EstadoReproductivoRepository extends JpaRepository<EstadoReproductivo, Long> {
    List<EstadoReproductivo> findByAnimalId(Long animalId);
}
