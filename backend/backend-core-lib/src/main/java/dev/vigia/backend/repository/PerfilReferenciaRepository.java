package dev.vigia.backend.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.PerfilReferencia;

@Repository
public interface PerfilReferenciaRepository extends JpaRepository<PerfilReferencia, Long> {

    Optional<PerfilReferencia> findByAnimal(Animal animal);

    boolean existsByAnimal(Animal animal);

    /** Mapa: cuáles de estos animales ya tienen perfil de salud (terminaron de calibrar), en una consulta. */
    @Query("SELECT p.animal.id FROM PerfilReferencia p WHERE p.animal.id IN :animalIds")
    Set<Long> findAnimalIdsConPerfil(@Param("animalIds") Collection<Long> animalIds);
}
