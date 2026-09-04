package dev.vigia.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.EstadoSalud;

@Repository
public interface EstadoSaludRepository extends JpaRepository<EstadoSalud, Long> {
}
