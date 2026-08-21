package dev.vigia.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.PerfilReferencia;

@Repository
public interface PerfilReferenciaRepository extends JpaRepository<PerfilReferencia, Long> {

    Optional<PerfilReferencia> findByAnimal(Animal animal);
}
