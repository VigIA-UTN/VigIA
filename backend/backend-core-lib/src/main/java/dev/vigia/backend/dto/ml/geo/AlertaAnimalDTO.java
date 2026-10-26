package dev.vigia.backend.dto.ml.geo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertaAnimalDTO {
    @JsonProperty("animal_id")
    private String animalId;

    @JsonProperty("indice_cohesion")
    private Double indiceCohesion;

    @JsonProperty("distancia_centroide_m")
    private Double distanciaCentroideM;

    @JsonProperty("alerta_aislamiento")
    private Boolean alertaAislamiento;

    @JsonProperty("tiempo_fuera_grupo_s")
    private Double tiempoFueraGrupoS;

    /** Tramo final fuera del grupo sin volver, en segundos (cuánto lleva alejado ahora). */
    @JsonProperty("tiempo_fuera_continuo_s")
    private Double tiempoFueraContinuoS;
}
