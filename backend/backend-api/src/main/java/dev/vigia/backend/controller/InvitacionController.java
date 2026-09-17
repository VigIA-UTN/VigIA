package dev.vigia.backend.controller;

import dev.vigia.backend.dto.InvitacionEmpleadoRequest;
import dev.vigia.backend.dto.InvitacionProductorRequest;
import dev.vigia.backend.dto.InvitacionResponse;
import dev.vigia.backend.service.InvitacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/invitaciones")
@RequiredArgsConstructor
public class InvitacionController {

    private final InvitacionService invitacionService;

    /**
     * POST /api/v1/invitaciones/productor
     * <p>
     * Genera una invitación para que una persona se registre como PRODUCTOR.
     *
     * @param request        datos de la invitación
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 201 con la invitación generada, 400 si hay error de validación
     */
    @PostMapping("/productor")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> generarInvitacionProductor(
            @Valid @RequestBody InvitacionProductorRequest request,
            Authentication authentication) {

        try {
            String emailAdmin = authentication.getName();
            InvitacionResponse response = invitacionService.generarInvitacionProductor(request, emailAdmin);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/invitaciones/empleado
     * <p>
     * Genera una invitación para que una persona se una como empleado
     * (VETERINARIO o CAMPERO) a un establecimiento del productor autenticado.
     *
     * @param request        datos de la invitación
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 201 con la invitación generada, 400 si hay error de validación
     */
    @PostMapping("/empleado")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> generarInvitacionEmpleado(
            @Valid @RequestBody InvitacionEmpleadoRequest request,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            InvitacionResponse response = invitacionService.generarInvitacionEmpleado(request, emailProductor);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }
    /**
     * GET /api/v1/invitaciones/productor
     * <p>
     * Lista todas las invitaciones a PRODUCTOR generadas en el sistema.
     *
     * @return 200 con la lista de invitaciones
     */
    @GetMapping("/productor")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<InvitacionResponse>> listarInvitacionesProductor() {
        return ResponseEntity.ok(invitacionService.listarInvitacionesProductor());
    }

    /**
     * GET /api/v1/invitaciones/empleado?establecimientoId={id}
     * <p>
     * Lista las invitaciones a empleados generadas por el productor autenticado,
     * opcionalmente filtradas por establecimiento.
     *
     * @param establecimientoId (opcional) ID del establecimiento a filtrar
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de invitaciones
     */
    @GetMapping("/empleado")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<List<InvitacionResponse>> listarInvitacionesEmpleado(
            @RequestParam(name = "establecimientoId", required = false) Long establecimientoId,
            Authentication authentication) {
        return ResponseEntity.ok(invitacionService.listarInvitacionesEmpleado(authentication.getName(), establecimientoId));
    }

    /**
     * GET /api/v1/invitaciones/mis-invitaciones
     * <p>
     * Lista las invitaciones pendientes recibidas por el usuario autenticado.
     *
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de invitaciones
     */
    @GetMapping("/mis-invitaciones")
    public ResponseEntity<List<InvitacionResponse>> listarMisInvitaciones(Authentication authentication) {
        return ResponseEntity.ok(invitacionService.listarMisInvitaciones(authentication.getName()));
    }

    /**
     * POST /api/v1/invitaciones/{id}/aceptar
     * <p>
     * Acepta una invitación pendiente del usuario autenticado.
     *
     * @param id             ID de la invitación
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 si se aceptó correctamente
     */
    @PostMapping("/{id}/aceptar")
    public ResponseEntity<Void> aceptarInvitacion(@PathVariable Long id, Authentication authentication) {
        invitacionService.aceptarInvitacion(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/v1/invitaciones/token/{token}/aceptar
     * <p>
     * Acepta una invitación identificándola por su token (usado en el link
     * enviado por email), en vez de por ID.
     *
     * @param token          token de la invitación
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 si se aceptó correctamente
     */
    @PostMapping("/token/{token}/aceptar")
    public ResponseEntity<Void> aceptarInvitacionPorToken(@PathVariable String token, Authentication authentication) {
        invitacionService.aceptarInvitacionPorToken(token, authentication.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/v1/invitaciones/{id}/rechazar
     * <p>
     * Rechaza una invitación pendiente del usuario autenticado.
     *
     * @param id             ID de la invitación
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 si se rechazó correctamente
     */
    @PostMapping("/{id}/rechazar")
    public ResponseEntity<Void> rechazarInvitacion(@PathVariable Long id, Authentication authentication) {
        invitacionService.rechazarInvitacion(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /api/v1/invitaciones/{id}
     * <p>
     * Cancela una invitación generada por el usuario autenticado antes de que
     * sea aceptada o rechazada.
     *
     * @param id             ID de la invitación a cancelar
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 204 si se canceló correctamente
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'PRODUCTOR')")
    public ResponseEntity<Void> cancelarInvitacion(@PathVariable Long id, Authentication authentication) {
        invitacionService.cancelarInvitacion(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
