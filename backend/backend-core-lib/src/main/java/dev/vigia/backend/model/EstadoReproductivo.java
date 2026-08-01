package dev.vigia.backend.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "estados_reproductivos")
@Getter
@Setter
@NoArgsConstructor
public class EstadoReproductivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "animal_id")
    private Animal animal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_registro_id")
    private Usuario usuarioRegistrador;

    @Enumerated(EnumType.STRING)
    private TipoReproductivo estado;

    @Column(name = "fecha_ultimo_parto")
    private LocalDate fechaUltParto;

    @Column(name = "fecha_ultima_inseminacion")
    private LocalDate fechaUltInseminacion;

    @Enumerated(EnumType.STRING)
    private OrigenEstado origen;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;
}
