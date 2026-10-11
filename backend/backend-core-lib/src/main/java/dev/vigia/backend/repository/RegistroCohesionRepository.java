package dev.vigia.backend.repository;

import dev.vigia.backend.model.RegistroCohesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RegistroCohesionRepository extends JpaRepository<RegistroCohesion, Long> {

    List<RegistroCohesion> findByEstablecimientoId(Long establecimientoId);

    List<RegistroCohesion> findByEstablecimientoIdAndAnimalId(Long establecimientoId, Long animalId);

    List<RegistroCohesion> findByEstablecimientoIdAndAnimalIdAndFechaBetweenOrderByFechaAsc(
            Long establecimientoId, Long animalId, LocalDateTime desde, LocalDateTime hasta);
}
