package dev.vigia.backend.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilCalibracionRequestDTO {
    private List<PuntoTelemetriaDTO> calibracion;
}
