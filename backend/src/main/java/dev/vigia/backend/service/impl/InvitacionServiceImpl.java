package dev.vigia.backend.service.impl;

import dev.vigia.backend.service.InvitacionService;
import dev.vigia.backend.service.EmailService;

import dev.vigia.backend.dto.InvitacionEmpleadoRequest;
import dev.vigia.backend.dto.InvitacionProductorRequest;
import dev.vigia.backend.dto.InvitacionResponse;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitacionServiceImpl implements InvitacionService {

    private final InvitacionRepository invitacionRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;

    @Transactional
    @SuppressWarnings("null")
    public InvitacionResponse generarInvitacionProductor(InvitacionProductorRequest request, String emailAdmin) {
        
        boolean existsVigente = invitacionRepository.existsByEmailDestinoAndEstadoInvitacionAndFechaExpiracionAfter(
                request.getEmail(), 
                EstadoInvitacion.PENDIENTE, 
                LocalDateTime.now()
        );
        
        if (existsVigente) {
            throw new IllegalArgumentException("No se puede generar una nueva invitación. Ya existe una invitación pendiente vigente para este email.");
        }

        Usuario adminUser = usuarioRepository.findByEmail(emailAdmin)
                .orElseThrow(() -> new IllegalArgumentException("Usuario administrador no encontrado."));

        Establecimiento establecimiento = establecimientoRepository.findById(request.getEstablecimientoId())
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

        String token = UUID.randomUUID().toString();
        
        Invitacion invitacion = new Invitacion();
        invitacion.setEmailDestino(request.getEmail());
        invitacion.setRol(Rol.PRODUCTOR);
        invitacion.setTipoInvitacion(TipoInvitacion.PRODUCTOR);
        invitacion.setEstadoInvitacion(EstadoInvitacion.PENDIENTE);
        invitacion.setCreador(adminUser);
        invitacion.setEstablecimiento(establecimiento);
        invitacion.setToken(token);
        invitacion.setFechaCreacion(LocalDateTime.now());
        invitacion.setFechaExpiracion(LocalDateTime.now().plusDays(7));

        Invitacion saved = invitacionRepository.save(invitacion);

        emailService.enviarInvitacionProductor(saved.getEmailDestino(), token, establecimiento.getNombre());

        return InvitacionResponse.builder()
                .id(saved.getId())
                .emailDestino(saved.getEmailDestino())
                .rol(saved.getRol().name())
                .token(saved.getToken())
                .fechaCreacion(saved.getFechaCreacion())
                .fechaExpiracion(saved.getFechaExpiracion())
                .estado(saved.getEstadoInvitacion().name())
                .build();
    }

    @Transactional
    @SuppressWarnings("null")
    public InvitacionResponse generarInvitacionEmpleado(InvitacionEmpleadoRequest request, String emailProductor) {
        
        boolean existsVigente = invitacionRepository.existsByEmailDestinoAndEstadoInvitacionAndFechaExpiracionAfter(
                request.getEmail(), 
                EstadoInvitacion.PENDIENTE, 
                LocalDateTime.now()
        );
        
        if (existsVigente) {
            throw new IllegalArgumentException("No se puede generar una nueva invitación. Ya existe una invitación pendiente vigente para este email.");
        }

        Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

        if (productorUser.getRol() != Rol.PRODUCTOR) {
            throw new IllegalArgumentException("Solo un productor puede generar esta invitación.");
        }

        Establecimiento establecimiento = establecimientoRepository.findById(request.getEstablecimientoId())
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

        if (!establecimiento.getUsuarios().contains(productorUser)) {
            throw new IllegalArgumentException("El productor no pertenece a este establecimiento.");
        }

        if (request.getRol() == Rol.ADMINISTRADOR || request.getRol() == Rol.PRODUCTOR || request.getRol() == Rol.PENDIENTE) {
            throw new IllegalArgumentException("El rol especificado no es válido para un empleado.");
        }

        String token = UUID.randomUUID().toString();
        
        Invitacion invitacion = new Invitacion();
        invitacion.setEmailDestino(request.getEmail());
        invitacion.setRol(request.getRol());
        invitacion.setTipoInvitacion(TipoInvitacion.EMPLEADO);
        invitacion.setEstadoInvitacion(EstadoInvitacion.PENDIENTE);
        invitacion.setCreador(productorUser);
        invitacion.setEstablecimiento(establecimiento);
        invitacion.setToken(token);
        invitacion.setFechaCreacion(LocalDateTime.now());
        invitacion.setFechaExpiracion(LocalDateTime.now().plusDays(7));

        Invitacion saved = invitacionRepository.save(invitacion);

        emailService.enviarInvitacionEmpleado(saved.getEmailDestino(), token, establecimiento.getNombre(), saved.getRol().name());

        return InvitacionResponse.builder()
                .id(saved.getId())
                .emailDestino(saved.getEmailDestino())
                .rol(saved.getRol().name())
                .token(saved.getToken())
                .fechaCreacion(saved.getFechaCreacion())
                .fechaExpiracion(saved.getFechaExpiracion())
                .estado(saved.getEstadoInvitacion().name())
                .build();
    }
}
