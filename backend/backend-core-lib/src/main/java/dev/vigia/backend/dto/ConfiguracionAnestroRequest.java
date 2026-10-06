package dev.vigia.backend.dto;

import dev.vigia.backend.model.SeveridadAlerta;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfiguracionAnestroRequest {
    @NotNull
    @Min(1)
    @Max(365)
    private Integer diasDeteccionAnestro;

    @NotNull
    private SeveridadAlerta severidadAnestro;
}
