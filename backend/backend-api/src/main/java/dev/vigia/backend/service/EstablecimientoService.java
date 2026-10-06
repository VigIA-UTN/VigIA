package dev.vigia.backend.service;

import dev.vigia.backend.dto.ConfigAnestroRequest;
import dev.vigia.backend.dto.ConfigAnestroResponse;
import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.dto.EstablecimientoResponse;
import dev.vigia.backend.dto.IntervencionResponse;

import java.util.List;

public interface EstablecimientoService {
    EstablecimientoResponse createEstablecimiento(EstablecimientoRequest request);

    List<EstablecimientoResponse> getAllEstablecimientos();

    List<EstablecimientoResponse> getEstablecimientosByUsuario(String email);

    EstablecimientoResponse getEstablecimientoById(Long id);
    EstablecimientoResponse updateEstablecimiento(Long id, EstablecimientoRequest request, String emailUsuario, boolean esAdmin);
    EstablecimientoResponse desactivarEstablecimiento(Long id);

    EstablecimientoResponse activarEstablecimiento(Long id);

    List<IntervencionResponse> obtenerIntervenciones(String emailUsuario, Long establecimientoId);

    /**
     * Consulta los parámetros de anestro de un establecimiento.
     * Solo puede ser invocado por un PRODUCTOR o VETERINARIO activo del establecimiento.
     *
     * @param establecimientoId ID del establecimiento
     * @param emailUsuario      email del usuario autenticado
     * @return config de anestro vigente
     * @throws org.springframework.security.access.AccessDeniedException si el usuario no pertenece al establecimiento
     */
    ConfigAnestroResponse getConfigAnestro(Long establecimientoId, String emailUsuario);

    /**
     * Actualiza los parámetros de anestro de un establecimiento.
     * Solo puede ser invocado por un PRODUCTOR o VETERINARIO activo del establecimiento.
     * Valida rangos: dias 1–365, severidad dentro de {BAJA, MEDIA, ALTA}.
     *
     * @param establecimientoId ID del establecimiento
     * @param request           nuevos parámetros
     * @param emailUsuario      email del usuario autenticado
     * @return config de anestro actualizada
     * @throws IllegalArgumentException                                  si los valores están fuera de rango
     * @throws org.springframework.security.access.AccessDeniedException si el usuario no pertenece al establecimiento
     */
    ConfigAnestroResponse updateConfigAnestro(Long establecimientoId, ConfigAnestroRequest request, String emailUsuario);
}
