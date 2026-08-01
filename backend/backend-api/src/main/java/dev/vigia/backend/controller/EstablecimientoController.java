package dev.vigia.backend.controller;

import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.dto.EstablecimientoResponse;
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

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstablecimientoResponse> createEstablecimiento(@RequestBody EstablecimientoRequest request) {
        if (request.getNombre() == null || request.getLatitud() == null || request.getLongitud() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(establecimientoService.createEstablecimiento(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<List<EstablecimientoResponse>> getAllEstablecimientos(Authentication authentication) {
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"))) {
            return ResponseEntity.ok(establecimientoService.getAllEstablecimientos());
        } else {
            return ResponseEntity.ok(establecimientoService.getEstablecimientosByUsuario(authentication.getName()));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstablecimientoResponse> getEstablecimientoById(@PathVariable Long id) {
        return ResponseEntity.ok(establecimientoService.getEstablecimientoById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstablecimientoResponse> updateEstablecimiento(@PathVariable Long id, @RequestBody EstablecimientoRequest request) {
        return ResponseEntity.ok(establecimientoService.updateEstablecimiento(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstablecimientoResponse> desactivarEstablecimiento(@PathVariable Long id) {
        return ResponseEntity.ok(establecimientoService.desactivarEstablecimiento(id));
    }

    @PutMapping("/activar/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstablecimientoResponse> activarEstablecimiento(@PathVariable Long id) {
        return ResponseEntity.ok(establecimientoService.activarEstablecimiento(id));
    }
}
