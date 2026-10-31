package dev.vigia.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RodeoLoteRequest {

    @NotBlank(message = "El nombre del rodeo/lote es obligatorio")
    @Size(max = 255, message = "El nombre del rodeo/lote no puede superar los 255 caracteres")
    private String nombre;

    @NotNull(message = "El establecimiento es obligatorio")
    private Long establecimientoId;

}
