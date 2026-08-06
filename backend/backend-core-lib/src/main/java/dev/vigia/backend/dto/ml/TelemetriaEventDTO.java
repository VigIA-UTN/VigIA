package dev.vigia.backend.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetriaEventDTO {
    private Long animalId;
    private Long establecimientoId;
    private Double latitud;
    private Double longitud;
    private LocalDateTime fechaHora;
}
