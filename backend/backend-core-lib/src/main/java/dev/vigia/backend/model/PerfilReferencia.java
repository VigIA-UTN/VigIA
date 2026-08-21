package dev.vigia.backend.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "perfiles_referencia")
@Getter
@Setter
@NoArgsConstructor
public class PerfilReferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "animal_id", nullable = false, unique = true)
    private Animal animal;

    @Column(name = "perfil_json", columnDefinition = "TEXT", nullable = false)
    private String perfilJson;

    @Column(name = "fecha_calculo", nullable = false)
    private LocalDateTime fechaCalculo;
}
