package dev.vigia.backend.service;

import java.util.List;

import dev.vigia.backend.dto.CaravanaRequest;
import dev.vigia.backend.dto.CaravanaResponse;

public interface CaravanaService {
    public CaravanaResponse crearCaravana(CaravanaRequest caravanaRequest, String email);

    public List<CaravanaResponse> obtenerSensores(String email, Long establecimientoId);
}
