package dev.vigia.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class AnimalMetricaResponse {
    private LocalDate fecha;
    private Double actividadMotora;
    private Double latitud;
    private Double longitud;
    private boolean sinDatos;
}
