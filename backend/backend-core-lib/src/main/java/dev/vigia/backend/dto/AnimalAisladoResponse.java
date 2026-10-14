package dev.vigia.backend.dto;

import java.time.Duration;
import java.time.LocalDateTime;

import dev.vigia.backend.model.AlertaAnimalAislado;
import dev.vigia.backend.model.Animal;
import lombok.Builder;
import lombok.Data;

/**
 * Un animal listado en una alerta AISLAMIENTO_SOCIAL. minutosFuera es el tiempo acumulado fuera del
 * grupo dentro de la ventana de 24 h que analiza el worker, así que nunca supera 1440.
 * minutosFueraSeguidos es la duración de este aislamiento; si la caravana deja de transmitir
 * mientras está afuera, sigue contando (no se la vio volver).
 */
@Data
@Builder
public class AnimalAisladoResponse {

    private Long animalId;
    private String identificador;
    private String alias;
    private Long minutosFuera;
    /** Minutos seguidos fuera en este aislamiento: desde que se alejó hasta ahora, o hasta que volvió. */
    private Long minutosFueraSeguidos;
    private Long distanciaGrupoM;
    /** ALTA si el animal está a más del doble del umbral del establecimiento; si no, MEDIA. */
    private String severidad;

    public static AnimalAisladoResponse from(AlertaAnimalAislado episodio, double umbral) {
        Animal animal = episodio.getAnimal();
        Double distancia = episodio.getDistanciaGrupoM();
        LocalDateTime hasta = episodio.getTsRegreso() != null ? episodio.getTsRegreso() : LocalDateTime.now();
        return AnimalAisladoResponse.builder()
                .animalId(animal.getId())
                .identificador(animal.getIdCaravanaOficial())
                .alias(animal.getAlias())
                .minutosFuera(episodio.getSegundosFuera() / 60)
                .minutosFueraSeguidos(Duration.between(episodio.getTsInicio(), hasta).toMinutes())
                .distanciaGrupoM(distancia != null ? Math.round(distancia) : null)
                .severidad(distancia != null && distancia > 2 * umbral ? "ALTA" : "MEDIA")
                .build();
    }
}
