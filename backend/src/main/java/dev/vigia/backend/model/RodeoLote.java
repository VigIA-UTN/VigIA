package dev.vigia.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

import dev.vigia.backend.util.BooleanToSmallintConverter;

@Entity
@Table(name = "rodeo_lote")
@Getter
@Setter
@NoArgsConstructor
public class RodeoLote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "establecimiento_id")
    private Establecimiento establecimiento;

    private String nombre;

    @Convert(converter = BooleanToSmallintConverter.class)
    private boolean activo = true;

    @Column(name = "fecha_alta")
    private LocalDateTime fechaAlta;

    public RodeoLote(Establecimiento establecimiento) {
        this.establecimiento = establecimiento;
        this.fechaAlta = LocalDateTime.now();
    }
}
