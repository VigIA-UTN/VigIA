package dev.vigia.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RegistroPesoRequest {

    @NotNull(message = "El peso es obligatorio")
    @Positive(message = "El peso debe ser un valor positivo")
    private Double valorKg;

    @NotNull(message = "La unidad es obligatoria")
    private String unidad;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;
}
