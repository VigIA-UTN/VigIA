package dev.vigia.backend.service;

import java.util.List;

import dev.vigia.backend.dto.CaravanaRequest;
import dev.vigia.backend.dto.CaravanaResponse;
import dev.vigia.backend.model.EstadoSensor;

public interface CaravanaService {
    CaravanaResponse crearCaravana(CaravanaRequest caravanaRequest);

    List<CaravanaResponse> listarCaravanas(String email, Long establecimientoId);

    List<CaravanaResponse> listarCaravanas(String email, Long establecimientoId, EstadoSensor estado, String codigo);

    List<CaravanaResponse> listarCaravanasDisponibles(Long establecimientoId, String emailUsuario);

    CaravanaResponse darDeBajaCaravana(Long caravanaId, String motivo, String email);

    CaravanaResponse reactivarCaravana(Long caravanaId, String email);
}
