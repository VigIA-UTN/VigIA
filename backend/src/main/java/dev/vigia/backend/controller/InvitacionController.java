package dev.vigia.backend.controller;

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
}
