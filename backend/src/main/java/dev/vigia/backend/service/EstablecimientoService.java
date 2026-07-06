package dev.vigia.backend.service;

import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.dto.EstablecimientoResponse;

import java.util.List;

public interface EstablecimientoService {
    EstablecimientoResponse createEstablecimiento(EstablecimientoRequest request);
    List<EstablecimientoResponse> getAllEstablecimientos();
    EstablecimientoResponse getEstablecimientoById(Long id);
    EstablecimientoResponse updateEstablecimiento(Long id, EstablecimientoRequest request);
    EstablecimientoResponse desactivarEstablecimiento(Long id);
}
