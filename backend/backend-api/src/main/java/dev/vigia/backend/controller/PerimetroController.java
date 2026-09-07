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

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> eliminarPerimetro(
            @PathVariable("id") Long id,
            Authentication authentication) {
        return desactivarPerimetro(id, authentication);
    }
}
