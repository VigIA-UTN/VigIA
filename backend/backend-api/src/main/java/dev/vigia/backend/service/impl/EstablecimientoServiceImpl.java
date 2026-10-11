package dev.vigia.backend.service.impl;

import dev.vigia.backend.service.EstablecimientoService;

import dev.vigia.backend.dto.ConfigAislamientoRequest;
import dev.vigia.backend.dto.ConfigAislamientoResponse;
import dev.vigia.backend.dto.ConfigAnestroRequest;
import dev.vigia.backend.dto.ConfigAnestroResponse;
import dev.vigia.backend.dto.EstablecimientoRequest;
import dev.vigia.backend.dto.EstablecimientoResponse;
import dev.vigia.backend.dto.IntervencionResponse;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Intervencion;
import dev.vigia.backend.model.Rol;
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

    private static final int DIAS_MIN = 1;
    private static final int DIAS_MAX = 365;

    private static final double UMBRAL_AISLAMIENTO_MIN = 10.0;
    private static final double UMBRAL_AISLAMIENTO_MAX = 1000.0;
    private static final double UMBRAL_AISLAMIENTO_DEFAULT = 100.0;

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
    public EstablecimientoResponse updateEstablecimiento(Long id, EstablecimientoRequest request,
            String emailUsuario, boolean esAdmin) {
        Establecimiento est = esAdmin
                ? establecimientoRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"))
                : findPerteneciente(id, emailUsuario);
        est.setNombre(request.getNombre());
        est.setLatitud(request.getLatitud());
        est.setLongitud(request.getLongitud());
        return mapToResponse(establecimientoRepository.save(est));
    }

    private Establecimiento findPerteneciente(Long id, String emailUsuario) {
        return establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(emailUsuario).stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException(
                        "El usuario no pertenece al establecimiento indicado o no está activo."));
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
                .alertaId(intervencion.getAlerta() != null ? intervencion.getAlerta().getId() : null)
                .interventor(intervencion.getInterventor().getNombre())
                .descripcion(intervencion.getDescripcion())
                .fechaHora(intervencion.getFechaHora())
                .tipo(intervencion.getTipo())
                .build();
    }

    // ── Configuración de anestro ──────────────────────────────────────────────

    /**
     * Consulta los parámetros de anestro del establecimiento.
     * Solo PRODUCTOR o VETERINARIO activos del establecimiento pueden consultar.
     */
    @SuppressWarnings("null")
    public ConfigAnestroResponse getConfigAnestro(Long establecimientoId, String emailUsuario) {
        Establecimiento est = establecimientoRepository.findById(establecimientoId)
                .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));

        verificarPertenencia(est, emailUsuario);

        return mapToConfigResponse(est);
    }

    /**
     * Actualiza los parámetros de anestro del establecimiento.
     * Solo PRODUCTOR o VETERINARIO activos del establecimiento pueden editar.
     * Valida: dias 1–365, severidad dentro de {BAJA, MEDIA, ALTA}.
     */
    @SuppressWarnings("null")
    public ConfigAnestroResponse updateConfigAnestro(Long establecimientoId,
                                                     ConfigAnestroRequest request,
                                                     String emailUsuario) {
        Establecimiento est = establecimientoRepository.findById(establecimientoId)
                .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));

        verificarPertenencia(est, emailUsuario);
        validarParametrosAnestro(request);

        est.setDiasDeteccionAnestro(request.getDiasDeteccionAnestro());
        est.setSeveridadAnestro(parseSeveridad(request.getSeveridadAnestro()));

        return mapToConfigResponse(establecimientoRepository.save(est));
    }

    // ── Configuración de aislamiento ──────────────────────────────────────────

    /**
     * Consulta el umbral de aislamiento en metros del establecimiento.
     * Solo PRODUCTOR o VETERINARIO activos del establecimiento pueden consultar.
     * Si no fue configurado previamente, devuelve el valor default (100.0 m).
     */
    @SuppressWarnings("null")
    public ConfigAislamientoResponse getConfigAislamiento(Long establecimientoId, String emailUsuario) {
        Establecimiento est = establecimientoRepository.findById(establecimientoId)
                .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));

        verificarPertenencia(est, emailUsuario);

        return mapToConfigAislamientoResponse(est);
    }

    /**
     * Actualiza el umbral de aislamiento en metros del establecimiento.
     * Solo PRODUCTOR o VETERINARIO activos del establecimiento pueden editar.
     * Valida que el valor se encuentre en el rango permitido (10.0 a 1000.0 m).
     */
    @SuppressWarnings("null")
    public ConfigAislamientoResponse updateConfigAislamiento(Long establecimientoId,
                                                             ConfigAislamientoRequest request,
                                                             String emailUsuario) {
        Establecimiento est = establecimientoRepository.findById(establecimientoId)
                .orElseThrow(() -> new RuntimeException("Establecimiento no encontrado"));

        verificarPertenencia(est, emailUsuario);
        validarParametrosAislamiento(request);

        est.setUmbralAislamientoM(request.getUmbralAislamientoM());

        return mapToConfigAislamientoResponse(establecimientoRepository.save(est));
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Verifica que el usuario autenticado sea PRODUCTOR o VETERINARIO activo
     * del establecimiento dado. Lanza AccessDeniedException si no cumple.
     */
    private void verificarPertenencia(Establecimiento est, String emailUsuario) {
        boolean esMiembro = est.getEmpleados().stream()
                .anyMatch(emp -> emp.isActivo()
                        && emp.getUsuario().getEmail().equals(emailUsuario)
                        && (emp.getUsuario().getRol() == Rol.PRODUCTOR
                            || emp.getUsuario().getRol() == Rol.VETERINARIO));

        if (!esMiembro) {
            throw new AccessDeniedException(
                    "Solo el Productor o el Veterinario del establecimiento pueden acceder a esta configuración.");
        }
    }

    /**
     * Valida que los parámetros de anestro estén dentro de los rangos permitidos.
     */
    private void validarParametrosAnestro(ConfigAnestroRequest request) {
        if (request.getDiasDeteccionAnestro() == null) {
            throw new IllegalArgumentException("El campo diasDeteccionAnestro es obligatorio.");
        }
        if (request.getDiasDeteccionAnestro() < DIAS_MIN || request.getDiasDeteccionAnestro() > DIAS_MAX) {
            throw new IllegalArgumentException(
                    String.format("diasDeteccionAnestro debe estar entre %d y %d.", DIAS_MIN, DIAS_MAX));
        }
        if (request.getSeveridadAnestro() == null || request.getSeveridadAnestro().isBlank()) {
            throw new IllegalArgumentException("El campo severidadAnestro es obligatorio.");
        }
        parseSeveridad(request.getSeveridadAnestro()); // valida que sea un enum conocido
    }

    /**
     * Convierte el String de severidad al enum correspondiente.
     * Lanza IllegalArgumentException con mensaje legible si es inválido.
     */
    private SeveridadAlerta parseSeveridad(String valor) {
        try {
            return SeveridadAlerta.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "La severidad '" + valor + "' no es válida. Los valores permitidos son: BAJA, MEDIA, ALTA.");
        }
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
                .umbralAislamientoM(est.getUmbralAislamientoM())
                .build();
    }

    private ConfigAnestroResponse mapToConfigResponse(Establecimiento est) {
        return ConfigAnestroResponse.builder()
                .establecimientoId(est.getId())
                .nombreEstablecimiento(est.getNombre())
                .diasDeteccionAnestro(est.getDiasDeteccionAnestro())
                .severidadAnestro(est.getSeveridadAnestro())
                .build();
    }

    private void validarParametrosAislamiento(ConfigAislamientoRequest request) {
        if (request.getUmbralAislamientoM() == null) {
            throw new IllegalArgumentException("El campo umbralAislamientoM es obligatorio.");
        }
        if (request.getUmbralAislamientoM() < UMBRAL_AISLAMIENTO_MIN || request.getUmbralAislamientoM() > UMBRAL_AISLAMIENTO_MAX) {
            throw new IllegalArgumentException(
                    String.format("umbralAislamientoM debe estar entre %.1f y %.1f metros.", UMBRAL_AISLAMIENTO_MIN, UMBRAL_AISLAMIENTO_MAX));
        }
    }

    private ConfigAislamientoResponse mapToConfigAislamientoResponse(Establecimiento est) {
        Double umbral = est.getUmbralAislamientoM() != null ? est.getUmbralAislamientoM() : UMBRAL_AISLAMIENTO_DEFAULT;
        return ConfigAislamientoResponse.builder()
                .establecimientoId(est.getId())
                .nombreEstablecimiento(est.getNombre())
                .umbralAislamientoM(umbral)
                .build();
    }
}
