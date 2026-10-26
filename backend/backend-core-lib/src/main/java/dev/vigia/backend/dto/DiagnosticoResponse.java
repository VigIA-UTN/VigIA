package dev.vigia.backend.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiagnosticoResponse {
    private Long id;
    private Long alertaId;
    private Long animalId;
    private String animalAlias;
    private String animalCaravanaOficial;
    private String establecimientoNombre;
    private String diagnostico;
    // Indicación o tratamiento (columna "indicaciones").
    private String tratamiento;
    private LocalDateTime fechaRegistro;
    private String veterinarioNombre;
    // Dato de origen de la alerta que motivó el diagnóstico.
    private String disparador;
    private String datoOrigen;
}
