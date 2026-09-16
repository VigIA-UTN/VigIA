package dev.vigia.backend.repository;

import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.EstadoSensor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaravanaRepository extends JpaRepository<Caravana, Long> {
    Page<Caravana> findByActivoTrue(Pageable pageable);

    Optional<Caravana> findByCodigo(String codigo);

    Optional<Caravana> findByDevEui(String devEui);

    List<Caravana> findAllByAnimalEstablecimientoId(Long establecimientoId);

    List<Caravana> findAllByEstablecimientoId(Long establecimientoId);

    List<Caravana> findAllByEstablecimientoIdAndEstadoSensor(Long establecimientoId, EstadoSensor estadoSensor);
}
