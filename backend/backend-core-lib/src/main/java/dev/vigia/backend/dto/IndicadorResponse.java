package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicadorResponse {
    private String nombre;
    private String descripcion;
    private String valor;
    private String estado;
}
