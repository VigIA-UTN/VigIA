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

    @Column(name = "move_state")
    private Integer moveState;

    @Column(name = "nivel_rumia")
    private Double nivelRumia;

    private Double bateria;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_inferencia_celo")
    private EstadoInferencia estadoInferenciaCelo = EstadoInferencia.PENDIENTE;

    // Campo de estado INDEPENDIENTE del de celo (estadoInferenciaCelo de arriba).
    // Antes celo y salud compartian un unico flag "estado_inferencia": el
    // job que corriera primero en cada ciclo (celo, ver BatchScheduler) lo
    // marcaba COMPLETADO para TODA la telemetria de la caravana, y el job
    // de salud -- que corre segundos despues en el mismo ciclo -- nunca
    // encontraba nada PENDIENTE para esa caravana y jamas llegaba a llamar
    // a ml-salud. Cada pipeline necesita su propio flag de "ya lo mire".
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_inferencia_salud")
    private EstadoInferencia estadoInferenciaSalud = EstadoInferencia.PENDIENTE;
}
