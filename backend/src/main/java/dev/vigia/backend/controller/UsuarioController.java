package dev.vigia.backend.controller;

import dev.vigia.backend.dto.UsuarioResponse;
import dev.vigia.backend.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/empleados")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<List<UsuarioResponse>> getEmpleados(Principal principal) {
        return ResponseEntity.ok(usuarioService.getEmpleados(principal.getName()));
    }

    @DeleteMapping("/empleados/{id}")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<Void> desactivarEmpleado(@PathVariable Long id, Principal principal) {
        try {
            usuarioService.desactivarEmpleado(principal.getName(), id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/empleados/{id}/estado")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<Void> toggleEmpleadoEstado(@PathVariable Long id, Principal principal) {
        try {
            usuarioService.toggleEstadoEmpleado(principal.getName(),id);
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
}
