package dev.vigia.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import dev.vigia.backend.dto.AlertaDetalleResponse;
import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.AlertaResumenResponse;
import dev.vigia.backend.dto.IntervencionRequest;
import dev.vigia.backend.dto.LineaTiempoEventoResponse;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.model.EstadoAlerta;

public interface AlertaService {

        /**
         * Retorna el conteo de alertas activas agrupadas por tipo.
         */
        AlertaResumenResponse obtenerResumenAlertas(String emailUsuario, Long establecimientoId);

        /**
         * Retorna alertas de un establecimiento opcionalmente filtradas por tipo,
         * severidad y estado. Ordenadas por severidad descendente.
         */
        List<AlertaResponse> listarAlertas(String emailUsuario, Long establecimientoId, String tipo, String severidad,
                        EstadoAlerta estado);

        /**
         * Retorna las alertas de celo del establecimiento seleccionado por el usuario
         * en la página principal (toggle de establecimientos en el mapa),
         * opcionalmente filtradas por estado.
         *
         * <p>
         * Valida que el usuario autenticado pertenezca realmente al establecimiento
         * solicitado antes de devolver datos (aislamiento multi-tenant).
         *
         * @param emailUsuario      email del usuario autenticado (extraído del JWT)
         * @param establecimientoId ID del establecimiento actualmente seleccionado en
         *                          el frontend
         * @param estado            filtro opcional de estado (null = todos los estados)
         * @return lista de alertas ordenadas por tsGeneracion DESC
         * @throws AccessDeniedException    si el usuario no pertenece al
         *                                  establecimiento indicado
         * @throws IllegalArgumentException si el usuario no existe
         */
        List<AlertaResponse> listarAlertasCelo(String emailUsuario, Long establecimientoId, EstadoAlerta estado);

        /**
         * Retorna el detalle completo de una alerta de celo.
         * Valida que el usuario pertenezca al establecimiento y que la alerta
         * también pertenezca al mismo establecimiento.
         *
         * @param emailUsuario      email del usuario autenticado (extraído del JWT)
         * @param establecimientoId ID del establecimiento actualmente seleccionado en
         *                          el frontend
         * @param alertaId          ID de la alerta a consultar
         * @return DTO con el detalle de la alerta
         * @throws AccessDeniedException    si el usuario no pertenece al
         *                                  establecimiento indicado
         * @throws IllegalArgumentException si el usuario o la alerta no existen, o si
         *                                  la alerta no pertenece al establecimiento
         */
        AlertaDetalleResponse obtenerDetalleAlerta(String emailUsuario, Long establecimientoId, Long alertaId);

        AlertaResponse actualizarEstadoAlerta(String emailUsuario, Long alertaId, EstadoAlerta nuevoEstado,
                        String notas);

        /**
         * Archiva una alerta de celo porque su ventana de inseminación ha vencido.
         */
        AlertaResponse archivarAlertaPorVencimiento(String emailUsuario, Long establecimientoId, Long alertaId);

        /**
         * Retorna el estado actual de la ventana óptima de inseminación para
         * una alerta de celo.
         *
         * <p>
         * La ventana es de 6 a 24 horas desde la detección del celo
         * ({@code ventanaInicioIns} – {@code ventanaFinIns}). Devuelve:
         * <ul>
         * <li>{@code tiempoRestanteSegundos}: segundos restantes (0 si venció)</li>
         * <li>{@code porcentajeTranscurrido}: % de la ventana transcurrida (0–100)</li>
         * <li>{@code proximaAVencer}: {@code true} cuando quedan menos de 2 horas</li>
         * <li>{@code estado}: {@code ACTIVA}, {@code FUTURA} o {@code VENCIDA}</li>
         * </ul>
         *
         * @param emailUsuario      email del usuario autenticado (extraído del JWT)
         * @param establecimientoId ID del establecimiento actualmente seleccionado
         * @param alertaId          ID de la alerta de celo
         * @return DTO con el estado de la ventana
         * @throws AccessDeniedException    si el usuario no pertenece al
         *                                  establecimiento
         * @throws IllegalArgumentException si el usuario o la alerta no existen, o la
         *                                  alerta no pertenece al establecimiento
         *                                  indicado
         */
        VentanaInseminacionResponse obtenerEstadoVentanaInseminacion(String emailUsuario, Long establecimientoId,
                        Long alertaId);

        AlertaResponse intervenirAlerta(String emailUsuario, Long alertaId, IntervencionRequest request);

        /**
         * Devuelve la línea de tiempo unificada de alertas e intervenciones de un animal,
         * ordenada cronológicamente (ASC). Las alertas y las intervenciones (H0021) se
         * mezclan en una sola lista distinguibles por el campo {@code tipoEvento}.
         *
         * <p>Las intervenciones se devuelven siempre que cumplan el rango de fechas;
         * el filtro por {@code tipo} se aplica únicamente a las alertas.
         *
         * @param emailUsuario email del usuario autenticado (extraído del JWT)
         * @param animalId     ID del animal
         * @param tipo         filtro opcional de tipo de alerta:
         *                     {@code "celo"}, {@code "sanitario"},
         *                     {@code "geoespacial"}, {@code "perimetro"} (null = todos)
         * @param desde        inicio del rango de fechas, inclusive (null = sin límite)
         * @param hasta        fin del rango de fechas, inclusive (null = sin límite)
         * @return lista de eventos ordenados por fecha ASC
         * @throws AccessDeniedException    si el usuario no pertenece al establecimiento
         *                                  del animal
         * @throws IllegalArgumentException si el usuario o el animal no existen
         */
        List<LineaTiempoEventoResponse> obtenerLineaTiempoAlertas(
                        String emailUsuario,
                        Long animalId,
                        String tipo,
                        LocalDateTime desde,
                        LocalDateTime hasta);
}
