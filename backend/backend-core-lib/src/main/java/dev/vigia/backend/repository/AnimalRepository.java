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

    @EntityGraph(attributePaths = { "rodeoLote", "estadosReproductivos", "caravana" })
    List<Animal> findByEstablecimientoIdInAndEstadoActualNot(
            List<Long> establecimientoIds, EstadoAnimal estadoActual);

    @EntityGraph(attributePaths = { "rodeoLote", "estadosReproductivos", "caravana" })
    List<Animal> findByEstadoActualNot(EstadoAnimal estadoActual);

    /**
     * Detector de anestro: solo lo que lee la regla. perfilReferencia va en el join porque, del lado mappedBy del
     * OneToOne, Hibernate lo carga igual con una consulta por animal.
     */
    @EntityGraph(attributePaths = { "estadosReproductivos", "perfilReferencia" })
    List<Animal> findConEstadosByEstablecimientoIdAndEstadoActualNot(Long establecimientoId, EstadoAnimal estadoActual);

    Optional<Animal> findByIdCaravanaOficial(String idCaravanaOficial);

    Optional<Animal> findByIdCaravanaOficialAndEstablecimientoId(String idCaravanaOficial, Long establecimientoId);

    Optional<Animal> findByAliasAndEstablecimientoId(String alias, Long establecimientoId);

    boolean existsByIdCaravanaOficialAndEstablecimientoId(String idCaravanaOficial, Long establecimientoId);

    boolean existsByAliasAndEstablecimientoId(String alias, Long establecimientoId);

    Optional<Animal> findByCaravanaId(Long caravanaId);

    /** Webhook de ingesta: el geofence lee el lote y la caravana del animal en otra transacción. */
    @EntityGraph(attributePaths = { "rodeoLote", "caravana" })
    Optional<Animal> findConLoteYCaravanaByCaravanaId(Long caravanaId);
}
