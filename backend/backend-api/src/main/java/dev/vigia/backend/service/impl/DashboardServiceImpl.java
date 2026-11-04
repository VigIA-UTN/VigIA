package dev.vigia.backend.service.impl;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.CentroMandoResponse;
import dev.vigia.backend.dto.CentroMandoResponse.Umbrales;
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
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.service.AlertaService;
import dev.vigia.backend.service.DashboardService;
import dev.vigia.backend.service.VentanaInseminacionCalculadora;
import dev.vigia.backend.service.dashboard.CentroMandoCalculadora;
import dev.vigia.backend.service.dashboard.CentroMandoCalculadora.AlertaGenerada;
import dev.vigia.backend.service.dashboard.CentroMandoCalculadora.AnimalDashboard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    private final AnimalRepository animalRepository;
    private final EstadoSaludRepository estadoSaludRepository;
    private final AlertaRepository alertaRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final RodeoLoteRepository rodeoLoteRepository;
    private final AlertaService alertaService;
    private final Umbrales umbrales;

    public DashboardServiceImpl(AnimalRepository animalRepository, EstadoSaludRepository estadoSaludRepository,
            AlertaRepository alertaRepository, EstablecimientoRepository establecimientoRepository,
            RodeoLoteRepository rodeoLoteRepository, AlertaService alertaService,
            @Value("${vigia.dashboard.horas-sin-senial:6}") int horasSinSenial,
            @Value("${vigia.dashboard.bateria-baja-porcentaje:20}") int bateriaBajaPorcentaje,
            @Value("${vigia.dashboard.horas-ventana-por-vencer:3}") int horasVentanaPorVencer,
            @Value("${vigia.dashboard.dias-diagnostico-inseminada:35}") int diasDiagnosticoInseminada,
            @Value("${vigia.dashboard.cola-max:8}") int colaMax) {
        this.animalRepository = animalRepository;
        this.estadoSaludRepository = estadoSaludRepository;
        this.alertaRepository = alertaRepository;
        this.establecimientoRepository = establecimientoRepository;
        this.rodeoLoteRepository = rodeoLoteRepository;
        this.alertaService = alertaService;
        this.umbrales = Umbrales.builder()
                .horasSinSenial(horasSinSenial)
                .bateriaBajaPorcentaje(bateriaBajaPorcentaje)
                .horasVentanaPorVencer(horasVentanaPorVencer)
                .diasDiagnosticoInseminada(diasDiagnosticoInseminada)
                .colaMax(colaMax)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CentroMandoResponse centroMando(String emailUsuario, Long establecimientoId, Long rodeoId) {
        verificarAcceso(emailUsuario, establecimientoId, rodeoId);
        LocalDateTime ahora = LocalDateTime.now();

        List<AnimalDashboard> animales = animales(establecimientoId, rodeoId);
        Set<Long> idsAnimales = animales.stream().map(AnimalDashboard::id).collect(Collectors.toSet());

        // Las mismas que GET /alertas?estado=PENDIENTE; con rodeo, solo las de sus animales.
        List<AlertaResponse> pendientes = alertaService
                .listarAlertas(emailUsuario, establecimientoId, null, null, EstadoAlerta.PENDIENTE).stream()
                .filter(a -> rodeoId == null || idsAnimales.contains(a.getAnimalId()))
                .toList();

        List<AlertaGenerada> generadas = alertaRepository
                .findGeneradasDesde(establecimientoId, CentroMandoCalculadora.inicioTendencia(ahora),
                        CentroMandoCalculadora.TIPOS_TENDENCIA)
                .stream()
                .map(fila -> new AlertaGenerada((Long) fila[0], (DisparadorAlerta) fila[1], (String) fila[2],
                        (LocalDateTime) fila[3], (EstadoAlerta) fila[4]))
                .filter(g -> rodeoId == null || idsAnimales.contains(g.animalId()))
                .toList();

        return CentroMandoCalculadora.calcular(animales, pendientes, ventanas(pendientes, ahora), generadas,
                umbrales, ahora);
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
            return new AnimalDashboard(a.getId(), a.getIdCaravanaOficial(), a.getAlias(),
                    a.getRodeoLote() != null ? a.getRodeoLote().getId() : null,
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

    /** Ventana de cada celo pendiente que la tiene, en una consulta. */
    private Map<Long, VentanaInseminacionResponse> ventanas(List<AlertaResponse> pendientes, LocalDateTime ahora) {
        List<Long> celos = pendientes.stream()
                .filter(CentroMandoCalculadora::esCeloConVentana)
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
}
