package dev.vigia.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.Alerta;

@Repository
public interface AlertaRepository extends JpaRepository<Alerta, Long> {
}
