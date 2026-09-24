package dev.vigia.backend.model;

import dev.vigia.backend.util.GeometryUtils;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "perimetro")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Perimetro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "establecimiento_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Establecimiento establecimiento;

    @Column(nullable = false)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Convert(disableConversion = true)
    @Column(nullable = false)
    private boolean activo = true;

    @Column(columnDefinition = "geometry(Polygon,4326)", nullable = false)
    private Polygon area;

    @Column(name = "distancia_tolerancia_metros", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanciaToleranciaMetros = BigDecimal.ZERO;

    @Convert(disableConversion = true)
    @Column(name = "zona_estricta", nullable = false)
    private boolean zonaEstricta = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "perimetro_lote", joinColumns = @JoinColumn(name = "perimetro_id"), inverseJoinColumns = @JoinColumn(name = "rodeo_lote_id"))
    private Set<RodeoLote> lotes = new HashSet<>();

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    @Version
    @Column(nullable = false)
    private Long version;

    public Perimetro(Establecimiento establecimiento, String nombre, Polygon area) {
        if (establecimiento == null) {
            throw new IllegalArgumentException("El establecimiento no puede ser nulo.");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del perímetro no puede estar vacío.");
        }
        GeometryUtils.validarPoligono(area);

        this.establecimiento = establecimiento;
        this.nombre = nombre;
        this.area = area;
        this.activo = true;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Perimetro(Establecimiento establecimiento, String nombre, String descripcion, Polygon area) {
        this(establecimiento, nombre, area);
        this.descripcion = descripcion;
    }

    @PrePersist
    protected void onPrePersist() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onPreUpdate() {
        this.fechaModificacion = LocalDateTime.now();
    }

    public void activar() {
        this.activo = true;
        this.fechaModificacion = LocalDateTime.now();
    }

    public void desactivar() {
        this.activo = false;
        this.fechaModificacion = LocalDateTime.now();
    }

    public void actualizarArea(Polygon nuevoArea) {
        GeometryUtils.validarPoligono(nuevoArea);
        this.area = nuevoArea;
        this.fechaModificacion = LocalDateTime.now();
    }

    public void actualizarDatos(String nombre, String descripcion) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del perímetro no puede estar vacío.");
        }
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.fechaModificacion = LocalDateTime.now();
    }

    public void configurarUmbral(BigDecimal distanciaToleranciaMetros, boolean zonaEstricta) {
        if (distanciaToleranciaMetros == null) {
            throw new IllegalArgumentException("La distancia de tolerancia no puede ser nula.");
        }
        if (distanciaToleranciaMetros.signum() < 0) {
            throw new IllegalArgumentException("La distancia de tolerancia no puede ser negativa.");
        }
        this.distanciaToleranciaMetros = distanciaToleranciaMetros;
        this.zonaEstricta = zonaEstricta;
    }

    public BigDecimal getToleranciaEfectivaMetros() {
        return zonaEstricta ? BigDecimal.ZERO : distanciaToleranciaMetros;
    }

    public void asignarLote(RodeoLote lote) {
        if (lote != null) {
            this.lotes.add(lote);
        }
    }

    public void removerLote(RodeoLote lote) {
        if (lote != null) {
            this.lotes.remove(lote);
        }
    }

    public void sincronizarLotes(Collection<RodeoLote> nuevosLotes) {
        this.lotes.clear();
        if (nuevosLotes != null) {
            this.lotes.addAll(nuevosLotes);
        }
    }

    public Set<RodeoLote> getLotes() {
        return Collections.unmodifiableSet(this.lotes);
    }
}
