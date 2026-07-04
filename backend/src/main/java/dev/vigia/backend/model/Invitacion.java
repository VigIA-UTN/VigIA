package dev.vigia.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "invitaciones")
@Getter
@Setter
@NoArgsConstructor
public class Invitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_destino", nullable = false)
    private String emailDestino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_expiracion")
    private LocalDateTime fechaExpiracion;

    @ManyToOne
    @JoinColumn(name = "creador_id")
    private Usuario creador;

    @ManyToOne
    @JoinColumn(name = "establecimiento_id")
    private Establecimiento establecimiento;

    @Column(nullable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_invitacion", nullable = false)
    private TipoInvitacion tipoInvitacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_invitacion", nullable = false)
    private EstadoInvitacion estadoInvitacion;
}
