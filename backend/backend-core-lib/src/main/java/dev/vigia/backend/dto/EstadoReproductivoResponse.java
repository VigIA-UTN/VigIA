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
public class EstadoReproductivoResponse {
    
    private Long id;
    private Long animalId;
    private String estado;
    private LocalDate fechaUltimoParto;
    private LocalDate fechaUltimaInseminacion;
    private String origen;
    private LocalDateTime fechaRegistro;
    private String registradoPor;
}
