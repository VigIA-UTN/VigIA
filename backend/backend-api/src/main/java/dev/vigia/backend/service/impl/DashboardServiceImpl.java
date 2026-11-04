package dev.vigia.backend.service.impl;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.CentroMandoResponse;
import dev.vigia.backend.dto.ResumenEjecutivoResponse;
import dev.vigia.backend.dto.SaludReproduccionResponse;
import dev.vigia.backend.dto.UmbralesDashboardResponse;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.TipoEstadoSalud;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoSaludRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.service.AlertaService;
import dev.vigia.backend.service.DashboardService;
import dev.vigia.backend.service.VentanaInseminacionCalculadora;
import dev.vigia.backend.service.dashboard.CentroMandoCalculadora;
import dev.vigia.backend.service.dashboard.DashboardDatos;
import dev.vigia.backend.service.dashboard.DashboardDatos.AlertaGenerada;
import dev.vigia.backend.service.dashboard.DashboardDatos.AnimalDashboard;
import dev.vigia.backend.service.dashboard.DashboardDatos.IntervencionDashboard;
import dev.vigia.backend.service.dashboard.ResumenEjecutivoCalculadora;
import dev.vigia.backend.service.dashboard.ResumenEjecutivoCalculadora.RodeoDashboard;
import dev.vigia.backend.service.dashboard.SaludReproduccionCalculadora;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Carga en lote lo que necesita cada pestaña del Dashboard (una consulta por tipo de dato, no por animal) y delega los
 * cálculos en las calculadoras de {@code service.dashboard}.
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    /** Los períodos que ofrece el selector; se acepta cualquiera hasta un año. */
    static final int PERIODO_MAX_DIAS = 365;

    private final AnimalRepository animalRepository;
    private final EstadoSaludRepository estadoSaludRepository;
    private final AlertaRepository alertaRepository;
    private final IntervencionRepository intervencionRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final RodeoLoteRepository rodeoLoteRepository;
    private final AlertaService alertaService;
    private final UmbralesDashboardResponse umbrales;

    public DashboardServiceImpl(AnimalRepository animalRepository, EstadoSaludRepository estadoSaludRepository,
            AlertaRepository alertaRepository, IntervencionRepository intervencionRepository,
            EstablecimientoRepository establecimientoRepository, RodeoLoteRepository rodeoLoteRepository,
            AlertaService alertaService,
            @Value("${vigia.dashboard.horas-sin-senial:6}") int horasSinSenial,
            @Value("${vigia.dashboard.bateria-baja-porcentaje:20}") int bateriaBajaPorcentaje,
            @Value("${vigia.dashboard.horas-ventana-por-vencer:3}") int horasVentanaPorVencer,
            @Value("${vigia.dashboard.dias-diagnostico-inseminada:35}") int diasDiagnosticoInseminada,
            @Value("${vigia.dashboard.cola-max:8}") int colaMax,
            @Value("${vigia.dashboard.horas-salud-sin-atender:24}") int horasSaludSinAtender,
            @Value("${vigia.dashboard.dias-vacia-larga:90}") int diasVaciaLarga,
            @Value("${vigia.dashboard.dias-gestacion:283}") int diasGestacion) {
        this.animalRepository = animalRepository;
        this.estadoSaludRepository = estadoSaludRepository;
        this.alertaRepository = alertaRepository;
        this.intervencionRepository = intervencionRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.rodeoLoteRepository = rodeoLoteRepository;
        this.alertaService = alertaService;
        this.umbrales = UmbralesDashboardResponse.builder()
                .horasSinSenial(horasSinSenial)
                .bateriaBajaPorcentaje(bateriaBajaPorcentaje)
                .horasVentanaPorVencer(horasVentanaPorVencer)
                .diasDiagnosticoInseminada(diasDiagnosticoInseminada)
                .colaMax(colaMax)
                .horasSaludSinAtender(horasSaludSinAtender)
                .diasVaciaLarga(diasVaciaLarga)
                .diasGestacion(diasGestacion)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CentroMandoResponse centroMando(String emailUsuario, Long establecimientoId, Long rodeoId) {
        verificarAcceso(emailUsuario, establecimientoId, rodeoId);
        LocalDateTime ahora = LocalDateTime.now();

        List<AnimalDashboard> animales = animales(establecimientoId, rodeoId);
        Set<Long> ids = ids(animales);
        List<AlertaResponse> pendientes = pendientes(emailUsuario, establecimientoId, rodeoId, ids);
        List<AlertaGenerada> generadas = generadas(establecimientoId, CentroMandoCalculadora.inicioTendencia(ahora),
                CentroMandoCalculadora.TIPOS_TENDENCIA, rodeoId, ids);

        return CentroMandoCalculadora.calcular(animales, pendientes, ventanas(pendientes, ahora), generadas,
                umbrales, ahora);
    }

    @Override
    @Transactional(readOnly = true)
    public SaludReproduccionResponse saludReproduccion(String emailUsuario, Long establecimientoId, Long rodeoId,
            int periodoDias) {
        validarPeriodo(periodoDias);
        verificarAcceso(emailUsuario, establecimientoId, rodeoId);
        LocalDateTime ahora = LocalDateTime.now();

        List<AnimalDashboard> animales = animales(establecimientoId, rodeoId);
        Set<Long> ids = ids(animales);
        List<AlertaResponse> todasPendientes = pendientes(emailUsuario, establecimientoId, null, ids);
        List<AlertaResponse> pendientes = todasPendientes.stream()
                .filter(a -> rodeoId == null || ids.contains(a.getAnimalId()))
                .toList();
        // Anestro sin intervención: la alerta de anestro sigue pendiente (intervenir la cierra).
        Set<Long> anestros = todasPendientes.stream()
                .filter(a -> DisparadorAlerta.CELO.name().equals(a.getTipo()) && !DashboardDatos.esCeloConVentana(a))
                .map(AlertaResponse::getAnimalId)
                .collect(Collectors.toSet());
        List<AlertaGenerada> generadas = generadas(establecimientoId, ahora.minusDays(periodoDias),
                EnumSet.allOf(DisparadorAlerta.class), rodeoId, ids);

        return SaludReproduccionCalculadora.calcular(animales, pendientes, anestros, ventanas(pendientes, ahora),
                generadas, ultimasIntervenciones(ids), periodoDias, umbrales, ahora);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenEjecutivoResponse resumenEjecutivo(String emailUsuario, Long establecimientoId, int periodoDias) {
        validarPeriodo(periodoDias);
        verificarAcceso(emailUsuario, establecimientoId, null);
        LocalDateTime ahora = LocalDateTime.now();

        List<AnimalDashboard> animales = animales(establecimientoId, null);
        List<RodeoDashboard> rodeos = rodeoLoteRepository.findByEstablecimientoId(establecimientoId).stream()
                .filter(RodeoLote::isActivo)
                .sorted(Comparator.comparing(RodeoLote::getId))
                .map(r -> new RodeoDashboard(r.getId(), r.getNombre()))
                .toList();
        List<AlertaResponse> pendientes = pendientes(emailUsuario, establecimientoId, null, ids(animales));
        // Lo que cubra más: el período o las 8 semanas de la tendencia.
        LocalDateTime inicioPeriodo = ahora.minusDays(periodoDias);
        LocalDateTime inicioTendencia = CentroMandoCalculadora.inicioTendencia(ahora);
        List<AlertaGenerada> generadas = generadas(establecimientoId,
                inicioPeriodo.isBefore(inicioTendencia) ? inicioPeriodo : inicioTendencia,
                EnumSet.allOf(DisparadorAlerta.class), null, Set.of());

        return ResumenEjecutivoCalculadora.calcular(animales, rodeos, pendientes, generadas, periodoDias, umbrales,
                ahora);
    }

    // ── Acceso ───────────────────────────────────────────────────────────────

    private static void validarPeriodo(int periodoDias) {
        if (periodoDias < 1 || periodoDias > PERIODO_MAX_DIAS) {
            throw new IllegalArgumentException("El período debe ser de 1 a " + PERIODO_MAX_DIAS + " días.");
        }
    }

    private void verificarAcceso(String emailUsuario, Long establecimientoId, Long rodeoId) {
        boolean pertenece = establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(emailUsuario).stream()
                .anyMatch(e -> e.getId().equals(establecimientoId));
        if (!pertenece) {
            throw new AccessDeniedException("El usuario no pertenece al establecimiento indicado o no está activo.");
        }
        if (rodeoId != null) {
            RodeoLote rodeo = rodeoLoteRepository.findById(rodeoId)
                    .orElseThrow(() -> new IllegalArgumentException("Rodeo no encontrado."));
            if (rodeo.getEstablecimiento() == null || !establecimientoId.equals(rodeo.getEstablecimiento().getId())) {
                throw new AccessDeniedException("El rodeo no pertenece al establecimiento indicado.");
            }
        }
    }

    // ── Carga en lote ────────────────────────────────────────────────────────

    /** Los animales activos (del rodeo, si se pidió) con su estado actual, en dos consultas. */
    private List<AnimalDashboard> animales(Long establecimientoId, Long rodeoId) {
        List<Animal> animales = animalRepository
                .findParaDashboardByEstablecimientoIdAndEstadoActualNot(establecimientoId, EstadoAnimal.INACTIVO)
                .stream()
                .filter(a -> rodeoId == null || (a.getRodeoLote() != null && rodeoId.equals(a.getRodeoLote().getId())))
                .toList();

        Map<Long, TipoEstadoSalud> salud = new HashMap<>();
        if (!animales.isEmpty()) {
            estadoSaludRepository.findUltimoPorAnimal(animales.stream().map(Animal::getId).toList())
                    .forEach(e -> salud.put(e.getAnimalId(), TipoEstadoSalud.valueOf(e.getEstado())));
        }

        return animales.stream().map(a -> {
            // Mismo criterio que la ficha del animal: el último estado registrado, y calibrando mientras no tenga
            // perfil de salud.
            EstadoReproductivo reproductivo = a.getEstadosReproductivos().stream().findFirst().orElse(null);
            boolean tieneCaravana = a.getCaravana() != null;
            RodeoLote rodeo = a.getRodeoLote();
            return new AnimalDashboard(a.getId(), a.getIdCaravanaOficial(), a.getAlias(),
                    rodeo != null ? rodeo.getId() : null, rodeo != null ? rodeo.getNombre() : null,
                    a.getFechaNacimiento(), a.getFechaAlta(), tieneCaravana,
                    tieneCaravana && a.getPerfilReferencia() == null,
                    tieneCaravana ? a.getCaravana().getUltimaSenial() : null,
                    tieneCaravana ? a.getCaravana().getBateria() : null,
                    reproductivo != null ? reproductivo.getEstado() : null,
                    reproductivo != null ? reproductivo.getFechaUltParto() : null,
                    reproductivo != null ? reproductivo.getFechaUltInseminacion() : null,
                    salud.get(a.getId()));
        }).toList();
    }

    private static Set<Long> ids(List<AnimalDashboard> animales) {
        return animales.stream().map(AnimalDashboard::id).collect(Collectors.toSet());
    }

    /** Las mismas que GET /alertas?estado=PENDIENTE; con rodeo, solo las de sus animales. */
    private List<AlertaResponse> pendientes(String emailUsuario, Long establecimientoId, Long rodeoId,
            Set<Long> idsAnimales) {
        return alertaService.listarAlertas(emailUsuario, establecimientoId, null, null, EstadoAlerta.PENDIENTE)
                .stream()
                .filter(a -> rodeoId == null || idsAnimales.contains(a.getAnimalId()))
                .toList();
    }

    private List<AlertaGenerada> generadas(Long establecimientoId, LocalDateTime desde,
            Collection<DisparadorAlerta> disparadores, Long rodeoId, Set<Long> idsAnimales) {
        return alertaRepository.findGeneradasDesde(establecimientoId, desde, disparadores).stream()
                .map(fila -> new AlertaGenerada((Long) fila[0], (DisparadorAlerta) fila[1], (String) fila[2],
                        (LocalDateTime) fila[3], (EstadoAlerta) fila[4], (LocalDateTime) fila[5]))
                .filter(g -> rodeoId == null || idsAnimales.contains(g.animalId()))
                .toList();
    }

    /** Ventana de cada celo pendiente que la tiene, en una consulta. */
    private Map<Long, VentanaInseminacionResponse> ventanas(List<AlertaResponse> pendientes, LocalDateTime ahora) {
        List<Long> celos = pendientes.stream()
                .filter(DashboardDatos::esCeloConVentana)
                .map(AlertaResponse::getId)
                .toList();
        Map<Long, VentanaInseminacionResponse> ventanas = new HashMap<>();
        if (!celos.isEmpty()) {
            for (Alerta alerta : alertaRepository.findAllById(celos)) {
                ventanas.put(alerta.getId(), VentanaInseminacionCalculadora.calcular(alerta, ahora));
            }
        }
        return ventanas;
    }

    /** La última intervención de cada animal, en una consulta. */
    private List<IntervencionDashboard> ultimasIntervenciones(Set<Long> idsAnimales) {
        if (idsAnimales.isEmpty()) {
            return List.of();
        }
        return intervencionRepository.findUltimaPorAnimal(idsAnimales).stream()
                .map(i -> new IntervencionDashboard(i.getAnimal().getId(), i.getTipo(), i.getFechaHora(),
                        i.getInterventor() != null ? i.getInterventor().getNombre() : null))
                .toList();
    }
}
