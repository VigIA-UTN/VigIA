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

@RestController
@RequestMapping("/api/v1/invitaciones")
@RequiredArgsConstructor
public class InvitacionController {

    private final InvitacionService invitacionService;

    @PostMapping("/productor")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<InvitacionResponse> generarInvitacionProductor(
            @Valid @RequestBody InvitacionProductorRequest request,
            Authentication authentication) {

        String emailAdmin = authentication.getName();
        InvitacionResponse response = invitacionService.generarInvitacionProductor(request, emailAdmin);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/empleado")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<InvitacionResponse> generarInvitacionEmpleado(
            @Valid @RequestBody InvitacionEmpleadoRequest request,
            Authentication authentication) {

        String emailProductor = authentication.getName();
        InvitacionResponse response = invitacionService.generarInvitacionEmpleado(request, emailProductor);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
}
