package dev.vigia.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.vigia.backend.dto.RodeoLoteRequest;
import dev.vigia.backend.dto.RodeoLoteResponse;
import dev.vigia.backend.service.RodeoLoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/rodeo-lotes")
@RequiredArgsConstructor
public class RodeoLoteController {

    private final RodeoLoteService rodeoLoteService;

    /**
     * GET /api/v1/rodeo-lotes?establecimientoId={id}
     * <p>
     * Lista rodeos/lotes. Un ADMINISTRADOR ve todos los rodeos del sistema; el
     * resto de roles ve los suyos, opcionalmente filtrados por establecimiento.
     *
     * @param establecimientoId (opcional) ID del establecimiento a filtrar
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de rodeos/lotes, 403 si acceso denegado, 400 si
     *         hay error
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> getAllRodeoLotes(
            @RequestParam(name = "establecimientoId", required = false) Long establecimientoId,
            Authentication authentication) {
        try {
            if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"))) {
                return ResponseEntity.ok(rodeoLoteService.getAllRodeoLotes());
            } else {
                return ResponseEntity
                        .ok(rodeoLoteService.getRodeoLotesByUsuario(authentication.getName(), establecimientoId));
            }
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/rodeo-lotes
     * <p>
     * Crea un nuevo rodeo/lote para el productor autenticado.
     *
     * @param request        datos del rodeo/lote a crear
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 201 con el rodeo/lote creado, 400 si hay error de validación
     */
    @PostMapping
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> generarRodeoLote(
            @Valid @RequestBody RodeoLoteRequest request,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            RodeoLoteResponse response = rodeoLoteService.generarRodeoLote(request, emailProductor);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * DELETE /api/v1/rodeo-lotes/{id}
     * <p>
     * Desactiva (baja lógica) un rodeo/lote del productor autenticado.
     *
     * @param rodeoId        ID del rodeo/lote a desactivar
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 si se desactivó correctamente, 400 si hay error
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> desactivarRodeoLote(
            @PathVariable("id") Long rodeoId,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            rodeoLoteService.desactivarRodeoLote(rodeoId, emailProductor);
            return ResponseEntity.ok().body("Rodeo eliminado exitosamente");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/rodeo-lotes/{id}
     * <p>
     * Obtiene el detalle de un rodeo/lote del productor autenticado.
     *
     * @param rodeoId        ID del rodeo/lote
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con el rodeo/lote, 400 si hay error
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> getRodeoLoteById(
            @PathVariable("id") Long rodeoId,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            RodeoLoteResponse response = rodeoLoteService.getRodeoLoteById(rodeoId, emailProductor);
            return ResponseEntity.ok().body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
