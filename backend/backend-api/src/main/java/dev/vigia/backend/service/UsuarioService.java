package dev.vigia.backend.service;

import dev.vigia.backend.dto.UsuarioResponse;

import java.util.List;

public interface UsuarioService {
    List<UsuarioResponse> getProductores();
    void desactivarProductor(Long id);
    List<UsuarioResponse> getEmpleados(String emailProductor, Long establecimientoId);
    void removerEmpleado(String emailProductor, Long empleadoId, Long establecimientoId);
    List<UsuarioResponse> getAllUsuariosNoAdmins();
    void toggleUsuarioEstado(Long id);
}
