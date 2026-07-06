package dev.vigia.backend.service;

import dev.vigia.backend.dto.UsuarioResponse;

import java.util.List;

public interface UsuarioService {
    List<UsuarioResponse> getProductores();
    void desactivarProductor(Long id);
}
