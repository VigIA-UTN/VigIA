package dev.vigia.backend.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.vigia.backend.dto.SensorRequest;
import dev.vigia.backend.dto.SensorResponse;
import dev.vigia.backend.exception.DevEuiDuplicadoException;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.service.SensorService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SensorServiceImpl implements SensorService {

    private final CaravanaRepository caravanaRepository;
    private final EstablecimientoRepository establecimientoRepository;

    @Override
    public SensorResponse crearSensor(SensorRequest sensorRequest) {
        Establecimiento establecimiento = establecimientoRepository.findById(sensorRequest.getEstablecimientoId())
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado"));

        caravanaRepository.findByDevEui(sensorRequest.getDev_eui())
                .ifPresent(c -> {
                    throw new DevEuiDuplicadoException(
                            "Ya existe un sensor registrado con el dev_eui " + sensorRequest.getDev_eui());
                });

        Caravana sensor = new Caravana();
        sensor.setDevEui(sensorRequest.getDev_eui());
        sensor.setAlias(sensorRequest.getAlias());
        sensor.setEstablecimiento(establecimiento);
        sensor.setEstadoSensor(EstadoSensor.DISPONIBLE);
        sensor.setActivo(false);
        sensor.setFechaAlta(LocalDate.now());

        Caravana guardado = caravanaRepository.save(sensor);

        return toResponse(guardado);
    }

    @Override
    public List<SensorResponse> listarSensores(Long establecimientoId) {
        establecimientoRepository.findById(establecimientoId)
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado"));

        return caravanaRepository.findAllByEstablecimientoId(establecimientoId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private SensorResponse toResponse(Caravana caravana) {
        Animal animal = caravana.getAnimal();
        return SensorResponse.builder()
                .id(caravana.getId())
                .devEui(caravana.getDevEui())
                .alias(caravana.getAlias())
                .estadoSensor(caravana.getEstadoSensor())
                .establecimientoId(caravana.getEstablecimiento() != null ? caravana.getEstablecimiento().getId() : null)
                .animalId(animal != null ? animal.getId() : null)
                .animalAlias(animal != null ? animal.getAlias() : null)
                .build();
    }
}
