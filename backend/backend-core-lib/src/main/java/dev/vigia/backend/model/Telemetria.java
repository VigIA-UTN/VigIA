package dev.vigia.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.DynamicUpdate;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// UPDATE solo con las columnas modificadas: celo, salud y geo corren en hilos distintos y cada uno completa su
// estado_inferencia_*; con el UPDATE de todas las columnas, el último en confirmar pisaba el estado del otro.
@Entity
@DynamicUpdate
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

    @Column(name = "move_state")
    private Integer moveState;

    private Double bateria;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_inferencia_celo")
    private EstadoInferencia estadoInferenciaCelo = EstadoInferencia.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_inferencia_salud")
    private EstadoInferencia estadoInferenciaSalud = EstadoInferencia.PENDIENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_inferencia_geo")
    private EstadoInferencia estadoInferenciaGeo = EstadoInferencia.PENDIENTE;
}
