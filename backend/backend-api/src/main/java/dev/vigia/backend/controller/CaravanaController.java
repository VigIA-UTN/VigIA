package dev.vigia.backend.controller;

import dev.vigia.backend.dto.CaravanaRequest;
import dev.vigia.backend.dto.CaravanaResponse;
import dev.vigia.backend.exception.DevEuiDuplicadoException;
import dev.vigia.backend.service.CaravanaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/caravana")
@RequiredArgsConstructor
public class CaravanaController {

    private final CaravanaService caravanaService;

    /**
     * POST /api/v1/caravana
     * <p>
     * Crea una nueva caravana (dispositivo sensor) y la asocia a un
     * establecimiento.
     *
     * @param caravanaRequest datos de la caravana a crear
     * @return 201 con la caravana creada, 404 si el establecimiento no existe,
     *         409 si el DevEUI ya está registrado, 400 si hay error de
     *         validación
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> crearCaravana(@Valid @RequestBody CaravanaRequest caravanaRequest) {
        try {
            CaravanaResponse caravanaResponse = caravanaService.crearCaravana(caravanaRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(caravanaResponse);
        } catch (DevEuiDuplicadoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Establecimiento no encontrado.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/caravana?establecimientoId={id}
     * <p>
     * Lista las caravanas de un establecimiento.
     *
     * @param establecimientoId ID del establecimiento
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de caravanas, 404 si el establecimiento no existe,
     *         403 si acceso denegado, 400 si hay error
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'PRODUCTOR', 'CAMPERO')")
    public ResponseEntity<?> listarCaravanas(
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            return ResponseEntity.ok(caravanaService.listarCaravanas(email, establecimientoId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Establecimiento no encontrado.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
