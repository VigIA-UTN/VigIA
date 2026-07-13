package dev.vigia.backend.service.impl;

import dev.vigia.backend.service.UsuarioService;

import dev.vigia.backend.dto.UsuarioResponse;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    public List<UsuarioResponse> getEmpleados(String emailProductor) {
        Usuario productor = usuarioRepository.findByEmail(emailProductor)
                .orElseThrow(() -> new IllegalArgumentException("Productor no encontrado"));

        List<Establecimiento> establecimientos = establecimientoRepository
                .findActiveEstablecimientoByUsuarioId(productor.getId());

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

    private UsuarioResponse mapToResponse(Usuario usuario) {
        return UsuarioResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .email(usuario.getEmail())
                .rol(usuario.getRol())
                .activo(usuario.isActivo())
                .build();
    }
}
