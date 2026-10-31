package dev.vigia.backend.dto;

import java.time.LocalDate;

import dev.vigia.backend.model.TipoReproductivo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoReproductivoCsvRequest {
    // Número de fila en el archivo (la 1 es el encabezado), para informar los errores que surgen al registrar.
    private int fila;
    private String idCaravana;
    private String alias;
    private TipoReproductivo estado;
    private LocalDate fechaUltimoParto;
    private LocalDate fechaUltimaInseminacion;
}
