package dev.vigia.backend.controller;

import dev.vigia.backend.dto.CaravanaBajaRequest;
import dev.vigia.backend.dto.CaravanaRequest;
import dev.vigia.backend.dto.CaravanaResponse;
import dev.vigia.backend.exception.DevEuiDuplicadoException;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.service.CaravanaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            @RequestParam(name = "establecimientoId", required = false) Long establecimientoId,
            @RequestParam(name = "estado", required = false) EstadoSensor estado,
            @RequestParam(name = "codigo", required = false) String codigo,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            return ResponseEntity.ok(caravanaService.listarCaravanas(email, establecimientoId, estado, codigo));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Establecimiento no encontrado.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/disponibles")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('CAMPERO')")
    public ResponseEntity<?> listarCaravanasDisponibles(
            @RequestParam("establecimientoId") Long establecimientoId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            return ResponseEntity.ok(caravanaService.listarCaravanasDisponibles(establecimientoId, email));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Establecimiento no encontrado.".equals(e.getMessage()) || "Usuario no encontrado".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/baja")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> darDeBajaCaravana(
            @PathVariable Long id,
            @RequestBody(required = false) CaravanaBajaRequest request,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            String motivo = request != null ? request.getMotivo() : null;
            return ResponseEntity.ok(caravanaService.darDeBajaCaravana(id, motivo, email));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Caravana no encontrada.".equals(e.getMessage()) || "Usuario no encontrado".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/reactivar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> reactivarCaravana(@PathVariable Long id, Authentication authentication) {
        try {
            String email = authentication.getName();
            return ResponseEntity.ok(caravanaService.reactivarCaravana(id, email));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Caravana no encontrada.".equals(e.getMessage()) || "Usuario no encontrado".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
