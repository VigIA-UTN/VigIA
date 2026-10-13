package dev.vigia.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Bloque de aislamiento del detalle de alerta (solo AISLAMIENTO_SOCIAL). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetalleAislamientoResponse {
    /** Animales del mismo rodeo que se alejaron, con más tiempo fuera primero. */
    private List<AnimalAisladoResponse> animalesAislados;
    /** Generación de la alerta más antigua del grupo. */
    private LocalDateTime tsInicio;
    private String nombreLote;
}
