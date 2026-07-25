package dev.vigia.backend.model;

import java.time.LocalDateTime;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "alertas")
@Getter
@Setter
@NoArgsConstructor
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "animal_id")
    private Animal animal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_confirmacion_id")
    private Usuario usuarioConfirmacion;

    private String tipo;

    @Enumerated(EnumType.STRING)
    private SeveridadAlerta severidad;

    @Column(name = "ts_generacion", nullable = false)
    private LocalDateTime tsGeneracion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisparadorAlerta disparador;

    @Column(name = "valor_umbral", nullable = false)
    private Double valorUmbral;

    @Column(name = "valor_detectado")
    private Double valorDetectado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoAlerta estado;

    @Column(name = "ts_confirmacion")
    private LocalDateTime tsConfirmacion;

    @Column(name = "motivo_descarte")
    private String motivoDescarte;

    @Column(name = "ventana_inicio_ins")
    private LocalDateTime ventanaInicioIns;

    @Column(name = "ventana_fin_ins")
    private LocalDateTime ventanaFinIns;

    @Column(name = "accion_recomendada")
    private String accionRecomendada;

    @Column(name = "confianza_modelo") // TODO: REVISAR
    private Float confianzaModelo;

    @Column(name = "ventana_optima_horas") // TODO: REVISAR
    private Integer ventanaOptimaHoras;
}
