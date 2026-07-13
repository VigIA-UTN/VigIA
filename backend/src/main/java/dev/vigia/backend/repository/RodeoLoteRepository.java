package dev.vigia.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Usuario;

@Repository
public interface RodeoLoteRepository extends JpaRepository<RodeoLote, Long> {

    List<RodeoLote> findByEstablecimientoId(Long establecimientoId);

    @Query("SELECT r FROM RodeoLote r JOIN r.establecimiento e JOIN e.empleados emp JOIN emp.usuario u WHERE u = :usuario AND emp.activo = true")
    List<RodeoLote> findByEstablecimiento_Empleados_Usuario(Usuario usuario);

}
