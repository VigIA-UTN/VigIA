package dev.vigia.backend.controller;

import dev.vigia.backend.dto.CambiarPasswordRequest;
import dev.vigia.backend.dto.UpdatePerfilRequest;
import dev.vigia.backend.dto.UsuarioResponse;
import dev.vigia.backend.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    /**
     * GET /api/v1/usuarios/productores
     * <p>
     * Lista todos los usuarios con rol PRODUCTOR.
     *
     * @return 200 con la lista de productores
     */
    @GetMapping("/productores")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<UsuarioResponse>> getProductores() {
        return ResponseEntity.ok(usuarioService.getProductores());
    }

    /**
     * DELETE /api/v1/usuarios/productores/{id}
     * <p>
     * Desactiva un usuario con rol PRODUCTOR.
     *
     * @param id ID del usuario a desactivar
     * @return 200 si se desactivó correctamente, 400 si hay error
     */
    @DeleteMapping("/productores/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> desactivarProductor(@PathVariable Long id) {
        try {
            usuarioService.desactivarProductor(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DELETE /api/v1/usuarios/empleados/{establecimientoId}/{id}
     * <p>
     * Remueve a un empleado (VETERINARIO o CAMPERO) de un establecimiento del
     * productor autenticado.
     *
     * @param establecimientoId ID del establecimiento
     * @param id                ID del empleado a remover
     * @param principal         usuario autenticado
     * @return 200 si se removió correctamente, 400 si hay error
     */
    @DeleteMapping("/empleados/{establecimientoId}/{id}")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<Void> desactivarEmpleado(@PathVariable Long establecimientoId, @PathVariable Long id,
            Principal principal) {
        try {
            usuarioService.removerEmpleado(principal.getName(), id, establecimientoId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /api/v1/usuarios
     * <p>
     * Lista todos los usuarios del sistema excepto los administradores.
     *
     * @return 200 con la lista de usuarios
     */
    @GetMapping("")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<UsuarioResponse>> getAllUsuarios() {
        return ResponseEntity.ok(usuarioService.getAllUsuariosNoAdmins());
    }

    /**
     * GET /api/v1/usuarios/empleados?establecimientoId={id}
     * <p>
     * Lista los usuarios activos vinculados a los establecimientos del productor
     * autenticado (productores, incluido él mismo, veterinarios y camperos),
     * opcionalmente filtrados por establecimiento.
     *
     * @param establecimientoId (opcional) ID del establecimiento a filtrar
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de empleados, 403 si acceso denegado, 400 si hay
     *         error
     */
    @GetMapping("/empleados")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> getEmpleados(
            @RequestParam(name = "establecimientoId", required = false) Long establecimientoId,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(usuarioService.getEmpleados(authentication.getName(), establecimientoId));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PATCH /api/v1/usuarios/{id}/estado
     * <p>
     * Alterna el estado activo/inactivo de un usuario.
     *
     * @param id ID del usuario
     * @return 200 si se actualizó correctamente, 400 si hay error
     */
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> toggleUsuarioEstado(@PathVariable Long id) {
        try {
            usuarioService.toggleUsuarioEstado(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /api/v1/usuarios/me
     * <p>
     * Obtiene el perfil del usuario autenticado.
     *
     * @param principal usuario autenticado
     * @return 200 con el perfil del usuario
     */
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> getMiPerfil(Principal principal) {
        return ResponseEntity.ok(usuarioService.getMiPerfil(principal.getName()));
    }

    /**
     * PUT /api/v1/usuarios/me
     * <p>
     * Edita los datos del perfil del usuario autenticado.
     *
     * @param principal usuario autenticado
     * @param request   nuevos datos del perfil
     * @return 200 con el perfil actualizado
     */
    @PutMapping("/me")
    public ResponseEntity<UsuarioResponse> updateMiPerfil(Principal principal,
            @RequestBody UpdatePerfilRequest request) {
        return ResponseEntity.ok(usuarioService.updateMiPerfil(principal.getName(), request));
    }

    /**
     * PUT /api/v1/usuarios/me/password
     * <p>
     * Cambia la contraseña del usuario autenticado.
     *
     * @param principal usuario autenticado
     * @param request   contraseña actual y nueva contraseña
     * @return 200 si se cambió correctamente, 400 si hay error (por ejemplo,
     *         contraseña actual incorrecta)
     */
    @PutMapping("/me/password")
    public ResponseEntity<Void> cambiarPassword(Principal principal, @RequestBody CambiarPasswordRequest request) {
        try {
            usuarioService.cambiarPassword(principal.getName(), request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
