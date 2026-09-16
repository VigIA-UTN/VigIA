package dev.vigia.backend.dto;

import dev.vigia.backend.model.EstadoSensor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorResponse {
    private Long id;
    private String devEui;
    private String alias;
    private EstadoSensor estadoSensor;
    private Long establecimientoId;
    private Long animalId;
    private String animalAlias;
}
