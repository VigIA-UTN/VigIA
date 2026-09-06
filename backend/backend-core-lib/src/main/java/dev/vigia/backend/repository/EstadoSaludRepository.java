package dev.vigia.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.EstadoSalud;

@Repository
public interface EstadoSaludRepository extends JpaRepository<EstadoSalud, Long> {

    List<EstadoSalud> findByAnimalIdOrderByFechaRegistroDesc(Long animalId);
}
