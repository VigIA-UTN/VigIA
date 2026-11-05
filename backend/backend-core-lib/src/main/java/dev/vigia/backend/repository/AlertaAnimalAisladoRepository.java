package dev.vigia.backend.repository;

import dev.vigia.backend.model.AlertaAnimalAislado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AlertaAnimalAisladoRepository extends JpaRepository<AlertaAnimalAislado, Long> {

    /** Episodios abiertos (el animal sigue fuera del grupo) de los animales del establecimiento. */
    List<AlertaAnimalAislado> findByAnimal_Establecimiento_IdAndTsRegresoIsNull(Long establecimientoId);

    @EntityGraph(attributePaths = { "animal", "alerta" })
    List<AlertaAnimalAislado> findByAlerta_IdOrderBySegundosFueraDesc(Long alertaId);

    /** Mapa: los episodios de varias alertas en una consulta, por alerta del que más tiempo estuvo fuera. */
    @EntityGraph(attributePaths = { "animal", "alerta" })
    List<AlertaAnimalAislado> findByAlerta_IdInOrderBySegundosFueraDesc(Collection<Long> alertaIds);

    /**
     * Mapa: en qué alertas de aislamiento pendientes figura cada animal (aunque ya haya vuelto al grupo). Trae la
     * alerta con su animal y caravana, igual que {@link AlertaRepository#findPendientesDeAnimales}.
     */
    @Query("SELECT r FROM AlertaAnimalAislado r JOIN FETCH r.alerta a LEFT JOIN FETCH a.animal an "
            + "LEFT JOIN FETCH an.caravana WHERE r.animal.id IN :animalIds AND a.estado = 'PENDIENTE'")
    List<AlertaAnimalAislado> findListadosEnAlertasPendientes(@Param("animalIds") Collection<Long> animalIds);
}
