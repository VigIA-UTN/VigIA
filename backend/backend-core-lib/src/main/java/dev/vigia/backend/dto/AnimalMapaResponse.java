package dev.vigia.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AnimalMapaResponse {
    private Long id;
    private String identificador;
    private Double latitud;
    private Double longitud;
    private LocalDateTime tsUltimaPosicion;
    private boolean sinDatosRecientes;
}
