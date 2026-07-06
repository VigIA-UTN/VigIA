package dev.vigia.backend.service.impl;

import dev.vigia.backend.service.UsuarioService;

import dev.vigia.backend.dto.UsuarioResponse;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;

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
