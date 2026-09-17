package dev.vigia.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.model.TipoEstadoSalud;
import dev.vigia.backend.model.TipoReproductivo;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AnimalResponse {
    private Long id;
    private String idCaravanaOficial;
    private String alias;
    private LocalDate fechaNacimiento;
    private String raza;
    private EstadoAnimal estadoActual;
    private Long rodeoLoteId;
    private String rodeoLoteNombre;
    private Long establecimientoId;
    private LocalDate fechaAlta;
    private TipoReproductivo estadoReproductivo;
    private LocalDate fechaUltimoParto;
    private LocalDate fechaUltimaInseminacion;
    private TipoEstadoSalud estadoSalud;

    private Boolean enCalibracion;
    private Integer diasRestantesCalibracion;

    private Long caravanaId;
    private String caravanaDevEui;
    private String caravanaAlias;
    private EstadoSensor caravanaEstado;
    private LocalDateTime caravanaUltimaSenial;
    private Double caravanaBateria;
}

