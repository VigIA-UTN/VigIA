package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Un animal que se alejó de su rodeo (alerta AISLAMIENTO_SOCIAL). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnimalAisladoResponse {
    private Long animalId;
    /** Caravana oficial del animal (o su id si no tiene). */
    private String identificador;
    private String alias;
    /** Tiempo acumulado fuera del grupo en la ventana analizada, en minutos. */
    private long minutosFuera;
    /** Distancia al centroide del grupo en el último análisis, en metros. */
    private long distanciaGrupoM;
    private String severidad;
}
