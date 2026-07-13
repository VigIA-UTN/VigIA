package dev.vigia.backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.vigia.backend.dto.RodeoRequest;
import dev.vigia.backend.dto.RodeoResponse;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.service.RodeoService;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.RodeoRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RodeoServiceImpl implements RodeoService {

    private final RodeoRepository rodeoRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    public RodeoResponse generarRodeo(RodeoRequest request, String emailProductor) {

        Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

        Establecimiento establecimiento = establecimientoRepository.findById(request.getEstablecimientoId())
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

        boolean productorActivoEnEstablecimiento = establecimiento.getEmpleados().stream()
                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

        if (!productorActivoEnEstablecimiento) {
            throw new IllegalArgumentException("El productor no pertenece a este establecimiento o no está activo.");
        }

        RodeoLote rodeo = new RodeoLote(establecimiento);
        rodeo.setNombre(request.getNombre());
        RodeoLote savedRodeo = rodeoRepository.save(rodeo);
        return mapToResponse(savedRodeo);
    }

    private RodeoResponse mapToResponse(RodeoLote rodeo) {
        return RodeoResponse.builder()
                .id(rodeo.getId())
                .nombre(rodeo.getNombre())
                .establecimientoId(rodeo.getEstablecimiento().getId())
                .activo(rodeo.isActivo())
                .fechaAlta(rodeo.getFechaAlta())
                .build();
    }

    @Override
    public void desactivarRodeo(Long rodeoId, String emailProductor) {

        Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

        RodeoLote rodeo = rodeoRepository.findById(rodeoId)
                .orElseThrow(() -> new IllegalArgumentException("Rodeo no encontrado."));

        boolean productorActivoEnEstablecimiento = rodeo.getEstablecimiento().getEmpleados().stream()
                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

        if (!productorActivoEnEstablecimiento) {
            throw new IllegalArgumentException("El productor no pertenece a este establecimiento o no está activo.");
        }

        rodeo.setActivo(false);
        rodeoRepository.save(rodeo);
    }

    @Override
    public RodeoResponse getRodeoById(Long id, String emailProductor) {

        Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

        RodeoLote rodeo = rodeoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rodeo no encontrado."));

        boolean productorActivoEnEstablecimiento = rodeo.getEstablecimiento().getEmpleados().stream()
                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

        if (!productorActivoEnEstablecimiento) {
            throw new IllegalArgumentException("El productor no pertenece a este establecimiento o no está activo.");
        }

        return mapToResponse(rodeo);
    }

    @Override
    public List<RodeoResponse> getAllRodeos() {
        List<RodeoLote> rodeos = rodeoRepository.findAll();
        return rodeos.stream()
                .map(this::mapToResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<RodeoResponse> getRodeosByUsuario(String emailProductor) {
        Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        List<RodeoLote> rodeos = rodeoRepository.findByEstablecimiento_Empleados_Usuario(productorUser);
        return rodeos.stream()
                .map(this::mapToResponse)
                .collect(java.util.stream.Collectors.toList());
    }

}
