package dev.vigia.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.RegistroPeso;
import java.util.List;

@Repository
public interface RegistroPesoRepository extends JpaRepository<RegistroPeso, Long> {
    List<RegistroPeso> findByAnimalIdOrderByFechaDesc(Long animalId);

    // ponytail: trae todo el historial del establecimiento; si crece, filtrar por fecha (?desde=)
    List<RegistroPeso> findByAnimalEstablecimientoIdOrderByFechaDescIdDesc(Long establecimientoId);
}
