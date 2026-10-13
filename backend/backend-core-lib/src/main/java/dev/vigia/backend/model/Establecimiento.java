package dev.vigia.backend.model;

import dev.vigia.backend.util.BooleanToSmallintConverter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// UPDATE solo con las columnas modificadas: los PUT de anestro y aislamiento llegan en paralelo
// y, con el UPDATE de todas las columnas, el último pisaba el cambio del otro.
@Entity
@DynamicUpdate
@Table(name = "establecimientos")
@Getter
@Setter
@NoArgsConstructor
public class Establecimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private Double latitud;
    private Double longitud;

    @OneToMany(mappedBy = "establecimiento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Empleado> empleados = new ArrayList<>();

    @OneToMany(mappedBy = "establecimiento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Perimetro> perimetros = new ArrayList<>();

    @Convert(converter = BooleanToSmallintConverter.class)
    private boolean activo = true;

    private LocalDate fechaAlta;

    private Integer diasDeteccionAnestro = 45;

    /**
     * Umbral de aislamiento para el análisis geoespacial de ml-geo, en metros.
     * Por defecto 100 m, que coincide con el valor de la variable de entorno
     * UMBRAL_AISLAMIENTO_METROS del servicio ml-geo.
     */
    private Double umbralAislamientoM = 100.0;

    @Enumerated(EnumType.STRING)
    private SeveridadAlerta severidadAnestro = SeveridadAlerta.MEDIA;
}

