package dev.vigia.backend.dto.ml;

import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.SeveridadAlerta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertaEventDTO {
    private Long id;
    private Long animalId;
    private String tipo;
    private SeveridadAlerta severidad;
    private LocalDateTime tsGeneracion;
    private DisparadorAlerta disparador;
    private Double valorUmbral;
    private Double valorDetectado;
    private EstadoAlerta estado;
    private String accionRecomendada;
    private Float confianzaModelo;
    private Integer ventanaOptimaHoras;

    // Solo alertas sanitarias (disparador = SALUD).
    private Boolean desvioDetectado;
    private String posibleCausa;
}
