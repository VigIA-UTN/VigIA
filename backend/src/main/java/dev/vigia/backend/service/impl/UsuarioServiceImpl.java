package dev.vigia.backend.service.impl;

import dev.vigia.backend.service.UsuarioService;

import dev.vigia.backend.dto.UsuarioResponse;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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

    public List<UsuarioResponse> getEmpleados(String emailProductor) {
        Usuario productor = usuarioRepository.findByEmail(emailProductor)
                .orElseThrow(() -> new IllegalArgumentException("Productor no encontrado"));

        Establecimiento establecimiento = establecimientoRepository.findByUsuariosId(productor.getId())
                .orElseThrow(() -> new IllegalArgumentException("El productor no tiene un establecimiento asignado"));

        return establecimiento.getUsuarios().stream()
                .filter(u -> u.getRol() != Rol.PRODUCTOR && u.isActivo())
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("null")
    public void desactivarEmpleado(String emailProductor, Long empleadoId) {
        Usuario productor = usuarioRepository.findByEmail(emailProductor)
                .orElseThrow(() -> new IllegalArgumentException("Productor no encontrado"));

        Establecimiento establecimiento = establecimientoRepository.findByUsuariosId(productor.getId())
                .orElseThrow(() -> new IllegalArgumentException("El productor no tiene un establecimiento asignado"));

        Usuario empleado = usuarioRepository.findById(empleadoId)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));

        if (!establecimiento.getUsuarios().contains(empleado)) {
            throw new IllegalArgumentException("El empleado no pertenece al establecimiento del productor");
        }

        if (empleado.getRol() == Rol.PRODUCTOR) {
            throw new IllegalArgumentException("No se puede desactivar a un productor");
        }

        empleado.setActivo(false);
        usuarioRepository.save(empleado);
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

    @SuppressWarnings("null")
    public void toggleEstadoEmpleado(String emailProductor, Long empleadoId) {
        Usuario productor = usuarioRepository.findByEmail(emailProductor)
                .orElseThrow(() -> new IllegalArgumentException("Productor no encontrado"));

        Establecimiento establecimiento = establecimientoRepository.findByUsuariosId(productor.getId())
                .orElseThrow(() -> new IllegalArgumentException("El productor no tiene un establecimiento asignado"));

        Usuario empleado = usuarioRepository.findById(empleadoId)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));

        if (!establecimiento.getUsuarios().contains(empleado)) {
            throw new IllegalArgumentException("El empleado no pertenece al establecimiento del productor");
        }

        if (empleado.getRol() == Rol.PRODUCTOR) {
            throw new IllegalArgumentException("No se puede desactivar a un productor");
        }

        empleado.setActivo(!empleado.isActivo());
        usuarioRepository.save(empleado);
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
