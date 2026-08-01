package dev.vigia.backend.service.impl;

import dev.vigia.backend.service.EstablecimientoService;

import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.dto.EstablecimientoResponse;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.repository.EstablecimientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EstablecimientoServiceImpl implements EstablecimientoService {

    private final EstablecimientoRepository establecimientoRepository;

    public EstablecimientoResponse createEstablecimiento(EstablecimientoRequest request) {
        Establecimiento est = new Establecimiento();
        est.setNombre(request.getNombre());
        est.setLatitud(request.getLatitud());
        est.setLongitud(request.getLongitud());
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());

        Establecimiento saved = establecimientoRepository.save(est);
        return mapToResponse(saved);
    }

    public List<EstablecimientoResponse> getAllEstablecimientos() {
        return establecimientoRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<EstablecimientoResponse> getEstablecimientosByUsuario(String email) {
        return establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(email).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("null")
    public EstablecimientoResponse getEstablecimientoById(Long id) {
        return establecimientoRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
    }

    @SuppressWarnings("null")
    public EstablecimientoResponse updateEstablecimiento(Long id, EstablecimientoRequest request) {
        Establecimiento est = establecimientoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
        est.setNombre(request.getNombre());
        est.setLatitud(request.getLatitud());
        est.setLongitud(request.getLongitud());
        return mapToResponse(establecimientoRepository.save(est));
    }

    @SuppressWarnings("null")
    public EstablecimientoResponse desactivarEstablecimiento(Long id) {
        Establecimiento est = establecimientoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
        est.setActivo(false);
        return mapToResponse(establecimientoRepository.save(est));
    }

    @SuppressWarnings("null")
    public EstablecimientoResponse activarEstablecimiento(Long id) {
        Establecimiento est = establecimientoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));
        est.setActivo(true);
        return mapToResponse(establecimientoRepository.save(est));
    }

    private EstablecimientoResponse mapToResponse(Establecimiento est) {
        return EstablecimientoResponse.builder()
                .id(est.getId())
                .nombre(est.getNombre())
                .latitud(est.getLatitud())
                .longitud(est.getLongitud())
                .activo(est.isActivo())
                .fechaAlta(est.getFechaAlta())
                .build();
    }
}
