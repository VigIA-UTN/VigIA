package dev.vigia.backend.dto;

import java.time.LocalDate;

import dev.vigia.backend.model.EstadoAnimal;
import lombok.Data;

@Data
public class AnimalRequest {
    private String idCaravanaOficial;
    private String alias;
    private LocalDate fechaNacimiento;
    private String sexo;
    private String raza;
    private Long rodeoLoteId;
    private EstadoAnimal estadoActual;
    private Long establecimientoId;
}
