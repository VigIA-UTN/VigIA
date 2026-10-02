package dev.vigia.backend.service;

import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.dto.EstablecimientoResponse;
import dev.vigia.backend.dto.IntervencionResponse;

import java.util.List;

public interface EstablecimientoService {
    EstablecimientoResponse createEstablecimiento(EstablecimientoRequest request);

    List<EstablecimientoResponse> getAllEstablecimientos();

    List<EstablecimientoResponse> getEstablecimientosByUsuario(String email);

    EstablecimientoResponse getEstablecimientoById(Long id);

    EstablecimientoResponse updateEstablecimiento(Long id, EstablecimientoRequest request);

    EstablecimientoResponse desactivarEstablecimiento(Long id);

    EstablecimientoResponse activarEstablecimiento(Long id);

    List<IntervencionResponse> obtenerIntervenciones(String emailUsuario, Long establecimientoId);
}
