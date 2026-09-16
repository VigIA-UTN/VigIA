package dev.vigia.backend.dto;

import java.time.LocalDate;

import dev.vigia.backend.model.EstadoAnimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AnimalRequest {
    private String idCaravanaOficial;
    private String alias;
    @NotNull(message = "La fecha de nacimiento es obligatoria")
    private LocalDate fechaNacimiento;
    private String raza;
    @NotNull(message = "El rodeo/lote es obligatorio")
    private Long rodeoLoteId;
    private EstadoAnimal estadoActual;
    @NotNull(message = "El establecimiento es obligatorio")
    private Long establecimientoId;

    /** Peso inicial del animal en kg (opcional). Si se provee, se crea el primer registro de peso. */
    @Positive(message = "El peso inicial debe ser un valor positivo")
    private Double pesoInicial;

    /** Id de la caravana LoRaWAN a vincular (opcional). */
    private Long caravanaId;
}
