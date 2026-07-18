package dev.vigia.backend.dto;

import java.time.LocalDate;

import dev.vigia.backend.model.TipoReproductivo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoReproductivoCsvRequest {
    private Long animalId;
    private TipoReproductivo estado;
    private LocalDate fechaUltimoParto;
    private LocalDate fechaUltimaInseminacion;
}
