package dev.vigia.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaravanaRequest {

    @NotNull(message = "El identificador de la caravana es obligatorio.")
    private String idCaravanaOficial;

    @NotNull(message = "El identificador del establecimiento es obligatorio.")
    private Long establecimientoId;

    @NotNull(message = "El codigo es obligatorio.")
    private String dev_eui;

}
