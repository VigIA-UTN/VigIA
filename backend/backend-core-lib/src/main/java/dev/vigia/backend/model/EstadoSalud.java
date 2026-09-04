package dev.vigia.backend.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "estados_salud")
@Getter
@Setter
@NoArgsConstructor
public class EstadoSalud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "estado_salud_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "animal_id", nullable = false)
    private Animal animal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_veterinario_id")
    private Usuario usuarioVeterinario;

    @Column(name = "fecha_revision")
    private LocalDate fechaRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEstadoSalud estado;

    @Column(name = "diagnostico_notas")
    private String diagnosticoNotas;

    @Column(name = "tratamiento_indicado")
    private String tratamientoIndicado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigenEstado origen;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;
}
