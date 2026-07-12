package dev.vigia.backend.model;

import java.time.LocalDateTime;

import dev.vigia.backend.util.BooleanToSmallintConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "animales")
@Getter
@Setter
@NoArgsConstructor
public class Animal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "establecimiento_id")
    private Establecimiento establecimiento;

    // ToDo: CARABANA
    // ToDO: PERFIL REFERENCIA

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rodeo_lote_id")
    private RodeoLote rodeoLote;

    @Column(name = "id_caravana_oficial")
    private String idCaravanaOficial;

    private String alias;

    @Column(name = "fecha_nacimiento")
    private LocalDateTime fechaNacimiento;

    private String raza;

    @Column(name = "estado_actual")
    @Enumerated(EnumType.STRING)
    private EstadoAnimal estadoActual;

    @Column(name = "fecha_alta")
    private LocalDateTime fechaAlta;

    @Convert(converter = BooleanToSmallintConverter.class)
    private boolean activo = true;

    public Animal(Establecimiento establecimiento, RodeoLote rodeoLote) {
        this.establecimiento = establecimiento;
        this.rodeoLote = rodeoLote;
        this.fechaAlta = LocalDateTime.now();
    }
}
