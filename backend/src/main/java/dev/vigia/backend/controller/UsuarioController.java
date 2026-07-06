package dev.vigia.backend.controller;

import dev.vigia.backend.dto.UsuarioResponse;
import dev.vigia.backend.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/productores")
    public ResponseEntity<List<UsuarioResponse>> getProductores() {
        return ResponseEntity.ok(usuarioService.getProductores());
    }

    @DeleteMapping("/productores/{id}")
    public ResponseEntity<Void> desactivarProductor(@PathVariable Long id) {
        try {
            usuarioService.desactivarProductor(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
