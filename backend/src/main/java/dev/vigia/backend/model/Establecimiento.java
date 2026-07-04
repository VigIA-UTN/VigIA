package dev.vigia.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Entity
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

    @ManyToMany
    @JoinTable(
        name = "establecimiento_usuario",
        joinColumns = @JoinColumn(name = "establecimiento_id"),
        inverseJoinColumns = @JoinColumn(name = "usuario_id")
    )
    private List<Usuario> usuarios;

    private boolean activo = true;

    private LocalDate fechaAlta;
}

