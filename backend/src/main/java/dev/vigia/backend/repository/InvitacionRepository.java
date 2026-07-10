package dev.vigia.backend.repository;

import dev.vigia.backend.model.EstadoInvitacion;
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

    boolean existsByEmailDestinoAndEstadoInvitacionAndFechaExpiracionAfter(
            String emailDestino,
            EstadoInvitacion estadoInvitacion,
            LocalDateTime now);

    List<Invitacion> findByTipoInvitacionOrderByFechaCreacionDesc(TipoInvitacion tipoInvitacion);

    List<Invitacion> findByCreador_EmailAndTipoInvitacionOrderByFechaCreacionDesc(String creadorEmail, TipoInvitacion tipoInvitacion);
}
