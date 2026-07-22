package dev.vigia.backend.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.AlertaService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AlertaServiceImpl implements AlertaService {

    private final AlertaRepository alertaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstablecimientoRepository establecimientoRepository;

    @Override
    public List<AlertaResponse> listarAlertasCelo(
            String emailUsuario, Long establecimientoId, EstadoAlerta estado) {

        // 1. Validar que el usuario existe
        usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        // 2. Verificar que el usuario realmente pertenece al establecimiento seleccionado.
        //    Esto garantiza el aislamiento multi-tenant: un usuario no puede consultar
        //    datos de un establecimiento ajeno aunque conozca su ID.
        List<Establecimiento> establecimientos =
                establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(emailUsuario);

        boolean perteneceAlEstablecimiento = establecimientos.stream()
                .anyMatch(e -> e.getId().equals(establecimientoId));

        if (!perteneceAlEstablecimiento) {
            throw new AccessDeniedException(
                    "El usuario no pertenece al establecimiento indicado o no está activo.");
        }

        // 3. Consultar alertas del establecimiento seleccionado según filtro
        List<Alerta> alertas = (estado == null)
                ? alertaRepository.findCeloByEstablecimiento(establecimientoId)
                : alertaRepository.findCeloByEstablecimientoAndEstado(establecimientoId, estado);

        // 4. Mapear a DTO
        return alertas.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Mapeo
    // -------------------------------------------------------------------------

    private AlertaResponse toResponse(Alerta alerta) {
        return AlertaResponse.builder()
                .id(alerta.getId())
                .tipo(alerta.getTipo())
                .animalId(alerta.getAnimal() != null ? alerta.getAnimal().getId() : null)
                .tsGeneracion(alerta.getTsGeneracion())
                .estado(alerta.getEstado())
                .build();
    }
}
