package dev.vigia.backend.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

    public static final String TIPO_ANESTRO = "Anestro Detectado";
    public static final String TIPO_CELO = "Celo Detectado";

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

    @Column(name = "latitud")
    private Double latitud;

    @Column(name = "longitud")
    private Double longitud;

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

    @Column(name = "confianza_modelo")
    private Float confianzaModelo;

    @Column(name = "ventana_optima_horas")
    private Integer ventanaOptimaHoras;

    @Column(name = "desvio_detectado")
    private Boolean desvioDetectado;

    @Column(name = "posible_causa")
    private String posibleCausa;

    @Column(name = "serie_comparativa_json", columnDefinition = "TEXT")
    private String serieComparativaJson;

    @OneToMany(mappedBy = "alerta", fetch = FetchType.LAZY)
    @OrderBy("fecha DESC")
    private List<Diagnostico> diagnosticos = new ArrayList<>();
}
