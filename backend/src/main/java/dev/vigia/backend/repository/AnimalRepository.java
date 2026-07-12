package dev.vigia.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.Animal;

@Repository
public interface AnimalRepository extends JpaRepository<Animal, Long> {

    List<Animal> findByEstablecimientoId(Long establecimientoId);

    List<Animal> findByRodeoLoteId(Long rodeoLoteId);
}
