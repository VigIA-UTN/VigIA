package dev.vigia.backend.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Detalle específico para alertas de tipo FUERA_DE_PERIMETRO.
 * Expone la última posición conocida del animal mientras el episodio está activo,
 * el lote al que pertenece y el tiempo transcurrido desde la salida.
 */
@Data
@Builder
public class DetalleGeoespacialResponse {

    private Double latitud;
    private Double longitud;
    private String nombreLote;
    private Long segundosFuera;
}
