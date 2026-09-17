package dev.vigia.backend.repository;

import dev.vigia.backend.model.Perimetro;
import dev.vigia.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PerimetroRepository extends JpaRepository<Perimetro, Long> {

    @Query(value = """
        SELECT p.* FROM perimetro p
        JOIN perimetro_lote pl ON pl.perimetro_id = p.id
        WHERE pl.rodeo_lote_id = :loteId
          AND p.activo = true
          AND ST_Contains(p.area, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
        """, nativeQuery = true)
    List<Perimetro> findPerimetrosQueContienen(@Param("loteId") Long loteId,
                                              @Param("lat") double lat,
                                              @Param("lon") double lon);

    List<Perimetro> findByEstablecimientoId(Long establecimientoId);

    List<Perimetro> findByEstablecimientoIdAndActivoTrue(Long establecimientoId);

    @Query("SELECT p FROM Perimetro p JOIN p.lotes l WHERE l.id = :loteId AND p.activo = true")
    List<Perimetro> findPerimetrosActivosByLoteId(@Param("loteId") Long loteId);

    @Query("""
        SELECT p FROM Perimetro p
        JOIN p.establecimiento e
        JOIN e.empleados emp
        JOIN emp.usuario u
        WHERE p.id = :id AND u = :usuario AND emp.activo = true
        """)
    Optional<Perimetro> findByIdAndUsuario(@Param("id") Long id, @Param("usuario") Usuario usuario);

    @Query("""
        SELECT p FROM Perimetro p
        JOIN p.establecimiento e
        JOIN e.empleados emp
        JOIN emp.usuario u
        WHERE e.id = :establecimientoId AND u = :usuario AND emp.activo = true
        """)
    List<Perimetro> findByEstablecimientoIdAndUsuario(@Param("establecimientoId") Long establecimientoId,
                                                    @Param("usuario") Usuario usuario);
}
