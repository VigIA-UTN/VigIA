package dev.vigia.backend.repository;

import dev.vigia.backend.model.Caravana;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaravanaRepository extends JpaRepository<Caravana, Long> {
    Page<Caravana> findByActivoTrue(Pageable pageable);
    java.util.Optional<Caravana> findByCodigo(String codigo);
}
