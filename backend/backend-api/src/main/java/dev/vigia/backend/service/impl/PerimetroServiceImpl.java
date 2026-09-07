package dev.vigia.backend.service.impl;

import dev.vigia.backend.dto.PerimetroRequest;
import dev.vigia.backend.dto.PerimetroResponse;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Perimetro;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.PerimetroRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.PerimetroService;
import dev.vigia.backend.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PerimetroServiceImpl implements PerimetroService {

    private final PerimetroRepository perimetroRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final RodeoLoteRepository rodeoLoteRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    public PerimetroResponse crearPerimetro(PerimetroRequest request, String emailUsuario) {
        log.info("Creando perímetro '{}' para establecimiento {} solicitado por {}",
                request.getNombre(), request.getEstablecimientoId(), emailUsuario);

        Usuario usuario = buscarUsuario(emailUsuario);
        Establecimiento establecimiento = buscarEstablecimiento(request.getEstablecimientoId());
        validarAccesoEstablecimiento(usuario, establecimiento);

        // 1. Construir y validar polígono con JTS (SRID 4326, lon/lat, sin autointersecciones)
        Polygon polygon = GeometryUtils.crearPoligono(request.getCoordenadas());

        // 2. Instanciar entidad Perimetro encapsulada
        Perimetro perimetro = new Perimetro(establecimiento, request.getNombre(), request.getDescripcion(), polygon);

        // 3. Asociar lotes asegurando que pertenezcan al mismo establecimiento
        if (request.getLoteIds() != null && !request.getLoteIds().isEmpty()) {
            List<RodeoLote> lotes = rodeoLoteRepository.findAllById(request.getLoteIds());
            for (RodeoLote lote : lotes) {
                if (!lote.getEstablecimiento().getId().equals(establecimiento.getId())) {
                    throw new IllegalArgumentException("El lote " + lote.getId() + " no pertenece al establecimiento indicado.");
                }
                perimetro.asignarLote(lote);
            }
        }

        Perimetro saved = perimetroRepository.save(perimetro);
        log.info("Perímetro creado exitosamente con ID {}", saved.getId());
        return mapToResponse(saved);
    }

    @Override
    public PerimetroResponse actualizarPerimetro(Long perimetroId, PerimetroRequest request, String emailUsuario) {
        log.info("Actualizando perímetro {} solicitado por {}", perimetroId, emailUsuario);

        Usuario usuario = buscarUsuario(emailUsuario);
        Perimetro perimetro = perimetroRepository.findById(perimetroId)
                .orElseThrow(() -> new IllegalArgumentException("Perímetro no encontrado con ID: " + perimetroId));

        validarAccesoEstablecimiento(usuario, perimetro.getEstablecimiento());

        // 1. Reconstruir polígono inmutable y validar topología
        Polygon nuevoPolygon = GeometryUtils.crearPoligono(request.getCoordenadas());
        perimetro.actualizarArea(nuevoPolygon);

        // 2. Actualizar datos descriptivos
        perimetro.actualizarDatos(request.getNombre(), request.getDescripcion());

        // 3. Sincronizar lotes
        if (request.getLoteIds() != null) {
            List<RodeoLote> lotes = rodeoLoteRepository.findAllById(request.getLoteIds());
            for (RodeoLote lote : lotes) {
                if (!lote.getEstablecimiento().getId().equals(perimetro.getEstablecimiento().getId())) {
                    throw new IllegalArgumentException("El lote " + lote.getId() + " no pertenece al establecimiento del perímetro.");
                }
            }
            perimetro.sincronizarLotes(lotes);
        }

        Perimetro updated = perimetroRepository.save(perimetro);
        return mapToResponse(updated);
    }

    @Override
    public void activarPerimetro(Long perimetroId, String emailUsuario) {
        Usuario usuario = buscarUsuario(emailUsuario);
        Perimetro perimetro = perimetroRepository.findById(perimetroId)
                .orElseThrow(() -> new IllegalArgumentException("Perímetro no encontrado con ID: " + perimetroId));

        validarAccesoEstablecimiento(usuario, perimetro.getEstablecimiento());
        perimetro.activar();
        perimetroRepository.save(perimetro);
        log.info("Perímetro {} activado por {}", perimetroId, emailUsuario);
    }

    @Override
    public void desactivarPerimetro(Long perimetroId, String emailUsuario) {
        Usuario usuario = buscarUsuario(emailUsuario);
        Perimetro perimetro = perimetroRepository.findById(perimetroId)
                .orElseThrow(() -> new IllegalArgumentException("Perímetro no encontrado con ID: " + perimetroId));

        validarAccesoEstablecimiento(usuario, perimetro.getEstablecimiento());
        perimetro.desactivar();
        perimetroRepository.save(perimetro);
        log.info("Perímetro {} desactivado por {}", perimetroId, emailUsuario);
    }

    @Override
    @Transactional(readOnly = true)
    public PerimetroResponse getPerimetroById(Long perimetroId, String emailUsuario) {
        Usuario usuario = buscarUsuario(emailUsuario);
        Perimetro perimetro = perimetroRepository.findById(perimetroId)
                .orElseThrow(() -> new IllegalArgumentException("Perímetro no encontrado con ID: " + perimetroId));

        validarAccesoEstablecimiento(usuario, perimetro.getEstablecimiento());
        return mapToResponse(perimetro);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PerimetroResponse> getPerimetrosByEstablecimiento(Long establecimientoId, String emailUsuario) {
        Usuario usuario = buscarUsuario(emailUsuario);
        Establecimiento establecimiento = buscarEstablecimiento(establecimientoId);
        validarAccesoEstablecimiento(usuario, establecimiento);

        List<Perimetro> perimetros = perimetroRepository.findByEstablecimientoId(establecimientoId);
        return perimetros.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con email: " + email));
    }

    private Establecimiento buscarEstablecimiento(Long establecimientoId) {
        return establecimientoRepository.findById(establecimientoId)
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado con ID: " + establecimientoId));
    }

    private void validarAccesoEstablecimiento(Usuario usuario, Establecimiento establecimiento) {
        boolean tieneAcceso = establecimiento.getEmpleados().stream()
                .anyMatch(e -> e.getUsuario().equals(usuario) && e.isActivo());

        if (!tieneAcceso) {
            throw new AccessDeniedException("El usuario no pertenece al establecimiento indicado o no está activo.");
        }
    }

    private PerimetroResponse mapToResponse(Perimetro perimetro) {
        List<Long> loteIds = perimetro.getLotes() != null
                ? perimetro.getLotes().stream().map(RodeoLote::getId).collect(Collectors.toList())
                : new ArrayList<>();

        return PerimetroResponse.builder()
                .id(perimetro.getId())
                .establecimientoId(perimetro.getEstablecimiento().getId())
                .establecimientoNombre(perimetro.getEstablecimiento().getNombre())
                .nombre(perimetro.getNombre())
                .descripcion(perimetro.getDescripcion())
                .activo(perimetro.isActivo())
                .loteIds(loteIds)
                .coordenadas(GeometryUtils.extraerCoordenadas(perimetro.getArea()))
                .fechaCreacion(perimetro.getFechaCreacion())
                .fechaModificacion(perimetro.getFechaModificacion())
                .version(perimetro.getVersion())
                .build();
    }
}
