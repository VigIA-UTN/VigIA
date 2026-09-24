package dev.vigia.backend.controller;

import dev.vigia.backend.dto.PerimetroRequest;
import dev.vigia.backend.dto.PerimetroResponse;
import dev.vigia.backend.service.PerimetroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/perimetros")
@RequiredArgsConstructor
public class PerimetroController {

    private final PerimetroService perimetroService;

    /**
     * POST /api/v1/perimetros
     * <p>
     * Crea un nuevo perímetro geográfico para un establecimiento.
     * <p>
     * Acepta opcionalmente {@code distanciaToleranciaMetros} (>= 0, default 0) y
     * {@code zonaEstricta} (default false). Con zona estricta la tolerancia se
     * ignora y cualquier punto fuera del polígono dispara alerta.
     *
     * @param request        datos del perímetro a crear
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 201 con el perímetro creado, 403 si acceso denegado, 400 si hay
     *         error de validación
     */
    @PostMapping
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> crearPerimetro(
            @Valid @RequestBody PerimetroRequest request,
            Authentication authentication) {
        try {
            PerimetroResponse response = perimetroService.crearPerimetro(request, authentication.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PUT /api/v1/perimetros/{id}
     * <p>
     * Edita un perímetro existente.
     * <p>
     * Si {@code distanciaToleranciaMetros} o {@code zonaEstricta} no se envían,
     * se conservan sus valores actuales.
     *
     * @param id             ID del perímetro a editar
     * @param request        nuevos datos del perímetro
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con el perímetro actualizado, 403 si acceso denegado, 400 si
     *         hay error de validación
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> actualizarPerimetro(
            @PathVariable("id") Long id,
            @Valid @RequestBody PerimetroRequest request,
            Authentication authentication) {
        try {
            PerimetroResponse response = perimetroService.actualizarPerimetro(id, request, authentication.getName());
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/perimetros/{id}
     * <p>
     * Obtiene el detalle de un perímetro por su ID.
     *
     * @param id             ID del perímetro
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con el perímetro, 403 si acceso denegado, 400 si hay error
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> getPerimetroById(
            @PathVariable("id") Long id,
            Authentication authentication) {
        try {
            PerimetroResponse response = perimetroService.getPerimetroById(id, authentication.getName());
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/perimetros?establecimientoId={id}
     * <p>
     * Lista los perímetros de un establecimiento.
     *
     * @param establecimientoId ID del establecimiento
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de perímetros, 403 si acceso denegado, 400 si hay
     *         error
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> getPerimetrosByEstablecimiento(
            @RequestParam("establecimientoId") Long establecimientoId,
            Authentication authentication) {
        try {
            List<PerimetroResponse> perimetros = perimetroService.getPerimetrosByEstablecimiento(
                    establecimientoId, authentication.getName());
            return ResponseEntity.ok(perimetros);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PATCH /api/v1/perimetros/{id}/activar
     * <p>
     * Activa un perímetro previamente desactivado.
     *
     * @param id             ID del perímetro a activar
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 si se activó correctamente, 403 si acceso denegado, 400 si hay
     *         error
     */
    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> activarPerimetro(
            @PathVariable("id") Long id,
            Authentication authentication) {
        try {
            perimetroService.activarPerimetro(id, authentication.getName());
            return ResponseEntity.ok("Perímetro activado exitosamente");
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PATCH /api/v1/perimetros/{id}/desactivar
     * <p>
     * Desactiva un perímetro.
     *
     * @param id             ID del perímetro a desactivar
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 si se desactivó correctamente, 403 si acceso denegado, 400 si
     *         hay error
     */
    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> desactivarPerimetro(
            @PathVariable("id") Long id,
            Authentication authentication) {
        try {
            perimetroService.desactivarPerimetro(id, authentication.getName());
            return ResponseEntity.ok("Perímetro desactivado exitosamente");
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * DELETE /api/v1/perimetros/{id}
     * <p>
     * Alias de {@code PATCH /{id}/desactivar}: elimina (desactiva) un perímetro.
     *
     * @param id             ID del perímetro a eliminar
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 si se eliminó correctamente, 403 si acceso denegado, 400 si hay
     *         error
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> eliminarPerimetro(
            @PathVariable("id") Long id,
            Authentication authentication) {
        return desactivarPerimetro(id, authentication);
    }
}
