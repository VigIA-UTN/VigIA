package dev.vigia.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;

import dev.vigia.backend.dto.AlertaDetalleResponse;
import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.AlertaResumenResponse;
import dev.vigia.backend.dto.DiagnosticoRequest;
import dev.vigia.backend.dto.DiagnosticoResponse;
import dev.vigia.backend.dto.IntervencionRequest;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.exception.AlertaYaProcesadaException;
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
     * GET
     * /api/v1/alertas?establecimientoId={id}&tipo={tipo}&severidad={severidad}&estado={estado}
     * <p>
     * Obtiene las alertas de un establecimiento. Permite filtrar opcionalmente
     * por tipo (reproductivo, sanitario, geoespacial), severidad (alta, media,
     * baja) y estado.
     * Si no se envían filtros, devuelve todas las alertas.
     * Ordena por defecto por severidad descendente.
     *
     * @param establecimientoId ID del establecimiento
     * @param tipo              (opcional) reproductivo, sanitario, geoespacial
     * @param severidad         (opcional) ALTA, MEDIA, BAJA
     * @param estado            (opcional) PENDIENTE, CONFIRMADA, DESCARTADA
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de alertas filtradas, 400 si el usuario no existe,
     *         403 si no
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
            List<AlertaResponse> response = alertaService.listarAlertas(email, establecimientoId, tipo, severidad,
                    estado);
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
     * GET /api/v1/alertas/salud?establecimientoId={id}&estado={estado}
     * <p>
     * Lista las alertas sanitarias (disparador SALUD) del establecimiento
     * seleccionado por el usuario, opcionalmente filtradas por estado.
     *
     * @param establecimientoId ID del establecimiento actualmente seleccionado
     *                          (obligatorio)
     * @param estado            (opcional) filtro por estado: PENDIENTE, CONFIRMADA
     *                          o DESCARTADA
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de alertas, 400 si el usuario no existe, 403 si no
     *         pertenece
     */
    @GetMapping("/salud")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> listarAlertasSalud(
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            @RequestParam(name = "estado", required = false) EstadoAlerta estado,
            Authentication authentication) {

        try {
            String email = authentication.getName();
            List<AlertaResponse> response = alertaService.listarAlertasSalud(email, establecimientoId, estado);
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

    /**
     * GET /api/v1/alertas/{id}/ventana-inseminacion?establecimientoId={id}
     * <p>
     * Alias histórico de {@code GET /api/v1/alertas/{id}/ventana}: devuelve el
     * estado actual de la ventana óptima de inseminación de una alerta de celo.
     *
     * @param alertaId          ID de la alerta
     * @param establecimientoId ID del establecimiento actualmente seleccionado
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con el estado de la ventana, 403 si acceso denegado,
     *         404 si la alerta no existe, 400 si la alerta no pertenece al
     *         establecimiento
     */
    @GetMapping("/{id}/ventana-inseminacion")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> obtenerEstadoVentanaInseminacion(
            @PathVariable("id") Long alertaId,
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            VentanaInseminacionResponse response = alertaService.obtenerEstadoVentanaInseminacion(email,
                    establecimientoId, alertaId);
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

    /**
     * PUT /api/v1/alertas/{id}/archivar?establecimientoId={id}
     * <p>
     * Archiva una alerta de celo cuya ventana de inseminación venció, quedando
     * fuera del listado de alertas activas.
     *
     * @param alertaId          ID de la alerta a archivar
     * @param establecimientoId ID del establecimiento actualmente seleccionado
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la alerta archivada, 400 si hay error, 403 si acceso
     *         denegado
     */
    @PutMapping("/{id}/archivar")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> archivarAlertaPorVencimiento(
            @PathVariable("id") Long alertaId,
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            AlertaResponse response = alertaService.archivarAlertaPorVencimiento(email, establecimientoId, alertaId);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PUT /api/v1/alertas/estado/{id}?estado={estado}&notas={notas}
     * <p>
     * Cambia el estado de una alerta (por ejemplo de PENDIENTE a CONFIRMADA o
     * DESCARTADA), sin importar su tipo (celo, salud, etc.).
     *
     * @param alertaId       ID de la alerta
     * @param nuevoEstado    nuevo estado: PENDIENTE, CONFIRMADA o DESCARTADA
     * @param notas          (opcional) notas asociadas al cambio de estado
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con la alerta actualizada, 400 si hay error, 403 si acceso
     *         denegado, 409 si otro usuario ya la procesó
     */
    @PutMapping("/estado/{id}")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
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
        } catch (AlertaYaProcesadaException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/alertas/intervenir/{id}
     * <p>
     * Registra una intervención (tratamiento, vacunación, revisión veterinaria,
     * inseminación u otro) asociada a una alerta.
     *
     * @param request        datos de la intervención (tipo, fecha/hora, descripción
     *                       y, para REVISION_VETERINARIA, el estado de salud
     *                       resultante)
     * @param alertaId       ID de la alerta sobre la que se interviene
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con la alerta actualizada, 400 si hay error, 403 si acceso
     *         denegado, 404 si la alerta no existe
     */
    @PostMapping("/intervenir/{id}")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> intervenirAlerta(
            @RequestBody IntervencionRequest request,
            @PathVariable("id") Long alertaId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            AlertaResponse response = alertaService.intervenirAlerta(email, alertaId, request);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Alerta no encontrada".equals(e.getMessage())) {
                return ResponseEntity.status(404).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/alertas/{id}/diagnosticos
     * <p>
     * Registra un diagnóstico veterinario sobre una alerta sanitaria confirmada.
     * No modifica el estado de la alerta.
     *
     * @param alertaId       ID de la alerta diagnosticada
     * @param request        diagnóstico e indicación o tratamiento
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con el diagnóstico registrado, 400 si hay error, 403 si acceso
     *         denegado, 404 si la alerta no existe
     */
    @PostMapping("/{id}/diagnosticos")
    @PreAuthorize("hasRole('VETERINARIO')")
    public ResponseEntity<?> registrarDiagnostico(
            @PathVariable("id") Long alertaId,
            @RequestBody DiagnosticoRequest request,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            DiagnosticoResponse response = alertaService.registrarDiagnostico(email, alertaId, request);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Alerta no encontrada".equals(e.getMessage())) {
                return ResponseEntity.status(404).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/alertas/diagnosticos?animalId={id}&establecimientoId={id}
     * <p>
     * Lista los diagnósticos de los establecimientos a los que pertenece el
     * usuario, del más reciente al más antiguo. Ambos filtros son opcionales.
     *
     * @param animalId          (opcional) solo los diagnósticos de este animal
     * @param establecimientoId (opcional) solo los de este establecimiento
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista, 403 si el usuario no pertenece al establecimiento
     *         indicado
     */
    @GetMapping("/diagnosticos")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> listarDiagnosticos(
            @RequestParam(name = "animalId", required = false) Long animalId,
            @RequestParam(name = "establecimientoId", required = false) Long establecimientoId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            List<DiagnosticoResponse> response = alertaService.listarDiagnosticos(email, animalId,
                    establecimientoId);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/alertas/stream?establecimientoId={id}&lastEventId={id}
     * <p>
     * Abre un stream Server-Sent Events (SSE) que publica en tiempo real las
     * nuevas alertas generadas para el establecimiento indicado.
     *
     * @param establecimientoId ID del establecimiento a suscribir
     * @param lastEventId       (opcional) ID del último evento recibido por el
     *                          cliente, usado para reanudar el stream tras una
     *                          reconexión sin perder eventos
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return emisor SSE registrado para el establecimiento indicado
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlertas(
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            @RequestParam(name = "lastEventId", required = false) Long lastEventId,
            Authentication authentication) {

        return sseAlertaPublisher.registrarClienteSse(establecimientoId, lastEventId);
    }

    /**
     * GET /api/v1/alertas/{id}/ventana
     * <p>
     * Devuelve el estado actual de la ventana óptima de inseminación para una
     * alerta de celo. La ventana va de 6 a 24 horas desde la detección del celo.
     *
     * <p>
     * Respuesta:
     * <ul>
     * <li>{@code tiempo_restante_segundos}: segundos hasta el fin (0 si
     * venció)</li>
     * <li>{@code porcentaje_transcurrido}: % transcurrido (0–100)</li>
     * <li>{@code proxima_a_vencer}: {@code true} cuando quedan &lt; 2 horas</li>
     * <li>{@code estado}: {@code ACTIVA}, {@code FUTURA} o {@code VENCIDA}</li>
     * </ul>
     *
     * @param establecimientoId ID del establecimiento actualmente seleccionado
     * @param alertaId          ID de la alerta
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con el estado de la ventana, 403 si acceso denegado,
     *         404 si la alerta no existe, 400 si la alerta no pertenece al
     *         establecimiento
     */
    @GetMapping("/{id}/ventana")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> obtenerVentanaInseminacion(
            @PathVariable("id") Long alertaId,
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            VentanaInseminacionResponse response = alertaService.obtenerEstadoVentanaInseminacion(email,
                    establecimientoId, alertaId);
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
}
