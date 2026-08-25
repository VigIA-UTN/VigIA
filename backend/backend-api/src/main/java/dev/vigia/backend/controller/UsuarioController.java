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

    @GetMapping("/productores")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<UsuarioResponse>> getProductores() {
        return ResponseEntity.ok(usuarioService.getProductores());
    }

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

    @GetMapping("")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<UsuarioResponse>> getAllUsuarios() {
        return ResponseEntity.ok(usuarioService.getAllUsuariosNoAdmins());
    }

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

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> getMiPerfil(Principal principal) {
        return ResponseEntity.ok(usuarioService.getMiPerfil(principal.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<UsuarioResponse> updateMiPerfil(Principal principal,
            @RequestBody UpdatePerfilRequest request) {
        return ResponseEntity.ok(usuarioService.updateMiPerfil(principal.getName(), request));
    }

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
