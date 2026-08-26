package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Respuesta de GET /api/v1/ingesta/ultima-posicion/{deviceId}.
 *
 * Pensado para que el simulador (ver simulador/simulador_demo.py) pueda
 * arrancar continuando la última posición real que ya tiene el animal en la
 * base (sembrada por DataInitializer, o dejada por una corrida anterior del
 * propio simulador), en vez de depender de coordenadas hardcodeadas que
 * podían desincronizarse — ver el comentario de BUGFIX en simulador.py sobre
 * la alerta de celo falsa que causaba ese desfasaje.
 */
@Data
@AllArgsConstructor
public class UltimaPosicionResponseDTO {
    private Double latitud;
    private Double longitud;
    private Double bateria;
    private Integer moveState;
    private LocalDateTime fechaHora;
}
