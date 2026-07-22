package dev.vigia.backend.service;

import java.util.List;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.model.EstadoAlerta;

public interface AlertaService {

    /**
     * Retorna las alertas de celo del establecimiento seleccionado por el usuario
     * en la página principal (toggle de establecimientos en el mapa),
     * opcionalmente filtradas por estado.
     *
     * <p>Valida que el usuario autenticado pertenezca realmente al establecimiento
     * solicitado antes de devolver datos (aislamiento multi-tenant).
     *
     * @param emailUsuario      email del usuario autenticado (extraído del JWT)
     * @param establecimientoId ID del establecimiento actualmente seleccionado en el frontend
     * @param estado            filtro opcional de estado (null = todos los estados)
     * @return lista de alertas ordenadas por tsGeneracion DESC
     * @throws AccessDeniedException     si el usuario no pertenece al establecimiento indicado
     * @throws IllegalArgumentException  si el usuario no existe
     */
    List<AlertaResponse> listarAlertasCelo(String emailUsuario, Long establecimientoId, EstadoAlerta estado);
}
