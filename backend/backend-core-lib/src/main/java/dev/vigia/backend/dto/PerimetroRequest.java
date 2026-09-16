package dev.vigia.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerimetroRequest {

    @NotBlank(message = "El nombre del perímetro es obligatorio")
    @Size(max = 255, message = "El nombre no puede exceder 255 caracteres")
    private String nombre;

    @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
    private String descripcion;

    @NotNull(message = "El ID del establecimiento es obligatorio")
    private Long establecimientoId;

    @Builder.Default
    private List<Long> loteIds = new ArrayList<>();

    @NotEmpty(message = "Debe proporcionar los vértices del perímetro")
    @Size(min = 3, message = "Se requieren al menos 3 vértices para definir el perímetro")
    @Valid
    private List<CoordenadaDTO> coordenadas;
}
