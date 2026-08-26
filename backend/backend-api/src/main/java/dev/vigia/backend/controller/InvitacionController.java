package dev.vigia.backend.controller;

import dev.vigia.backend.dto.InvitacionEmpleadoRequest;
import dev.vigia.backend.dto.InvitacionProductorRequest;
import dev.vigia.backend.dto.InvitacionResponse;
import dev.vigia.backend.service.InvitacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/invitaciones")
@RequiredArgsConstructor
public class InvitacionController {

    private final InvitacionService invitacionService;

    @PostMapping("/productor")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> generarInvitacionProductor(
            @Valid @RequestBody InvitacionProductorRequest request,
            Authentication authentication) {

        try {
            String emailAdmin = authentication.getName();
            InvitacionResponse response = invitacionService.generarInvitacionProductor(request, emailAdmin);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/empleado")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> generarInvitacionEmpleado(
            @Valid @RequestBody InvitacionEmpleadoRequest request,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            InvitacionResponse response = invitacionService.generarInvitacionEmpleado(request, emailProductor);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }
    @GetMapping("/productor")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<InvitacionResponse>> listarInvitacionesProductor() {
        return ResponseEntity.ok(invitacionService.listarInvitacionesProductor());
    }

    @GetMapping("/empleado")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<List<InvitacionResponse>> listarInvitacionesEmpleado(
            @RequestParam(name = "establecimientoId", required = false) Long establecimientoId,
            Authentication authentication) {
        return ResponseEntity.ok(invitacionService.listarInvitacionesEmpleado(authentication.getName(), establecimientoId));
    }

    @GetMapping("/mis-invitaciones")
    public ResponseEntity<List<InvitacionResponse>> listarMisInvitaciones(Authentication authentication) {
        return ResponseEntity.ok(invitacionService.listarMisInvitaciones(authentication.getName()));
    }

    @PostMapping("/{id}/aceptar")
    public ResponseEntity<Void> aceptarInvitacion(@PathVariable Long id, Authentication authentication) {
        invitacionService.aceptarInvitacion(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/token/{token}/aceptar")
    public ResponseEntity<Void> aceptarInvitacionPorToken(@PathVariable String token, Authentication authentication) {
        invitacionService.aceptarInvitacionPorToken(token, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<Void> rechazarInvitacion(@PathVariable Long id, Authentication authentication) {
        invitacionService.rechazarInvitacion(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'PRODUCTOR')")
    public ResponseEntity<Void> cancelarInvitacion(@PathVariable Long id, Authentication authentication) {
        invitacionService.cancelarInvitacion(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
