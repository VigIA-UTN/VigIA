package dev.vigia.backend.service;

import java.util.List;

import dev.vigia.backend.dto.SensorRequest;
import dev.vigia.backend.dto.SensorResponse;

public interface SensorService {
    SensorResponse crearSensor(SensorRequest sensorRequest);

    List<SensorResponse> listarSensores(Long establecimientoId);
}
