package dev.vigia.backend.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import dev.vigia.backend.dto.CaravanaRequest;
import dev.vigia.backend.dto.CaravanaResponse;
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
    @SuppressWarnings("null")
    public CaravanaResponse crearCaravana(CaravanaRequest caravanaRequest, String email) {

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        Establecimiento establecimiento = establecimientoRepository.findById(caravanaRequest.getEstablecimientoId())
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado"));

        validarAccesoEstablecimiento(usuario, establecimiento);

        caravanaRepository.findByCodigo(caravanaRequest.getIdCaravanaOficial())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("Ya existe una caravana con este codigo");
                });

        caravanaRepository.findByDevEui(caravanaRequest.getDev_eui())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("Ya existe una caravana con este devEUI");
                });

        Caravana caravana = new Caravana();
        caravana.setCodigo(caravanaRequest.getIdCaravanaOficial());
        caravana.setFechaAsignacion(LocalDate.now());
        caravana.setFechaAlta(LocalDate.now());
        caravana.setDevEui(caravanaRequest.getDev_eui());

        caravana.setActivo(false);
        caravana.setEstadoSensor(EstadoSensor.DISPONIBLE);

        Caravana newCaravana = caravanaRepository.save(caravana);

        return CaravanaResponse.builder()
                .id(newCaravana.getId())
                .codigo(newCaravana.getCodigo())
                .fechaAsignacion(newCaravana.getFechaAsignacion())
                .fechaAlta(newCaravana.getFechaAlta())
                .devEui(newCaravana.getDevEui())
                .activo(newCaravana.isActivo())
                .estadoSensor(newCaravana.getEstadoSensor())
                .build();
    }

    private void validarAccesoEstablecimiento(Usuario usuario, Establecimiento establecimiento) {
        boolean tieneAcceso = establecimiento.getEmpleados().stream()
                .anyMatch(e -> e.getUsuario().equals(usuario) && e.isActivo())
                || usuario.getRol().equals(Rol.ADMINISTRADOR);
        if (!tieneAcceso) {
            throw new AccessDeniedException("No tiene acceso a este establecimiento.");
        }
    }

    @Override
    public List<CaravanaResponse> obtenerSensores(String email, Long establecimientoId) {

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        Establecimiento establecimiento = establecimientoRepository.findById(establecimientoId)
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado"));

        validarAccesoEstablecimiento(usuario, establecimiento);

        return caravanaRepository.findAllByAnimalEstablecimientoId(establecimientoId)
                .stream().map(c -> CaravanaResponse.builder()
                        .id(c.getId())
                        .codigo(c.getCodigo())
                        .fechaAsignacion(c.getFechaAsignacion())
                        .fechaAlta(c.getFechaAlta())
                        .devEui(c.getDevEui())
                        .activo(c.isActivo())
                        .estadoSensor(c.getEstadoSensor())
                        .build())
                .toList();
    }
}
