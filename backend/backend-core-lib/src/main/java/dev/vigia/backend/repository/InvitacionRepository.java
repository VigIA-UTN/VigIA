package dev.vigia.backend.repository;

import dev.vigia.backend.model.EstadoInvitacion;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Invitacion;
import dev.vigia.backend.model.TipoInvitacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvitacionRepository extends JpaRepository<Invitacion, Long> {
    Optional<Invitacion> findByToken(String token);

    boolean existsByEmailDestinoAndEstablecimientoAndEstadoInvitacionAndFechaExpiracionAfter(
            String emailDestino,
            Establecimiento establecimiento,
            EstadoInvitacion estadoInvitacion,
            LocalDateTime now);

    List<Invitacion> findByTipoInvitacionOrderByFechaCreacionDesc(TipoInvitacion tipoInvitacion);

    List<Invitacion> findByCreador_EmailAndTipoInvitacionOrderByFechaCreacionDesc(String creadorEmail, TipoInvitacion tipoInvitacion);

    List<Invitacion> findByCreador_EmailAndEstablecimiento_IdAndTipoInvitacionOrderByFechaCreacionDesc(String creadorEmail, Long establecimientoId, TipoInvitacion tipoInvitacion);

    List<Invitacion> findByEmailDestinoAndEstadoInvitacionOrderByFechaCreacionDesc(String emailDestino, EstadoInvitacion estadoInvitacion);
}
