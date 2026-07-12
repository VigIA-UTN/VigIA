package dev.vigia.backend.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import dev.vigia.backend.dto.RodeoRequest;
import dev.vigia.backend.dto.RodeoResponse;
import dev.vigia.backend.service.RodeoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/rodeos")
@RequiredArgsConstructor
public class RodeoController {

    private final RodeoService rodeoService;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<List<RodeoResponse>> getAllRodeos(Authentication authentication) {
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"))) {
            return ResponseEntity.ok(rodeoService.getAllRodeos());
        } else {
            return ResponseEntity.ok(rodeoService.getRodeosByUsuario(authentication.getName()));
        }

    }

    @PostMapping("/crear")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> generarRodeo(
            @Valid @RequestBody RodeoRequest request,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            RodeoResponse response = rodeoService.generarRodeo(request, emailProductor);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> desactivarRodeo(
            @PathVariable("id") Long rodeoId,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            rodeoService.desactivarRodeo(rodeoId, emailProductor);
            return ResponseEntity.ok().body("Rodeo eliminado exitosamente");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> getRodeoById(
            @PathVariable("id") Long rodeoId,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            RodeoResponse response = rodeoService.getRodeoById(rodeoId, emailProductor);
            return ResponseEntity.ok().body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
