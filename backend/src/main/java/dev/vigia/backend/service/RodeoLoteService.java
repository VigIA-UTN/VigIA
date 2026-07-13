package dev.vigia.backend.service;

import java.util.List;

import dev.vigia.backend.dto.RodeoLoteRequest;
import dev.vigia.backend.dto.RodeoLoteResponse;

public interface RodeoLoteService {

    RodeoLoteResponse generarRodeoLote(RodeoLoteRequest request, String emailProductor);

    void desactivarRodeoLote(Long rodeoLoteId, String emailProductor);

    RodeoLoteResponse getRodeoLoteById(Long id, String emailProductor);

    List<RodeoLoteResponse> getAllRodeoLotes();

    List<RodeoLoteResponse> getRodeoLotesByUsuario(String emailUsuario);

}
