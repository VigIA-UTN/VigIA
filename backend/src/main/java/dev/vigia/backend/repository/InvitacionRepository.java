package dev.vigia.backend.repository;

import dev.vigia.backend.model.Invitacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvitacionRepository extends JpaRepository<Invitacion, Long> {
    Optional<Invitacion> findByToken(String token);

    boolean existsByEmailDestinoAndEstadoInvitacionAndFechaExpiracionAfter(
            String emailDestino,
            dev.vigia.backend.model.EstadoInvitacion estadoInvitacion,
            java.time.LocalDateTime now);
}
