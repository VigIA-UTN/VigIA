package dev.vigia.backend.service;

import dev.vigia.backend.dto.CaravanaRequest;
import dev.vigia.backend.dto.CaravanaResponse;

public interface CaravanaService {
    public CaravanaResponse crearCaravana(CaravanaRequest caravanaRequest, String email);
}
