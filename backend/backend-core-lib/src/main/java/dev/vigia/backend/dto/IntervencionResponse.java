package dev.vigia.backend.dto;

import java.time.LocalDateTime;

import dev.vigia.backend.model.TipoIntervencion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntervencionResponse {
    private Long id;
    private Long alertaId;
    private Long animalId;
    private String interventor;
    private String descripcion;
    private LocalDateTime fechaHora;
    private TipoIntervencion tipo;
}
