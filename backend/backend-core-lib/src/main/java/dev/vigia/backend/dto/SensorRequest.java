package dev.vigia.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorRequest {

    @NotBlank(message = "El dev_eui es obligatorio.")
    private String dev_eui;

    private String alias;

    @NotNull(message = "El identificador del establecimiento es obligatorio.")
    private Long establecimientoId;
}
