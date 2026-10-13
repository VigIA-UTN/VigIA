package dev.vigia.backend.repository;

import dev.vigia.backend.model.AlertaAnimalAislado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertaAnimalAisladoRepository extends JpaRepository<AlertaAnimalAislado, Long> {

    /** Episodios abiertos (el animal sigue fuera del grupo) de los animales del establecimiento. */
    List<AlertaAnimalAislado> findByAnimal_Establecimiento_IdAndTsRegresoIsNull(Long establecimientoId);

    List<AlertaAnimalAislado> findByAlerta_IdOrderBySegundosFueraDesc(Long alertaId);
}
