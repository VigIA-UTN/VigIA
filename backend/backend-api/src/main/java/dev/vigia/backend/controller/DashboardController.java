package dev.vigia.backend.controller;

import dev.vigia.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Métricas del Dashboard ya calculadas, una pestaña por endpoint. */
@RestController
@RequestMapping("/api/v1/establecimientos/{id}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/v1/establecimientos/{id}/dashboard/centro-mando?rodeoId={rodeoId}
     * <p>
     * Pestaña "Centro de mando" (H0043): alertas pendientes, ventanas de inseminación, animales en observación,
     * cobertura de caravanas, cola de atención, embudo reproductivo y tendencia de 8 semanas.
     *
     * @return 200 con las métricas, 400 si el rodeo no existe, 403 si el usuario no pertenece al establecimiento o el
     *         rodeo es de otro
     */
    @GetMapping("/centro-mando")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> centroMando(@PathVariable Long id,
            @RequestParam(name = "rodeoId", required = false) Long rodeoId,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(dashboardService.centroMando(authentication.getName(), id, rodeoId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/establecimientos/{id}/dashboard/salud-reproduccion?rodeoId={rodeoId}&periodoDias={dias}
     * <p>
     * Pestaña "Salud y reproducción" (H0044): indicadores, embudo con sus excepciones, animales a revisar, partos
     * esperados, desempeño reproductivo, reincidencia y última intervención.
     *
     * @return 200 con las métricas, 400 si el rodeo no existe o el período no es válido, 403 si el usuario no
     *         pertenece al establecimiento o el rodeo es de otro
     */
    @GetMapping("/salud-reproduccion")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> saludReproduccion(@PathVariable Long id,
            @RequestParam(name = "rodeoId", required = false) Long rodeoId,
            @RequestParam(name = "periodoDias", defaultValue = "30") int periodoDias,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(
                    dashboardService.saludReproduccion(authentication.getName(), id, rodeoId, periodoDias));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/establecimientos/{id}/dashboard/resumen-ejecutivo?periodoDias={dias}
     * <p>
     * Pestaña "Resumen ejecutivo" (H0045): tabla por rodeo, tendencia de 8 semanas y actuación del equipo.
     *
     * @return 200 con las métricas, 400 si el período no es válido, 403 si el usuario no pertenece al establecimiento
     */
    @GetMapping("/resumen-ejecutivo")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> resumenEjecutivo(@PathVariable Long id,
            @RequestParam(name = "periodoDias", defaultValue = "30") int periodoDias,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(dashboardService.resumenEjecutivo(authentication.getName(), id, periodoDias));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
