package dev.vigia.backend.dto.ml;

import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.SeveridadAlerta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import dev.vigia.backend.dto.AnimalAisladoResponse;

import java.time.LocalDateTime;
import java.util.List;

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

    private Boolean desvioDetectado;
    private String posibleCausa;

    private Double latitud;
    private Double longitud;

    /** Solo AISLAMIENTO_SOCIAL: lo arma el worker porque sus filas todavía no están commiteadas al publicar. */
    private List<AnimalAisladoResponse> animalesAislados;
}
