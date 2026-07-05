package dev.vigia.backend.dto;

import lombok.Data;

@Data
public class EstablecimientoRequest {
    private String nombre;
    private Double latitud;
    private Double longitud;
}
