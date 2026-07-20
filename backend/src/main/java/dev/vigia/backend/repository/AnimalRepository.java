package dev.vigia.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.EstadoAnimal;

@Repository
public interface AnimalRepository extends JpaRepository<Animal, Long> {

    List<Animal> findByEstablecimientoId(Long establecimientoId);

    List<Animal> findByRodeoLoteId(Long rodeoLoteId);

    @EntityGraph(attributePaths = { "rodeoLote", "estadosReproductivos" })
    List<Animal> findByEstablecimientoIdInAndEstadoActualNot(
            List<Long> establecimientoIds, EstadoAnimal estadoActual);

    @EntityGraph(attributePaths = { "rodeoLote", "estadosReproductivos" })
    List<Animal> findByEstadoActualNot(EstadoAnimal estadoActual);

    Optional<Animal> findByIdCaravanaOficial(String idCaravanaOficial);

    Optional<Animal> findByIdCaravanaOficialAndEstablecimientoId(String idCaravanaOficial, Long establecimientoId);

    Optional<Animal> findByAliasAndEstablecimientoId(String alias, Long establecimientoId);

    boolean existsByIdCaravanaOficialAndEstablecimientoId(String idCaravanaOficial, Long establecimientoId);
}
