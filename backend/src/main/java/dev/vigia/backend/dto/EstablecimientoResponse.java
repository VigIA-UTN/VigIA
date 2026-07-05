package dev.vigia.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class EstablecimientoResponse {
    private Long id;
    private String nombre;
    private Double latitud;
    private Double longitud;
    private boolean activo;
    private LocalDate fechaAlta;
}
