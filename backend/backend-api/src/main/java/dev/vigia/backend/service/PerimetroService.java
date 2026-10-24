package dev.vigia.backend.service;

import dev.vigia.backend.dto.PerimetroRequest;
import dev.vigia.backend.dto.PerimetroResponse;

import java.util.List;

public interface PerimetroService {

    PerimetroResponse crearPerimetro(PerimetroRequest request, String emailUsuario);

    PerimetroResponse actualizarPerimetro(Long perimetroId, PerimetroRequest request, String emailUsuario);

    void activarPerimetro(Long perimetroId, String emailUsuario);

    void desactivarPerimetro(Long perimetroId, String emailUsuario);

    void eliminarPerimetro(Long perimetroId, String emailUsuario);

    PerimetroResponse getPerimetroById(Long perimetroId, String emailUsuario);

    List<PerimetroResponse> getPerimetrosByEstablecimiento(Long establecimientoId, String emailUsuario);
}
