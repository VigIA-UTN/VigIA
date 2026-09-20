package dev.vigia.backend.dto;

import java.time.LocalDateTime;

import dev.vigia.backend.model.EstadoAlerta;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertaResponse {
    private Long id;
    private String tipo;
    private String severidad;
    private EstadoAlerta estado;
    private Long animalId;
    private LocalDateTime tsGeneracion;
    private String detalle;
    private String accionRecomendada;
}
