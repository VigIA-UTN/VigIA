package dev.vigia.backend.service;

import dev.vigia.backend.dto.UsuarioResponse;

import java.util.List;

public interface UsuarioService {
    List<UsuarioResponse> getProductores();
    void desactivarProductor(Long id);
    List<UsuarioResponse> getEmpleados(String emailProductor);
    void desactivarEmpleado(String emailProductor, Long empleadoId);
    List<UsuarioResponse> getAllUsuariosNoAdmins();
    void toggleUsuarioEstado(Long id);
    void toggleEstadoEmpleado(String emailProductor, Long empleadoId);
}
