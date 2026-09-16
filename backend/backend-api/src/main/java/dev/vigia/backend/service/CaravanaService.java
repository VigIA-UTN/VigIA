package dev.vigia.backend.service;

import java.util.List;

import dev.vigia.backend.dto.CaravanaRequest;
import dev.vigia.backend.dto.CaravanaResponse;

public interface CaravanaService {
    CaravanaResponse crearCaravana(CaravanaRequest caravanaRequest);

    List<CaravanaResponse> listarCaravanas(String email, Long establecimientoId);
}
