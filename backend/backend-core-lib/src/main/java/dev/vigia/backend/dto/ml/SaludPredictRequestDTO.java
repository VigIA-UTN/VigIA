package dev.vigia.backend.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaludPredictRequestDTO {
    private List<PuntoTelemetriaDTO> serie;
    private Map<String, Object> perfil;
}
