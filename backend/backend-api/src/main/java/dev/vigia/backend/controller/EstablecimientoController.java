package dev.vigia.backend.controller;

import dev.vigia.backend.dto.ConfiguracionAnestroRequest;
import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.dto.EstablecimientoResponse;
import dev.vigia.backend.service.EstablecimientoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/establecimientos")
@RequiredArgsConstructor
public class EstablecimientoController {

    private final EstablecimientoService establecimientoService;

    /**
     * POST /api/v1/establecimientos
     * <p>
     * Crea un nuevo establecimiento.
     *
     * @param request datos del establecimiento (nombre, latitud y longitud
     *                obligatorios)
     * @return 201 con el establecimiento creado, 400 si faltan datos
     *         obligatorios
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstablecimientoResponse> createEstablecimiento(@RequestBody EstablecimientoRequest request) {
        if (request.getNombre() == null || request.getLatitud() == null || request.getLongitud() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(establecimientoService.createEstablecimiento(request));
    }

    /**
     * GET /api/v1/establecimientos
     * <p>
     * Lista establecimientos. Un ADMINISTRADOR ve todos los establecimientos del
     * sistema; el resto de roles ve únicamente los suyos.
     *
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de establecimientos
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<List<EstablecimientoResponse>> getAllEstablecimientos(Authentication authentication) {
        if (esAdmin(authentication)) {
            return ResponseEntity.ok(establecimientoService.getAllEstablecimientos());
        } else {
            return ResponseEntity.ok(establecimientoService.getEstablecimientosByUsuario(authentication.getName()));
        }
    }

    /**
     * GET /api/v1/establecimientos/{id}
     * <p>
     * Obtiene el detalle de un establecimiento por su ID.
     *
     * @param id ID del establecimiento
     * @return 200 con el establecimiento
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstablecimientoResponse> getEstablecimientoById(@PathVariable Long id) {
        return ResponseEntity.ok(establecimientoService.getEstablecimientoById(id));
    }

    /**
     * PUT /api/v1/establecimientos/{id}
     * <p>
     * Edita los datos de un establecimiento existente. Un ADMINISTRADOR puede
     * editar cualquiera; un PRODUCTOR solo los establecimientos a los que pertenece.
     *
     * @param id             ID del establecimiento a editar
     * @param request        nuevos datos del establecimiento
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con el establecimiento actualizado, 403 si el usuario no
     *         pertenece al establecimiento
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR')")
    public ResponseEntity<?> updateEstablecimiento(@PathVariable Long id,
            @RequestBody EstablecimientoRequest request, Authentication authentication) {
        try {
            return ResponseEntity.ok(establecimientoService.updateEstablecimiento(
                    id, request, authentication.getName(), esAdmin(authentication)));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    /**
     * PATCH /api/v1/establecimientos/{id}/configuracion-anestro
     * <p>
     * Actualiza solo los parámetros de detección de anestro de un establecimiento
     * al que el usuario pertenece.
     *
     * @param id             ID del establecimiento
     * @param request        días sin ciclo (1–365) y severidad de la alerta
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con el establecimiento actualizado, 400 si los datos son
     *         inválidos, 403 si el usuario no pertenece al establecimiento
     */
    @PatchMapping("/{id}/configuracion-anestro")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO')")
    public ResponseEntity<?> actualizarConfiguracionAnestro(@PathVariable Long id,
            @Valid @RequestBody ConfiguracionAnestroRequest request, Authentication authentication) {
        try {
            return ResponseEntity.ok(establecimientoService.actualizarConfiguracionAnestro(
                    id, request, authentication.getName()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    /**
     * DELETE /api/v1/establecimientos/{id}
     * <p>
     * Desactiva (baja lógica) un establecimiento.
     *
     * @param id ID del establecimiento a desactivar
     * @return 200 con el establecimiento desactivado
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstablecimientoResponse> desactivarEstablecimiento(@PathVariable Long id) {
        return ResponseEntity.ok(establecimientoService.desactivarEstablecimiento(id));
    }

    /**
     * PUT /api/v1/establecimientos/activar/{id}
     * <p>
     * Reactiva un establecimiento previamente desactivado.
     *
     * @param id ID del establecimiento a activar
     * @return 200 con el establecimiento activado
     */
    @PutMapping("/activar/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstablecimientoResponse> activarEstablecimiento(@PathVariable Long id) {
        return ResponseEntity.ok(establecimientoService.activarEstablecimiento(id));
    }

    private boolean esAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
    }
}
