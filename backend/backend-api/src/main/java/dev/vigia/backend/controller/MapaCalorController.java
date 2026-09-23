package dev.vigia.backend.controller;

import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.exception.MlGeoDegradadoException;
import dev.vigia.backend.service.MapaCalorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/mapa-calor")
@RequiredArgsConstructor
public class MapaCalorController {

    private final MapaCalorService mapaCalorService;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> getMapaCalor(
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            @RequestParam(name = "animalId", required = false) Long animalId,
            @RequestParam(name = "rodeoId", required = false) Long rodeoId,
            @RequestParam(name = "desde", required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(name = "hasta", required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            Authentication authentication) {
        
        try {
            if (desde == null) {
                desde = LocalDateTime.now().minusDays(7);
            }
            if (hasta == null) {
                hasta = LocalDateTime.now();
            }

            String email = authentication.getName();
            GeoAnalyzeResponseDTO response = mapaCalorService.obtenerMapaCalor(email, establecimientoId, desde, hasta, animalId, rodeoId);
            return ResponseEntity.ok(response);
            
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (MlGeoDegradadoException e) {
            GeoAnalyzeResponseDTO emptyResponse = GeoAnalyzeResponseDTO.builder()
                    .mapaCalor(new ArrayList<>())
                    .alertas(new ArrayList<>())
                    .build();
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(emptyResponse);
        }
    }
}
