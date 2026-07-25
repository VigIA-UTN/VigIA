package dev.vigia.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "telemetria")
@Getter
@Setter
@NoArgsConstructor
public class Telemetria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caravana_id", nullable = false)
    private Caravana caravana;

    private Double latitud;
    private Double longitud;

    @Column(name = "accel_x")
    private Double accelX;

    @Column(name = "accel_y")
    private Double accelY;

    @Column(name = "accel_z")
    private Double accelZ;

    @Column(name = "nivel_rumia")
    private Double nivelRumia;

    private Double bateria;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_inferencia")
    private EstadoInferencia estadoInferencia = EstadoInferencia.PENDIENTE;
}
