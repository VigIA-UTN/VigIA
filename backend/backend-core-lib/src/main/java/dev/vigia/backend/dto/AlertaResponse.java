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
    // Tipo crudo de la alerta ("Anestro Detectado", "Celo Detectado", ...); tipo lleva el disparador.
    private String subtipo;
    private String severidad;
    private EstadoAlerta estado;
    private Long animalId;
    private LocalDateTime tsGeneracion;
    private Double latitud;
    private Double longitud;
    private String detalle;
    private String accionRecomendada;
}
