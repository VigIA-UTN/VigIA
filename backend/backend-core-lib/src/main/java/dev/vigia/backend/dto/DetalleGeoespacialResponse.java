package dev.vigia.backend.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Detalle específico para alertas de tipo FUERA_DE_PERIMETRO.
 * Expone la posición exacta donde el animal salió del perímetro,
 * el lote al que pertenece y el tiempo transcurrido desde la salida.
 */
@Data
@Builder
public class DetalleGeoespacialResponse {

    private Double latitudSalida;
    private Double longitudSalida;
    private String nombreLote;
    private String nombrePerimetro;
    private Long segundosFuera;
}
