package dev.vigia.backend.service;

import dev.vigia.backend.dto.ConfiguracionAnestroRequest;
import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.dto.EstablecimientoResponse;

import java.util.List;

public interface EstablecimientoService {
    EstablecimientoResponse createEstablecimiento(EstablecimientoRequest request);
    List<EstablecimientoResponse> getAllEstablecimientos();
    List<EstablecimientoResponse> getEstablecimientosByUsuario(String email);
    EstablecimientoResponse getEstablecimientoById(Long id);
    EstablecimientoResponse updateEstablecimiento(Long id, EstablecimientoRequest request, String emailUsuario, boolean esAdmin);
    EstablecimientoResponse actualizarConfiguracionAnestro(Long id, ConfiguracionAnestroRequest request, String emailUsuario);
    EstablecimientoResponse desactivarEstablecimiento(Long id);
    EstablecimientoResponse activarEstablecimiento(Long id);
}
