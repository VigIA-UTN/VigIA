package dev.vigia.backend.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response de POST /api/internal/ml/salud/perfil. `perfil` es el blob
 * opaco (ver SaludPredictRequestDTO) que el backend persiste en
 * una entidad PerfilReferencia (1:1 con el animal) y reenvía tal
 * cual en /predict.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfilCalibracionResponseDTO {
    private Map<String, Object> perfil;
}
