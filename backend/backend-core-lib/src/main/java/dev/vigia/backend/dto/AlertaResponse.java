package dev.vigia.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

import dev.vigia.backend.model.EstadoAlerta;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertaResponse {
    private Long id;
    private String tipo;
    private String subtipo;
    private String severidad;
    private EstadoAlerta estado;
    private Long animalId;
    private LocalDateTime tsGeneracion;
    private LocalDateTime tsConfirmacion;
    private Double latitud;
    private Double longitud;
    private String detalle;
    private String accionRecomendada;

    private LocalDateTime caravanaUltimaSenial;
    private boolean datoPosiblementeDesactualizado;
    private long horasUmbralDatoDesactualizado;

    /** Solo AISLAMIENTO_SOCIAL: animales que se alejaron del grupo. */
    private List<AnimalAisladoResponse> animalesAislados;
}
