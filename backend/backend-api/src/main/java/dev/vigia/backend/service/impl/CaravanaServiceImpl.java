package dev.vigia.backend.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import dev.vigia.backend.dto.CaravanaRequest;
import dev.vigia.backend.dto.CaravanaResponse;
import dev.vigia.backend.exception.DevEuiDuplicadoException;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.CaravanaService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CaravanaServiceImpl implements CaravanaService {

    private final CaravanaRepository caravanaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstablecimientoRepository establecimientoRepository;

    @Override
    public CaravanaResponse crearCaravana(CaravanaRequest caravanaRequest) {
        Establecimiento establecimiento = establecimientoRepository.findById(caravanaRequest.getEstablecimientoId())
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

        caravanaRepository.findByDevEui(caravanaRequest.getDev_eui())
                .ifPresent(c -> {
                    throw new DevEuiDuplicadoException(
                            "Ya existe una caravana registrada con el dev_eui " + caravanaRequest.getDev_eui());
                });

        String codigo = caravanaRequest.getIdCaravanaOficial();
        if (codigo != null) {
            caravanaRepository.findByCodigo(codigo)
                    .ifPresent(c -> {
                        throw new IllegalArgumentException("Ya existe una caravana con este codigo");
                    });
        }

        Caravana caravana = new Caravana();
        caravana.setCodigo(codigo);
        caravana.setAlias(caravanaRequest.getAlias());
        caravana.setDevEui(caravanaRequest.getDev_eui());
        caravana.setEstablecimiento(establecimiento);
        caravana.setFechaAlta(LocalDate.now());
        caravana.setFechaAsignacion(codigo != null ? LocalDate.now() : null);
        caravana.setActivo(false);
        caravana.setEstadoSensor(EstadoSensor.DISPONIBLE);

        Caravana guardada = caravanaRepository.save(caravana);

        return toResponse(guardada);
    }

    @Override
    public List<CaravanaResponse> listarCaravanas(String email, Long establecimientoId) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        Establecimiento establecimiento = establecimientoRepository.findById(establecimientoId)
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

        validarAccesoEstablecimiento(usuario, establecimiento);

        return caravanaRepository.findAllByEstablecimientoId(establecimientoId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validarAccesoEstablecimiento(Usuario usuario, Establecimiento establecimiento) {
        boolean tieneAcceso = establecimiento.getEmpleados().stream()
                .anyMatch(e -> e.getUsuario().equals(usuario) && e.isActivo())
                || usuario.getRol().equals(Rol.ADMINISTRADOR);
        if (!tieneAcceso) {
            throw new AccessDeniedException("No tiene acceso a este establecimiento.");
        }
    }

    private CaravanaResponse toResponse(Caravana caravana) {
        Animal animal = caravana.getAnimal();
        return CaravanaResponse.builder()
                .id(caravana.getId())
                .codigo(caravana.getCodigo())
                .alias(caravana.getAlias())
                .fechaAsignacion(caravana.getFechaAsignacion())
                .fechaAlta(caravana.getFechaAlta())
                .devEui(caravana.getDevEui())
                .activo(caravana.isActivo())
                .estadoSensor(caravana.getEstadoSensor())
                .establecimientoId(caravana.getEstablecimiento() != null ? caravana.getEstablecimiento().getId() : null)
                .animalId(animal != null ? animal.getId() : null)
                .animalAlias(animal != null ? animal.getAlias() : null)
                .build();
    }
}
