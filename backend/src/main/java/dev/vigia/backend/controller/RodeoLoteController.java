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

import dev.vigia.backend.dto.RodeoLoteRequest;
import dev.vigia.backend.dto.RodeoLoteResponse;
import dev.vigia.backend.service.RodeoLoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/rodeo-lotes")
@RequiredArgsConstructor
public class RodeoLoteController {

    private final RodeoLoteService rodeoLoteService;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<List<RodeoLoteResponse>> getAllRodeoLotes(Authentication authentication) {
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"))) {
            return ResponseEntity.ok(rodeoLoteService.getAllRodeoLotes());
        } else {
            return ResponseEntity.ok(rodeoLoteService.getRodeoLotesByUsuario(authentication.getName()));
        }

    }

    @PostMapping("/crear")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> generarRodeoLote(
            @Valid @RequestBody RodeoLoteRequest request,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            RodeoLoteResponse response = rodeoLoteService.generarRodeoLote(request, emailProductor);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> desactivarRodeoLote(
            @PathVariable("id") Long rodeoId,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            rodeoLoteService.desactivarRodeoLote(rodeoId, emailProductor);
            return ResponseEntity.ok().body("Rodeo eliminado exitosamente");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> getRodeoLoteById(
            @PathVariable("id") Long rodeoId,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            RodeoLoteResponse response = rodeoLoteService.getRodeoLoteById(rodeoId, emailProductor);
            return ResponseEntity.ok().body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
