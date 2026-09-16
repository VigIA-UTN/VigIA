package dev.vigia.backend.dto;

import java.time.LocalDate;

import dev.vigia.backend.model.EstadoSensor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaravanaResponse {
    private Long id;
    private String codigo;
    private LocalDate fechaAsignacion;
    private LocalDate fechaAlta;
    private String devEui;
    private Boolean activo;
    private EstadoSensor estadoSensor;
}
