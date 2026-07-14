package dev.vigia.backend.dto;

import java.time.LocalDate;

import dev.vigia.backend.model.EstadoAnimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnimalResponse {
    private Long id;
    private String idCaravanaOficial;
    private String alias;
    private LocalDate fechaNacimiento;
    private String sexo;
    private String raza;
    private EstadoAnimal estadoActual;
    private Long rodeoLoteId;
    private Long establecimientoId;
    private LocalDate fechaAlta;
}
