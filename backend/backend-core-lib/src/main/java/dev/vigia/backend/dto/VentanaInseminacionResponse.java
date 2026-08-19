package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VentanaInseminacionResponse {
    private Long tiempoRestanteSegundos;
    private Double porcentajeTranscurrido;
    private Boolean proximaAVencer;
    private String estado;
}
