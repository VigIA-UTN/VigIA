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
public class RegistroPesoResponse {
    private Long id;
    private Long animalId;
    private LocalDate fecha;
    private Double valorKg;
    private String unidad;
    private LocalDateTime fechaRegistro;
}
