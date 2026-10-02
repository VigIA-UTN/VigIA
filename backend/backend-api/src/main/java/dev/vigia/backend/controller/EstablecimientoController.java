package dev.vigia.backend.controller;

import dev.vigia.backend.dto.ConfigAnestroRequest;
import dev.vigia.backend.dto.ConfigAnestroResponse;
import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.dto.EstablecimientoResponse;
import dev.vigia.backend.dto.IntervencionResponse;
import dev.vigia.backend.service.EstablecimientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"))) {
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
     * Edita los datos de un establecimiento existente.
     *
     * @param id      ID del establecimiento a editar
     * @param request nuevos datos del establecimiento
     * @return 200 con el establecimiento actualizado
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR')")
    public ResponseEntity<EstablecimientoResponse> updateEstablecimiento(@PathVariable Long id,
            @RequestBody EstablecimientoRequest request) {
        return ResponseEntity.ok(establecimientoService.updateEstablecimiento(id, request));
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

    @GetMapping("{id}/intervenciones")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> obtenerIntervenciones(@PathVariable Long id, Authentication authentication) {
        try {
            String email = authentication.getName();
            List<IntervencionResponse> response = establecimientoService.obtenerIntervenciones(email, id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al obtener intervenciones: " + e.getMessage());
        }
    }

    // ── Configuración de anestro ──────────────────────────────────────────────

    /**
     * GET /api/v1/establecimientos/{id}/anestro
     * <p>
     * Consulta los parámetros de detección de anestro de un establecimiento.
     * Solo el PRODUCTOR o VETERINARIO activo del establecimiento puede consultarlos.
     *
     * @param id             ID del establecimiento
     * @param authentication contexto de seguridad
     * @return 200 con la configuración de anestro, 403 si no pertenece al establecimiento
     */
    @GetMapping("/{id}/anestro")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO')")
    public ResponseEntity<ConfigAnestroResponse> getConfigAnestro(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(
                establecimientoService.getConfigAnestro(id, authentication.getName()));
    }

    /**
     * PUT /api/v1/establecimientos/{id}/anestro
     * <p>
     * Actualiza los parámetros de detección de anestro de un establecimiento.
     * Solo el PRODUCTOR o VETERINARIO activo del establecimiento puede editarlos.
     * Valida rangos: días 1–365 y severidad dentro de {BAJA, MEDIA, ALTA}.
     *
     * @param id             ID del establecimiento
     * @param request        nuevos parámetros (diasDeteccionAnestro, severidadAnestro)
     * @param authentication contexto de seguridad
     * @return 200 con la configuración actualizada, 400 si los valores son inválidos,
     *         403 si no pertenece al establecimiento
     */
    @PutMapping("/{id}/anestro")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO')")
    public ResponseEntity<ConfigAnestroResponse> updateConfigAnestro(
            @PathVariable Long id,
            @RequestBody ConfigAnestroRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                establecimientoService.updateConfigAnestro(id, request, authentication.getName()));
    }
}
