package dev.vigia.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * Detalle específico para alertas AISLAMIENTO_SOCIAL: los animales que se alejaron de su rodeo
 * (el de más tiempo fuera primero), cuándo empezó el primer aislamiento y el nombre del rodeo.
 */
@Data
@Builder
public class DetalleAislamientoResponse {

    private List<AnimalAisladoResponse> animalesAislados;
    private LocalDateTime tsInicio;
    private String nombreLote;
    /** Horas de telemetría que analiza el worker: el tiempo acumulado fuera del grupo es dentro de esta ventana. */
    private Integer ventanaHoras;
}
