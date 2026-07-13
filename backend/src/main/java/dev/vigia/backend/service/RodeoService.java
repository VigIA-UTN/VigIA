package dev.vigia.backend.service;

import java.util.List;

import dev.vigia.backend.dto.RodeoRequest;
import dev.vigia.backend.dto.RodeoResponse;

public interface RodeoService {

    RodeoResponse generarRodeo(RodeoRequest request, String emailProductor);

    void desactivarRodeo(Long rodeoId, String emailProductor);

    RodeoResponse getRodeoById(Long id, String emailProductor);

    List<RodeoResponse> getAllRodeos();

    List<RodeoResponse> getRodeosByUsuario(String emailUsuario);

}
