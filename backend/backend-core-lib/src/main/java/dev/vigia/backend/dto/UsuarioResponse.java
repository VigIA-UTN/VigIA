package dev.vigia.backend.dto;

import dev.vigia.backend.model.Rol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private Rol rol;
    private boolean activo;
    private String establecimiento;
    private Long establecimientoId;
    private String proveedorAuth;
}
