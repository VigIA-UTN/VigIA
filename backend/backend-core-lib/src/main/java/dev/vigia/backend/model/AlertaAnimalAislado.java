package dev.vigia.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Episodio de aislamiento social: un animal fuera del grupo de su rodeo, listado en una
 * alerta AISLAMIENTO_SOCIAL. El episodio está abierto mientras tsRegreso es null; la base
 * garantiza uno solo abierto por animal (uq_aislamiento_abierto_por_animal).
 */
@Entity
@Table(name = "alerta_animales_aislados",
        uniqueConstraints = @UniqueConstraint(name = "uq_alerta_animal_aislado", columnNames = { "alerta_id", "animal_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertaAnimalAislado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alerta_id", nullable = false)
    private Alerta alerta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "animal_id", nullable = false)
    private Animal animal;

    /** Tiempo acumulado fuera del grupo según ml-geo, sobre la ventana (vigia.geo.ventana-horas) que manda el worker. */
    @Column(name = "segundos_fuera", nullable = false)
    private Long segundosFuera;

    @Column(name = "distancia_grupo_m")
    private Double distanciaGrupoM;

    @Column(name = "ts_inicio", nullable = false)
    private LocalDateTime tsInicio;

    /** Cuándo se cerró el episodio (volvió o dejó de transmitir); null mientras sigue abierto. */
    @Column(name = "ts_regreso")
    private LocalDateTime tsRegreso;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_cierre", length = 20)
    private MotivoCierreAislamiento motivoCierre;
}
