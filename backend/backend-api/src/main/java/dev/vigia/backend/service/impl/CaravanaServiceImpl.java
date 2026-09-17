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
import dev.vigia.backend.repository.AnimalRepository;
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
    private final AnimalRepository animalRepository;

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
        return listarCaravanas(email, establecimientoId, null, null);
    }

    @Override
    public List<CaravanaResponse> listarCaravanas(String email, Long establecimientoId, EstadoSensor estado,
            String codigo) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        List<Caravana> caravanas;
        if (establecimientoId == null) {
            if (!usuario.getRol().equals(Rol.ADMINISTRADOR)) {
                throw new AccessDeniedException("Sólo un ADMINISTRADOR puede listar caravanas de todos los establecimientos.");
            }
            caravanas = listarCaravanasDeTodosLosEstablecimientos(estado, codigo);
        } else {
            Establecimiento establecimiento = establecimientoRepository.findById(establecimientoId)
                    .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

            validarAccesoEstablecimiento(usuario, establecimiento);

            if (estado != null && codigo != null) {
                caravanas = caravanaRepository.findAllByEstablecimientoIdAndEstadoSensorAndCodigoContainingIgnoreCase(
                        establecimientoId, estado, codigo);
            } else if (estado != null) {
                caravanas = caravanaRepository.findAllByEstablecimientoIdAndEstadoSensor(establecimientoId, estado);
            } else if (codigo != null) {
                caravanas = caravanaRepository.findAllByEstablecimientoIdAndCodigoContainingIgnoreCase(
                        establecimientoId, codigo);
            } else {
                caravanas = caravanaRepository.findAllByEstablecimientoId(establecimientoId);
            }
        }

        return caravanas.stream()
                .map(this::toResponse)
                .toList();
    }

    private List<Caravana> listarCaravanasDeTodosLosEstablecimientos(EstadoSensor estado, String codigo) {
        if (estado != null && codigo != null) {
            return caravanaRepository.findAllByEstadoSensorAndCodigoContainingIgnoreCase(estado, codigo);
        } else if (estado != null) {
            return caravanaRepository.findAllByEstadoSensor(estado);
        } else if (codigo != null) {
            return caravanaRepository.findAllByCodigoContainingIgnoreCase(codigo);
        }
        return caravanaRepository.findAll();
    }

    @Override
    public List<CaravanaResponse> listarCaravanasDisponibles(Long establecimientoId, String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        Establecimiento establecimiento = establecimientoRepository.findById(establecimientoId)
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

        if (usuario.getRol().equals(Rol.VETERINARIO)) {
            throw new AccessDeniedException("El rol VETERINARIO no tiene acceso a las caravanas.");
        }

        validarAccesoEstablecimiento(usuario, establecimiento);

        return caravanaRepository.findAllByEstablecimientoIdAndEstadoSensor(establecimientoId, EstadoSensor.DISPONIBLE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CaravanaResponse darDeBajaCaravana(Long caravanaId, String motivo, String email) {
        Caravana caravana = cargarCaravanaConAcceso(caravanaId, email);

        if (caravana.getEstadoSensor() == EstadoSensor.INACTIVO) {
            throw new IllegalArgumentException("La caravana ya se encuentra dada de baja.");
        }

        if (caravana.getEstadoSensor() == EstadoSensor.VINCULADO) {
            Animal animal = caravana.getAnimal();
            if (animal != null) {
                animal.setCaravana(null);
                animalRepository.save(animal);
                caravana.setAnimal(null);
            }
        }

        caravana.setEstadoSensor(EstadoSensor.INACTIVO);
        caravana.setActivo(false);

        Caravana guardada = caravanaRepository.save(caravana);
        return toResponse(guardada);
    }

    @Override
    public CaravanaResponse reactivarCaravana(Long caravanaId, String email) {
        Caravana caravana = cargarCaravanaConAcceso(caravanaId, email);

        if (caravana.getEstadoSensor() != EstadoSensor.INACTIVO) {
            throw new IllegalArgumentException("Solo se puede reactivar una caravana que esté dada de baja.");
        }

        caravana.setEstadoSensor(EstadoSensor.DISPONIBLE);
        caravana.setActivo(false);

        Caravana guardada = caravanaRepository.save(caravana);
        return toResponse(guardada);
    }

    private Caravana cargarCaravanaConAcceso(Long caravanaId, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        Caravana caravana = caravanaRepository.findById(caravanaId)
                .orElseThrow(() -> new IllegalArgumentException("Caravana no encontrada."));

        validarAccesoEstablecimiento(usuario, caravana.getEstablecimiento());

        return caravana;
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
                .establecimientoNombre(caravana.getEstablecimiento() != null ? caravana.getEstablecimiento().getNombre() : null)
                .animalId(animal != null ? animal.getId() : null)
                .animalAlias(animal != null ? animal.getAlias() : null)
                .bateria(caravana.getBateria())
                .ultimaSenial(caravana.getUltimaSenial())
                .build();
    }
}
