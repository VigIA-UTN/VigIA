package dev.vigia.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.service.AlertaService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/alertas")
@RequiredArgsConstructor
public class AlertaController {

    private final AlertaService alertaService;

    /**
     * GET /api/v1/alertas/celo?establecimientoId={id}&estado={estado}
     * <p>
     * Lista las alertas de celo del establecimiento seleccionado por el usuario
     * en el toggle de la página principal (mapa). El frontend envía el ID del
     * establecimiento activo; el backend valida que el usuario pertenezca a él.
     *
     * @param establecimientoId ID del establecimiento actualmente seleccionado (obligatorio)
     * @param estado            (opcional) filtro por estado: PENDIENTE, CONFIRMADA o DESCARTADA
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de alertas, 400 si el usuario no existe, 403 si no pertenece
     */
    @GetMapping("/celo")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> listarAlertasCelo(
            @RequestParam(name = "establecimientoId") Long establecimientoId,
            @RequestParam(name = "estado", required = false) EstadoAlerta estado,
            Authentication authentication) {

        try {
            String email = authentication.getName();
            List<AlertaResponse> response =
                    alertaService.listarAlertasCelo(email, establecimientoId, estado);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
