package dev.vigia.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "caravanas")
@Getter
@Setter
@NoArgsConstructor
public class Caravana {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(name = "fecha_asignacion")
    private LocalDate fechaAsignacion;

    @Column(name = "ultima_senial")
    private LocalDateTime ultimaSenial;

    @Column(name = "fecha_alta")
    private LocalDate fechaAlta;

    @Column(nullable = false)
    private boolean activo = true;

    @OneToOne(mappedBy = "caravana", fetch = FetchType.LAZY)
    private Animal animal;

    @Column(name = "dev_eui", nullable = false, unique = true)
    private String devEui;

    @Column(name = "ultimo_frame_counter")
    private Integer ultimoFrameCounter;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_sensor")
    private EstadoSensor estadoSensor;
}
