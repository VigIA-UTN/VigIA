package dev.vigia.backend.dto;

import java.time.LocalDate;

import dev.vigia.backend.model.TipoReproductivo;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EstadoReproductivoRequest {

    @NotNull(message = "El estado reproductivo es obligatorio")
    private TipoReproductivo estado;

    private LocalDate fechaUltimoParto;

    private LocalDate fechaUltimaInseminacion;

}
