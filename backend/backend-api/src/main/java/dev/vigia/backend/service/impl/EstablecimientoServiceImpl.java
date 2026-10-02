package dev.vigia.backend.service.impl;

import dev.vigia.backend.service.EstablecimientoService;

import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.dto.EstablecimientoResponse;
import dev.vigia.backend.dto.IntervencionResponse;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Intervencion;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EstablecimientoServiceImpl implements EstablecimientoService {

    private final EstablecimientoRepository establecimientoRepository;
    private final IntervencionRepository intervencionRepository;
    private final UsuarioRepository usuarioRepository;

    public EstablecimientoResponse createEstablecimiento(EstablecimientoRequest request) {
        Establecimiento est = new Establecimiento();
        est.setNombre(request.getNombre());
        est.setLatitud(request.getLatitud());
        est.setLongitud(request.getLongitud());
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        est.setDiasDeteccionAnestro(request.getDiasDeteccionAnestro());
        est.setSeveridadAnestro(SeveridadAlerta.valueOf(request.getSeveridadAnestro()));

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
        est.setDiasDeteccionAnestro(request.getDiasDeteccionAnestro());
        est.setSeveridadAnestro(SeveridadAlerta.valueOf(request.getSeveridadAnestro()));
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
                .diasDeteccionAnestro(est.getDiasDeteccionAnestro())
                .severidadAnestro(est.getSeveridadAnestro())
                .build();
    }

    @Override
    public List<IntervencionResponse> obtenerIntervenciones(String emailUsuario, Long establecimientoId) {

        usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        List<Establecimiento> establecimientos = establecimientoRepository
                .findActiveEstablecimientoByUsuarioEmail(emailUsuario);

        boolean perteneceAlEstablecimiento = establecimientos.stream()
                .anyMatch(e -> e.getId().equals(establecimientoId));

        if (!perteneceAlEstablecimiento) {
            throw new AccessDeniedException(
                    "El usuario no pertenece al establecimiento indicado o no está activo.");
        }

        List<Intervencion> intervenciones = intervencionRepository.findByEstablecimientoId(establecimientoId);

        return intervenciones.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

    }

    private IntervencionResponse toResponse(Intervencion intervencion) {
        return IntervencionResponse.builder()
                .id(intervencion.getId())
                .animalId(intervencion.getAnimal().getId())
                .alertaId(intervencion.getAlerta().getId())
                .interventor(intervencion.getInterventor().getNombre())
                .descripcion(intervencion.getDescripcion())
                .fechaHora(intervencion.getFechaHora())
                .tipo(intervencion.getTipo())
                .build();
    }
}
