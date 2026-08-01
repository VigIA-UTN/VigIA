package dev.vigia.backend.model;

import dev.vigia.backend.util.BooleanToSmallintConverter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "empleado")
@Getter
@Setter
@NoArgsConstructor
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "establecimiento_id")
    private Establecimiento establecimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Convert(converter = BooleanToSmallintConverter.class)
    private boolean activo = true;

    private LocalDateTime fechaVinculacion;

    private LocalDateTime fechaDesvinculacion;

    public Empleado(Establecimiento establecimiento, Usuario usuario) {
        this.establecimiento = establecimiento;
        this.usuario = usuario;
        this.fechaVinculacion = LocalDateTime.now();
        this.activo = true;
    }
}
