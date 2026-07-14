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

import dev.vigia.backend.dto.AnimalRequest;
import dev.vigia.backend.dto.AnimalResponse;
import dev.vigia.backend.service.AnimalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/animales")
@RequiredArgsConstructor
public class AnimalController {

    private final AnimalService animalService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','PRODUCTOR','VETERINARIO','CAMPERO')")
    public ResponseEntity<?> listarAnimales(Authentication authentication) {
        try {
            String email = authentication.getName();
            List<AnimalResponse> response = animalService.listarAnimales(email);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<AnimalResponse> getAnimalById(
            @PathVariable("id") Long animalId,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            AnimalResponse response = animalService.getAnimalById(animalId, emailProductor);
            return ResponseEntity.ok().body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> cargarAnimal(
            @Valid @RequestBody AnimalRequest request,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            AnimalResponse response = animalService.cargarAnimal(emailProductor, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> desactivarAnimal(
            @PathVariable("id") Long animalId,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            animalService.desactivarAnimal(animalId, emailProductor);
            return ResponseEntity.ok().body("Animal desactivado exitosamente");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
