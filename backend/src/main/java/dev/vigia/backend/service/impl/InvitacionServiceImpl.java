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
import java.util.List;
import java.util.Optional;
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

                Usuario adminUser = usuarioRepository.findByEmail(emailAdmin)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Usuario administrador no encontrado."));

                Establecimiento establecimiento = establecimientoRepository.findById(request.getEstablecimientoId())
                                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

                var usuarioExistente = usuarioRepository.findByEmail(request.getEmail());
                if (usuarioExistente.isPresent()) {
                        Usuario productor = usuarioExistente.get();

                        Optional<Empleado> vinculoActivoOpt = establecimiento.getEmpleados().stream()
                                .filter(v -> v.getUsuario().equals(productor) && v.isActivo())
                                .findFirst();

                        if (vinculoActivoOpt.isPresent()) {
                                throw new IllegalArgumentException("El productor ya pertenece a este establecimiento y está activo.");
                        } else {
                                Empleado nuevoVinculo = new Empleado(establecimiento, productor);
                                establecimiento.getEmpleados().add(nuevoVinculo);
                        }
                        establecimientoRepository.save(establecimiento);

                        emailService.enviarNuevoEstablecimientoProductor(productor.getEmail(),
                                        establecimiento.getNombre());

                        return InvitacionResponse.builder()
                                        .emailDestino(productor.getEmail())
                                        .rol(productor.getRol().name())
                                        .estado(EstadoInvitacion.USADA.name())
                                        .build();
                }

                boolean existsVigente = invitacionRepository
                                .existsByEmailDestinoAndEstablecimientoAndEstadoInvitacionAndFechaExpiracionAfter(
                                                request.getEmail(),
                                                establecimiento,
                                                EstadoInvitacion.PENDIENTE,
                                                LocalDateTime.now());

                if (existsVigente) {
                        throw new IllegalArgumentException(
                                        "No se puede generar una nueva invitación. Ya existe una invitación pendiente vigente para este email.");
                }

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

                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                if (productorUser.getRol() != Rol.PRODUCTOR) {
                        throw new IllegalArgumentException("Solo un productor puede generar esta invitación.");
                }

                Establecimiento establecimiento = establecimientoRepository.findById(request.getEstablecimientoId())
                                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

                boolean productorActivoEnEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!productorActivoEnEstablecimiento) {
                        throw new IllegalArgumentException("El productor no pertenece a este establecimiento o no está activo.");
                }

                if (request.getRol() == Rol.ADMINISTRADOR || request.getRol() == Rol.PRODUCTOR
                                || request.getRol() == Rol.PENDIENTE) {
                        throw new IllegalArgumentException("El rol especificado no es válido para un empleado.");
                }

                var usuarioExistente = usuarioRepository.findByEmail(request.getEmail());
                if (usuarioExistente.isPresent()) {
                        Usuario empleado = usuarioExistente.get();

                        Optional<Empleado> vinculoActivoOpt = establecimiento.getEmpleados().stream()
                                .filter(v -> v.getUsuario().equals(empleado) && v.isActivo())
                                .findFirst();

                        if (vinculoActivoOpt.isPresent()) {
                                throw new IllegalArgumentException("El empleado ya pertenece a este establecimiento y está activo.");
                        }
                }

                boolean existsVigente = invitacionRepository
                                .existsByEmailDestinoAndEstablecimientoAndEstadoInvitacionAndFechaExpiracionAfter(
                                                request.getEmail(),
                                                establecimiento,
                                                EstadoInvitacion.PENDIENTE,
                                                LocalDateTime.now());

                if (existsVigente) {
                        throw new IllegalArgumentException(
                                        "No se puede generar una nueva invitación. Ya existe una invitación pendiente vigente para este email.");
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

                if (usuarioExistente.isEmpty()) {
                        emailService.enviarInvitacionEmpleado(saved.getEmailDestino(), token, establecimiento.getNombre(),
                                        saved.getRol().name());
                } else {
                        emailService.enviarInvitacionPendienteUsuarioExistente(saved.getEmailDestino(), establecimiento.getNombre(),
                                        saved.getRol().name());
                }

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

        public List<InvitacionResponse> listarInvitacionesProductor() {
                return invitacionRepository
                                .findByTipoInvitacionOrderByFechaCreacionDesc(TipoInvitacion.PRODUCTOR)
                                .stream()
                                .map(inv -> {
                                        Usuario usuario = usuarioRepository.findByEmail(inv.getEmailDestino())
                                                        .orElse(null);
                                        String nombreCompleto = usuario != null
                                                        ? usuario.getNombre() + " " + usuario.getApellido()
                                                        : null;
                                        return InvitacionResponse.builder()
                                                        .id(inv.getId())
                                                        .nombreCompleto(nombreCompleto)
                                                        .emailDestino(inv.getEmailDestino())
                                                        .rol(inv.getRol().name())
                                                        .establecimiento(inv.getEstablecimiento() != null
                                                                        ? inv.getEstablecimiento().getNombre()
                                                                        : null)
                                                        .token(inv.getToken())
                                                        .fechaCreacion(inv.getFechaCreacion())
                                                        .fechaExpiracion(inv.getFechaExpiracion())
                                                        .estado(inv.getEstadoInvitacion().name())
                                                        .build();
                                })
                                .toList();
        }

        public List<InvitacionResponse> listarInvitacionesEmpleado(String emailProductor) {
                return invitacionRepository
                                .findByCreador_EmailAndTipoInvitacionOrderByFechaCreacionDesc(emailProductor,
                                                TipoInvitacion.EMPLEADO)
                                .stream()
                                .map(inv -> {
                                        Usuario usuario = usuarioRepository.findByEmail(inv.getEmailDestino())
                                                        .orElse(null);
                                        String nombreCompleto = usuario != null
                                                        ? usuario.getNombre() + " " + usuario.getApellido()
                                                        : null;
                                        return InvitacionResponse.builder()
                                                        .id(inv.getId())
                                                        .nombreCompleto(nombreCompleto)
                                                        .emailDestino(inv.getEmailDestino())
                                                        .rol(inv.getRol().name())
                                                        .establecimiento(inv.getEstablecimiento() != null
                                                                        ? inv.getEstablecimiento().getNombre()
                                                                        : null)
                                                        .token(inv.getToken())
                                                        .fechaCreacion(inv.getFechaCreacion())
                                                        .fechaExpiracion(inv.getFechaExpiracion())
                                                        .estado(inv.getEstadoInvitacion().name())
                                                        .build();
                                })
                                .toList();
        }

        public List<InvitacionResponse> listarMisInvitaciones(String email) {
                return invitacionRepository
                                .findByEmailDestinoAndEstadoInvitacionOrderByFechaCreacionDesc(email, EstadoInvitacion.PENDIENTE)
                                .stream()
                                .map(inv -> {
                                        Usuario creador = inv.getCreador();
                                        String nombreCompleto = creador != null
                                                        ? creador.getNombre() + " " + creador.getApellido()
                                                        : null;
                                        return InvitacionResponse.builder()
                                                        .id(inv.getId())
                                                        .nombreCompleto(nombreCompleto)
                                                        .emailDestino(inv.getEmailDestino())
                                                        .rol(inv.getRol().name())
                                                        .establecimiento(inv.getEstablecimiento() != null
                                                                        ? inv.getEstablecimiento().getNombre()
                                                                        : null)
                                                        .token(inv.getToken())
                                                        .fechaCreacion(inv.getFechaCreacion())
                                                        .fechaExpiracion(inv.getFechaExpiracion())
                                                        .estado(inv.getEstadoInvitacion().name())
                                                        .build();
                                })
                                .toList();
        }

        @Transactional
        public void aceptarInvitacion(Long id, String email) {
                Invitacion invitacion = invitacionRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Invitación no encontrada"));

                if (!invitacion.getEmailDestino().equals(email)) {
                        throw new IllegalArgumentException("La invitación no te pertenece");
                }
                if (invitacion.getEstadoInvitacion() != EstadoInvitacion.PENDIENTE) {
                        throw new IllegalArgumentException("La invitación ya no está pendiente");
                }

                Usuario usuario = usuarioRepository.findByEmail(email)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
                
                if (usuario.getRol() == Rol.PENDIENTE) {
                        usuario.setRol(invitacion.getRol());
                        usuarioRepository.save(usuario);
                }

                Establecimiento establecimiento = invitacion.getEstablecimiento();

                Empleado nuevoVinculo = new Empleado(establecimiento, usuario);
                establecimiento.getEmpleados().add(nuevoVinculo);
                establecimientoRepository.save(establecimiento);

                invitacion.setEstadoInvitacion(EstadoInvitacion.USADA);
                invitacionRepository.save(invitacion);
        }

        @Transactional
        public void aceptarInvitacionPorToken(String token, String email) {
                Invitacion invitacion = invitacionRepository.findByToken(token)
                                .orElseThrow(() -> new IllegalArgumentException("Invitación no encontrada"));
                aceptarInvitacion(invitacion.getId(), email);
        }

        @Transactional
        public void rechazarInvitacion(Long id, String email) {
                Invitacion invitacion = invitacionRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Invitación no encontrada"));

                if (!invitacion.getEmailDestino().equals(email)) {
                        throw new IllegalArgumentException("La invitación no te pertenece");
                }
                if (invitacion.getEstadoInvitacion() != EstadoInvitacion.PENDIENTE) {
                        throw new IllegalArgumentException("La invitación ya no está pendiente");
                }

                invitacion.setEstadoInvitacion(EstadoInvitacion.RECHAZADA);
                invitacionRepository.save(invitacion);
        }
}
