package dev.vigia.backend.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caravana_id")
    private Caravana caravana;

    @OneToOne(mappedBy = "animal", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private PerfilReferencia perfilReferencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rodeo_lote_id")
    private RodeoLote rodeoLote;

    @Column(name = "id_caravana_oficial")
    private String idCaravanaOficial;

    private String alias;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    private String raza;

    @Column(name = "estado_actual")
    @Enumerated(EnumType.STRING)
    private EstadoAnimal estadoActual;

    @Column(name = "fecha_alta")
    private LocalDate fechaAlta;

    @OneToMany(mappedBy = "animal", fetch = FetchType.LAZY)
    @OrderBy("fechaRegistro DESC, id DESC")
    private List<EstadoReproductivo> estadosReproductivos = new ArrayList<>();

    @OneToMany(mappedBy = "animal", fetch = FetchType.LAZY)
    @OrderBy("fechaRegistro DESC, id DESC")
    private List<EstadoSalud> estadosSalud = new ArrayList<>();

    public Animal(Establecimiento establecimiento, RodeoLote rodeoLote) {
        this.establecimiento = establecimiento;
        this.rodeoLote = rodeoLote;
        this.fechaAlta = LocalDate.now();
    }
}
