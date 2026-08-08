package dev.vigia.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;

import dev.vigia.backend.dto.AlertaDetalleResponse;
import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.AlertaResumenResponse;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.service.AlertaService;
import dev.vigia.backend.service.SseAlertaPublisher;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/alertas")
@RequiredArgsConstructor
public class AlertaController {

    private final AlertaService alertaService;
    private final SseAlertaPublisher sseAlertaPublisher;

    /**
     * GET /api/v1/alertas?establecimientoId={id}&tipo={tipo}&severidad={severidad}&estado={estado}
     * <p>
     * Obtiene las alertas de un establecimiento. Permite filtrar opcionalmente
     * por tipo (reproductivo, sanitario, geoespacial), severidad (alta, media, baja) y estado.
     * Si no se envían filtros, devuelve todas las alertas.
     * Ordena por defecto por severidad descendente.
     *
     * @param establecimientoId ID del establecimiento
     * @param tipo              (opcional) reproductivo, sanitario, geoespacial
     * @param severidad         (opcional) ALTA, MEDIA, BAJA
     * @param estado            (opcional) PENDIENTE, CONFIRMADA, DESCARTADA
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de alertas filtradas, 400 si el usuario no existe, 403 si no
     *         pertenece
     */
    @GetMapping
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> listarTodasAlertas(
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            @RequestParam(name = "tipo", required = false) String tipo,
            @RequestParam(name = "severidad", required = false) String severidad,
            @RequestParam(name = "estado", required = false) EstadoAlerta estado,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            List<AlertaResponse> response = alertaService.listarAlertas(email, establecimientoId, tipo, severidad, estado);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/alertas/resumen?establecimientoId={id}
     * <p>
     * Devuelve el conteo de alertas activas agrupadas por tipo.
     */
    @GetMapping("/resumen")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> obtenerResumenAlertas(
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            AlertaResumenResponse response = alertaService.obtenerResumenAlertas(email, establecimientoId);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/alertas/celo?establecimientoId={id}&estado={estado}
     * <p>
     * Lista las alertas de celo del establecimiento seleccionado por el usuario
     * en el toggle de la página principal (mapa). El frontend envía el ID del
     * establecimiento activo; el backend valida que el usuario pertenezca a él.
     *
     * @param establecimientoId ID del establecimiento actualmente seleccionado
     *                          (obligatorio)
     * @param estado            (opcional) filtro por estado: PENDIENTE, CONFIRMADA
     *                          o DESCARTADA
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de alertas, 400 si el usuario no existe, 403 si no
     *         pertenece
     */
    @GetMapping("/celo")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> listarAlertasCelo(
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            @RequestParam(name = "estado", required = false) EstadoAlerta estado,
            Authentication authentication) {

        try {
            String email = authentication.getName();
            List<AlertaResponse> response = alertaService.listarAlertasCelo(email, establecimientoId, estado);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/alertas/{id}?establecimientoId={id}
     * <p>
     * Obtiene el detalle completo de una alerta.
     *
     * @param alertaId          ID de la alerta
     * @param establecimientoId ID del establecimiento actualmente seleccionado
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con el detalle de la alerta, 400 si hay error, 403 si acceso
     *         denegado
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> obtenerDetalleAlerta(
            @PathVariable("id") Long alertaId,
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            AlertaDetalleResponse response = alertaService.obtenerDetalleAlerta(email, establecimientoId, alertaId);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Alerta no encontrada.".equals(e.getMessage())) {
                return ResponseEntity.status(404).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/estado/{id}")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO')")
    public ResponseEntity<?> actualizarEstadoAlerta(
            @PathVariable("id") Long alertaId,
            @RequestParam(name = "estado") EstadoAlerta nuevoEstado,
            @RequestParam(name = "notas", required = false) String notas,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            AlertaResponse response = alertaService.actualizarEstadoAlerta(email, alertaId, nuevoEstado, notas);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlertas(
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            @RequestParam(name = "lastEventId", required = false) Long lastEventId,
            Authentication authentication) {

        return sseAlertaPublisher.registrarClienteSse(establecimientoId, lastEventId);
    }
}
