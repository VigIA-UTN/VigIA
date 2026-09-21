package dev.vigia.backend.service;

import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import java.time.LocalDateTime;

public interface MapaCalorService {
    GeoAnalyzeResponseDTO obtenerMapaCalor(String emailUsuario, Long establecimientoId, LocalDateTime desde, LocalDateTime hasta, Long animalId);
}
