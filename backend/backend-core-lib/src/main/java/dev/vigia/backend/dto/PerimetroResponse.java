package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerimetroResponse {

    private Long id;
    private Long establecimientoId;
    private String establecimientoNombre;
    private String nombre;
    private String descripcion;
    private boolean activo;
    @Builder.Default
    private List<Long> loteIds = new ArrayList<>();
    @Builder.Default
    private List<CoordenadaDTO> coordenadas = new ArrayList<>();
    private BigDecimal distanciaToleranciaMetros;
    private boolean zonaEstricta;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
    private Long version;
}
