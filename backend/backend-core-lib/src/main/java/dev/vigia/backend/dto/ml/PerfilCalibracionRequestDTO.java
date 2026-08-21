package dev.vigia.backend.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request para POST /api/internal/ml/salud/perfil: telemetría cruda del
 * período de calibración (RF03, >= 7 días) de UN animal. Se llama una
 * sola vez (o cuando toca recalibrar), no en cada predicción -- ver
 * SaludDetectorStrategyImpl.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilCalibracionRequestDTO {
    private List<PuntoTelemetriaDTO> calibracion;
}
