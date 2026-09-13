package dev.vigia.backend.service.impl;

import java.time.LocalDate;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import dev.vigia.backend.dto.CaravanaRequest;
import dev.vigia.backend.dto.CaravanaResponse;
import dev.vigia.backend.model.Caravana;
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
    public CaravanaResponse crearCaravana(CaravanaRequest caravanaRequest, String email) {

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!usuario.getRol().equals(Rol.ADMINISTRADOR)) {
            throw new AccessDeniedException("No tiene permiso para crear caravanas");
        }

        establecimientoRepository.findById(caravanaRequest.getEstablecimientoId())
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado"));

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
}
