package dev.vigia.backend.controller;

import dev.vigia.backend.dto.SensorRequest;
import dev.vigia.backend.dto.SensorResponse;
import dev.vigia.backend.exception.DevEuiDuplicadoException;
import dev.vigia.backend.service.SensorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sensores")
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> crearSensor(@Valid @RequestBody SensorRequest sensorRequest) {
        try {
            SensorResponse response = sensorService.crearSensor(sensorRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (DevEuiDuplicadoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Establecimiento no encontrado".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> listarSensores(@RequestParam("establecimientoId") Long establecimientoId) {
        try {
            return ResponseEntity.ok(sensorService.listarSensores(establecimientoId));
        } catch (IllegalArgumentException e) {
            if ("Establecimiento no encontrado".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
