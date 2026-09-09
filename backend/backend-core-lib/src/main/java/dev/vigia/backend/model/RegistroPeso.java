package dev.vigia.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "registros_peso")
@Getter
@Setter
@NoArgsConstructor
public class RegistroPeso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "animal_id", nullable = false)
    private Animal animal;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "valor_kg", nullable = false)
    private Double valorKg;

    @Column(name = "unidad", nullable = false)
    private String unidad;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;
}
