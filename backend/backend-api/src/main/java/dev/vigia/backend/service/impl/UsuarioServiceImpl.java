package dev.vigia.backend.service.impl;

import dev.vigia.backend.service.UsuarioService;
import dev.vigia.backend.dto.CambiarPasswordRequest;
import dev.vigia.backend.dto.UpdatePerfilRequest;
import dev.vigia.backend.dto.UsuarioResponse;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UsuarioResponse> getProductores() {
        return usuarioRepository.findByRolAndActivoTrue(Rol.PRODUCTOR).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("null")
    public void desactivarProductor(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (usuario.getRol() != Rol.PRODUCTOR) {
            throw new IllegalArgumentException("El usuario no es un productor");
        }
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    @SuppressWarnings("null")
    public List<UsuarioResponse> getEmpleados(String emailProductor, Long establecimientoId) {
        Usuario productor = usuarioRepository.findByEmail(emailProductor)
                .orElseThrow(() -> new IllegalArgumentException("Productor no encontrado"));

        List<Establecimiento> establecimientos;
        if (establecimientoId != null) {
            Establecimiento est = establecimientoRepository.findById(establecimientoId)
                    .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado"));
            boolean pertenece = est.getEmpleados().stream()
                    .anyMatch(v -> v.getUsuario().equals(productor) && v.isActivo());
            if (!pertenece) {
                throw new AccessDeniedException(
                        "No tiene acceso a este establecimiento");
            }
            establecimientos = List.of(est);
        } else {
            establecimientos = establecimientoRepository
                    .findActiveEstablecimientoByUsuarioId(productor.getId());
        }

        List<UsuarioResponse> responses = new ArrayList<>();
        for (Establecimiento est : establecimientos) {
            List<UsuarioResponse> emps = est.getEmpleados().stream()
                    .filter(Empleado::isActivo)
                    .map(Empleado::getUsuario)
                    .filter(u -> u.getRol() != Rol.PRODUCTOR && u.isActivo())
                    .map(u -> {
                        UsuarioResponse resp = mapToResponse(u);
                        resp.setEstablecimiento(est.getNombre());
                        resp.setEstablecimientoId(est.getId());
                        return resp;
                    })
                    .collect(Collectors.toList());
            responses.addAll(emps);
        }
        return responses;
    }

    @SuppressWarnings("null")
    public void removerEmpleado(String emailProductor, Long empleadoId, Long establecimientoId) {
        Usuario productor = usuarioRepository.findByEmail(emailProductor)
                .orElseThrow(() -> new IllegalArgumentException("Productor no encontrado"));

        Establecimiento establecimiento = establecimientoRepository.findById(establecimientoId)
                .filter(e -> e.isActivo()
                        && e.getEmpleados().stream().anyMatch(v -> v.getUsuario().equals(productor) && v.isActivo()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Establecimiento no encontrado o el productor no pertenece al mismo"));

        Usuario empleado = usuarioRepository.findById(empleadoId)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));

        Optional<Empleado> vinculoOpt = establecimiento.getEmpleados().stream()
                .filter(v -> v.getUsuario().equals(empleado) && v.isActivo())
                .findFirst();

        if (vinculoOpt.isEmpty()) {
            throw new IllegalArgumentException(
                    "El empleado no pertenece al establecimiento del productor o ya está desvinculado");
        }

        if (empleado.getRol() == Rol.PRODUCTOR) {
            throw new IllegalArgumentException("No se puede remover a un productor");
        }

        Empleado vinculo = vinculoOpt.get();
        vinculo.setActivo(false);
        vinculo.setFechaDesvinculacion(LocalDateTime.now());

        establecimientoRepository.save(establecimiento);
    }

    public List<UsuarioResponse> getAllUsuariosNoAdmins() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() != Rol.ADMINISTRADOR)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("null")
    public void toggleUsuarioEstado(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (usuario.getRol() == Rol.ADMINISTRADOR) {
            throw new IllegalArgumentException("No se puede modificar el estado de un administrador");
        }
        usuario.setActivo(!usuario.isActivo());
        usuarioRepository.save(usuario);
    }

    public UsuarioResponse getMiPerfil(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        return mapToResponse(usuario);
    }

    @Transactional
    @SuppressWarnings("null")
    public UsuarioResponse updateMiPerfil(String email, UpdatePerfilRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (request.getNombre() != null && !request.getNombre().isBlank()) {
            usuario.setNombre(request.getNombre());
        }
        if (request.getApellido() != null) {
            usuario.setApellido(request.getApellido());
        }

        usuario = usuarioRepository.save(usuario);
        return mapToResponse(usuario);
    }

    @Transactional
    public void cambiarPassword(String email, CambiarPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (usuario.getProveedorAuth() != null && !usuario.getProveedorAuth().equals("LOCAL")) {
            throw new IllegalArgumentException(
                    "Los usuarios registrados mediante SSO no pueden cambiar la contraseña localmente.");
        }

        if (!passwordEncoder.matches(request.getPasswordActual(), usuario.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta.");
        }

        validatePassword(request.getNuevaPassword());

        usuario.setPasswordHash(passwordEncoder.encode(request.getNuevaPassword()));
        usuarioRepository.save(usuario);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 32) {
            throw new IllegalArgumentException("La contraseña debe tener entre 8 y 32 caracteres.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("La contraseña debe tener al menos una letra mayúscula.");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("La contraseña debe tener al menos una letra minúscula.");
        }
        if (!password.matches(".*[0-9\\W_].*")) {
            throw new IllegalArgumentException("La contraseña debe tener al menos un número o carácter especial.");
        }
    }

    private UsuarioResponse mapToResponse(Usuario usuario) {
        return UsuarioResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .email(usuario.getEmail())
                .rol(usuario.getRol())
                .activo(usuario.isActivo())
                .proveedorAuth(usuario.getProveedorAuth())
                .build();
    }
}
