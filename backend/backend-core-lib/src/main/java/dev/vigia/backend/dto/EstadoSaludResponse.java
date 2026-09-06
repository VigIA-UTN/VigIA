package dev.vigia.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoSaludResponse {

    private Long id;
    private Long animalId;
    private String estado;
    private LocalDate fechaRevision;
    private String diagnosticoNotas;
    private String tratamientoIndicado;
    private String origen;
    private LocalDateTime fechaRegistro;
    private String registradoPor;
}
