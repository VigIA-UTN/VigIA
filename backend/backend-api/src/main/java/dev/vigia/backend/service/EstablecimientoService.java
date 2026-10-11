package dev.vigia.backend.service;

import dev.vigia.backend.dto.ConfigAislamientoRequest;
import dev.vigia.backend.dto.ConfigAislamientoResponse;
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

    /**
     * Consulta el umbral de aislamiento de un establecimiento.
     * Solo puede ser invocado por un PRODUCTOR o VETERINARIO activo del establecimiento.
     * Si no fue configurado previamente, devuelve el valor por defecto (100.0 m).
     *
     * @param establecimientoId ID del establecimiento
     * @param emailUsuario      email del usuario autenticado
     * @return configuración de aislamiento vigente
     * @throws org.springframework.security.access.AccessDeniedException si el usuario no pertenece al establecimiento
     */
    ConfigAislamientoResponse getConfigAislamiento(Long establecimientoId, String emailUsuario);

    /**
     * Actualiza el umbral de aislamiento de un establecimiento.
     * Solo puede ser invocado por un PRODUCTOR o VETERINARIO activo del establecimiento.
     * Valida que el valor se encuentre en el rango permitido (10.0 a 1000.0 metros).
     *
     * @param establecimientoId ID del establecimiento
     * @param request           nuevo umbral
     * @param emailUsuario      email del usuario autenticado
     * @return configuración de aislamiento actualizada
     * @throws IllegalArgumentException                                  si el valor está fuera de rango o es nulo
     * @throws org.springframework.security.access.AccessDeniedException si el usuario no pertenece al establecimiento
     */
    ConfigAislamientoResponse updateConfigAislamiento(Long establecimientoId, ConfigAislamientoRequest request, String emailUsuario);
}
