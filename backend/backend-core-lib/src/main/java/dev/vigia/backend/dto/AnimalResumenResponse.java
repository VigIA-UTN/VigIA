package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lo que el Dashboard muestra de un animal: su nombre y a qué ficha lleva. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnimalResumenResponse {
    private Long id;
    private String idCaravanaOficial;
    private String alias;
    private String rodeoLoteNombre;
}
