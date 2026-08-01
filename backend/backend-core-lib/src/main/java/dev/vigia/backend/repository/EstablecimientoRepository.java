package dev.vigia.backend.repository;

import dev.vigia.backend.model.Establecimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EstablecimientoRepository extends JpaRepository<Establecimiento, Long> {
    @Query("SELECT e FROM Establecimiento e JOIN e.empleados v WHERE v.usuario.id = :usuarioId AND v.activo = true")
    List<Establecimiento> findActiveEstablecimientoByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT e FROM Establecimiento e JOIN e.empleados v WHERE v.usuario.email = :email AND v.activo = true")
    List<Establecimiento> findActiveEstablecimientoByUsuarioEmail(@Param("email") String email);
}
