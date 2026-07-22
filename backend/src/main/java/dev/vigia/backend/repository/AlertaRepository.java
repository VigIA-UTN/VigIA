package dev.vigia.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.EstadoAlerta;

@Repository
public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    /**
     * Lista alertas de celo (disparador = CELO) de un establecimiento concreto,
     * ordenadas por tsGeneracion descendente.
     */
    @Query("SELECT a FROM Alerta a " +
           "WHERE a.animal.establecimiento.id = :establecimientoId " +
           "AND a.disparador = dev.vigia.backend.model.DisparadorAlerta.CELO " +
           "ORDER BY a.tsGeneracion DESC")
    List<Alerta> findCeloByEstablecimiento(@Param("establecimientoId") Long establecimientoId);

    /**
     * Lista alertas de celo filtradas por estado, ordenadas por tsGeneracion descendente.
     */
    @Query("SELECT a FROM Alerta a " +
           "WHERE a.animal.establecimiento.id = :establecimientoId " +
           "AND a.disparador = dev.vigia.backend.model.DisparadorAlerta.CELO " +
           "AND a.estado = :estado " +
           "ORDER BY a.tsGeneracion DESC")
    List<Alerta> findCeloByEstablecimientoAndEstado(
            @Param("establecimientoId") Long establecimientoId,
            @Param("estado") EstadoAlerta estado);

    /**
     * Busca una alerta verificando que pertenece al establecimiento indicado.
     * Útil para validar aislamiento antes de operar sobre ella.
     */
    @Query("SELECT a FROM Alerta a " +
           "WHERE a.id = :id " +
           "AND a.animal.establecimiento.id = :establecimientoId")
    Optional<Alerta> findByIdAndEstablecimiento(
            @Param("id") Long id,
            @Param("establecimientoId") Long establecimientoId);
}
