package dev.vigia.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.Intervencion;

@Repository
public interface IntervencionRepository extends JpaRepository<Intervencion, Long> {
    List<Intervencion> findByAnimalIdOrderByFechaHoraDesc(Long animalId);
}
