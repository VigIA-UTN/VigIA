package dev.vigia.backend.service.impl;

import java.util.Comparator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.vigia.backend.dto.AlertaDetalleResponse;
import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.AlertaResumenResponse;
import dev.vigia.backend.dto.DetalleSanitarioResponse;
import dev.vigia.backend.dto.DetalleGeoespacialResponse;
import dev.vigia.backend.dto.DiagnosticoRequest;
import dev.vigia.backend.dto.DiagnosticoResponse;
import dev.vigia.backend.dto.EstadoReproductivoResponse;
import dev.vigia.backend.dto.IntervencionRequest;
import dev.vigia.backend.dto.LineaTiempoEventoResponse;
import dev.vigia.backend.dto.SerieComparativaSnapshot;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.exception.AlertaYaProcesadaException;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Diagnostico;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.DiagnosticoRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.EstadoSaludRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.EstadoSalud;
import dev.vigia.backend.model.Intervencion;
import dev.vigia.backend.model.OrigenEstado;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.TipoIntervencion;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.service.AlertaService;
import dev.vigia.backend.service.CeloDatoDesactualizadoEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertaServiceImpl implements AlertaService {

        private final AlertaRepository alertaRepository;
        private final AnimalRepository animalRepository;
        private final UsuarioRepository usuarioRepository;
        private final EstablecimientoRepository establecimientoRepository;
        private final IntervencionRepository intervencionRepository;
        private final EstadoReproductivoRepository estadoReproductivoRepository;
        private final EstadoSaludRepository estadoSaludRepository;
        private final DiagnosticoRepository diagnosticoRepository;
        private final RabbitTemplate rabbitTemplate;
        private final ObjectMapper objectMapper;
        private final CeloDatoDesactualizadoEvaluator datoDesactualizadoEvaluator;

        @Override
        public List<AlertaResponse> listarAlertas(String emailUsuario, Long establecimientoId, String tipo,
                        String severidad, EstadoAlerta estado) {
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

                DisparadorAlerta disparadorEnum = resolverDisparador(tipo);

                SeveridadAlerta severidadEnum = null;
                if (severidad != null && !severidad.isBlank()) {
                        String s = severidad.trim().toUpperCase();
                        if (s.equals("ALTA"))
                                severidadEnum = SeveridadAlerta.ALTA;
                        else if (s.equals("MEDIA"))
                                severidadEnum = SeveridadAlerta.MEDIA;
                        else if (s.equals("BAJA"))
                                severidadEnum = SeveridadAlerta.BAJA;
                }

                List<Alerta> alertas = alertaRepository.findByFiltros(establecimientoId, estado, disparadorEnum,
                                severidadEnum);

                return alertas.stream()
                                .map(this::toResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public List<AlertaResponse> listarAlertasCelo(
                        String emailUsuario, Long establecimientoId, EstadoAlerta estado) {

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

                List<Alerta> alertas = (estado == null)
                                ? alertaRepository.findCeloByEstablecimiento(establecimientoId)
                                : alertaRepository.findCeloByEstablecimientoAndEstado(establecimientoId, estado);

                return alertas.stream()
                                .map(this::toResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public List<AlertaResponse> listarAlertasSalud(
                        String emailUsuario, Long establecimientoId, EstadoAlerta estado) {

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

                List<Alerta> alertas = (estado == null)
                                ? alertaRepository.findSaludByEstablecimiento(establecimientoId)
                                : alertaRepository.findSaludByEstablecimientoAndEstado(establecimientoId, estado);
                return alertas.stream()
                                .map(this::toResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public AlertaResumenResponse obtenerResumenAlertas(String emailUsuario, Long establecimientoId) {
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

                List<Object[]> conteos = alertaRepository.countAlertasActivasPorDisparador(establecimientoId);

                long reproductivo = 0;
                long sanitario = 0;
                long geoespacial = 0;

                for (Object[] row : conteos) {
                        DisparadorAlerta disparador = (DisparadorAlerta) row[0];
                        long count = ((Number) row[1]).longValue();

                        if (disparador == DisparadorAlerta.CELO) {
                                reproductivo = count;
                        } else if (disparador == DisparadorAlerta.SALUD) {
                                sanitario = count;
                        } else if (disparador == DisparadorAlerta.ABIGEATO) {
                                geoespacial = count;
                        }
                }

                return AlertaResumenResponse.builder()
                                .reproductivo(reproductivo)
                                .sanitario(sanitario)
                                .geoespacial(geoespacial)
                                .build();
        }

        @Override
        @SuppressWarnings("null")
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public AlertaDetalleResponse obtenerDetalleAlerta(
                        String emailUsuario, Long establecimientoId, Long alertaId) {

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

                Alerta alerta = alertaRepository.findById(alertaId)
                                .orElseThrow(() -> new IllegalArgumentException("Alerta no encontrada."));

                if (alerta.getAnimal() == null || alerta.getAnimal().getEstablecimiento() == null ||
                                !alerta.getAnimal().getEstablecimiento().getId().equals(establecimientoId)) {
                        throw new AccessDeniedException("La alerta no pertenece al establecimiento indicado.");
                }

                String ventanaOptima = null;
                if (alerta.getVentanaInicioIns() != null && alerta.getVentanaFinIns() != null) {
                        long horas = java.time.Duration.between(alerta.getVentanaInicioIns(), alerta.getVentanaFinIns())
                                        .toHours();
                        ventanaOptima = horas + " horas";
                }

                List<EstadoReproductivoResponse> ultimosEstados = alerta.getAnimal()
                                .getEstadosReproductivos().stream()
                                .limit(5)
                                .map(e -> {
                                        String registradoPor = null;
                                        if (e.getUsuarioRegistrador() != null) {
                                                registradoPor = e.getUsuarioRegistrador().getNombre();
                                                if (e.getUsuarioRegistrador().getApellido() != null
                                                                && !e.getUsuarioRegistrador().getApellido().isEmpty()) {
                                                        registradoPor += " " + e.getUsuarioRegistrador().getApellido();
                                                }
                                                if (registradoPor == null || registradoPor.isBlank()) {
                                                        registradoPor = e.getUsuarioRegistrador().getEmail();
                                                }
                                        }
                                        return dev.vigia.backend.dto.EstadoReproductivoResponse.builder()
                                                        .id(e.getId())
                                                        .animalId(alerta.getAnimal().getId())
                                                        .estado(e.getEstado().name())
                                                        .fechaUltimoParto(e.getFechaUltParto())
                                                        .fechaUltimaInseminacion(e.getFechaUltInseminacion())
                                                        .origen(e.getOrigen() != null ? e.getOrigen().name() : null)
                                                        .fechaRegistro(e.getFechaRegistro())
                                                        .registradoPor(registradoPor)
                                                        .build();
                                })
                                .collect(Collectors.toList());

                String usuarioAccionStr = nombreVisible(alerta.getUsuarioConfirmacion());

                DetalleSanitarioResponse detalleSanitario = null;
                if (alerta.getDisparador() == DisparadorAlerta.SALUD) {
                        detalleSanitario = construirDetalleSanitario(alerta);
                }

                DetalleGeoespacialResponse detalleGeoespacial = null;
                if (alerta.getDisparador() == DisparadorAlerta.FUERA_DE_PERIMETRO) {
                        detalleGeoespacial = construirDetalleGeoespacial(alerta);
                }

                String descripcionDatoOrigen = construirDescripcionDatoOrigen(alerta, detalleSanitario);

                LocalDateTime ultimaSenial = ultimaSenialDe(alerta);

                return AlertaDetalleResponse.builder()
                                .id(alerta.getId())
                                .animalId(alerta.getAnimal().getId())
                                .animalCaravanaOficial(alerta.getAnimal().getIdCaravanaOficial())
                                .animalAlias(alerta.getAnimal().getAlias())
                                .animalRaza(alerta.getAnimal().getRaza())
                                .tsGeneracion(alerta.getTsGeneracion())
                                .latitud(alerta.getLatitud())
                                .longitud(alerta.getLongitud())
                                .ventanaOptimaHoras(ventanaOptima)
                                .ventanaInicioIns(alerta.getVentanaInicioIns())
                                .ventanaFinIns(alerta.getVentanaFinIns())
                                .datoDisparador(alerta.getDisparador())
                                .estadoActual(alerta.getEstado())
                                .usuarioAccion(usuarioAccionStr)
                                .ultimosEstadosReproductivos(ultimosEstados)
                                .valorDetectado(alerta.getValorDetectado())
                                .valorUmbral(alerta.getValorUmbral())
                                .descripcionDatoOrigen(descripcionDatoOrigen)
                                .detalleSanitario(detalleSanitario)
                                .detalleGeoespacial(detalleGeoespacial)
                                .accionRecomendada(alerta.getAccionRecomendada())
                                .caravanaUltimaSenial(ultimaSenial)
                                .datoPosiblementeDesactualizado(esDatoDesactualizado(alerta, ultimaSenial))
                                .horasUmbralDatoDesactualizado(datoDesactualizadoEvaluator.getHorasUmbral())
                                .build();
        }

        private static LocalDateTime ultimaSenialDe(Alerta alerta) {
                return alerta.getAnimal() != null && alerta.getAnimal().getCaravana() != null
                                ? alerta.getAnimal().getCaravana().getUltimaSenial()
                                : null;
        }

        private boolean esDatoDesactualizado(Alerta alerta, LocalDateTime ultimaSenial) {
                return datoDesactualizadoEvaluator.esDatoDesactualizado(alerta.getDisparador(), alerta.getTipo(),
                                alerta.getEstado(), ultimaSenial);
        }

        private String construirDescripcionDatoOrigen(Alerta alerta, DetalleSanitarioResponse detalleSanitario) {
                if (detalleSanitario != null && detalleSanitario.getDescripcionDesvio() != null) {
                        return detalleSanitario.getDescripcionDesvio();
                }

                Integer confianzaPct = formatConfianzaPct(alerta.getValorDetectado());
                DisparadorAlerta disparador = alerta.getDisparador();

                if (Alerta.TIPO_ANESTRO.equals(alerta.getTipo())) {
                        return String.format("El animal no registra celo hace %d días (umbral: %d días).",
                                        Math.round(alerta.getValorDetectado()), Math.round(alerta.getValorUmbral()));
                }

                if (disparador == DisparadorAlerta.CELO) {
                        if (confianzaPct != null) {
                                return String.format(
                                                "Se detectaron signos de celo en el comportamiento del animal "
                                                                + "(mayor actividad y montas), con una confianza del %d%%.",
                                                confianzaPct);
                        }
                        return "Se detectaron signos de celo a partir del patrón de comportamiento del animal.";
                }

                if (disparador == DisparadorAlerta.ABIGEATO) {
                        if (confianzaPct != null) {
                                return String.format(
                                                "Se detectó un patrón de desplazamiento inusual del animal, distinto de su "
                                                                + "movimiento habitual en el establecimiento, con una confianza del %d%%.",
                                                confianzaPct);
                        }
                        return "Se detectó un patrón de desplazamiento inusual del animal, distinto de su "
                                        + "movimiento habitual en el establecimiento.";
                }

                if (disparador == DisparadorAlerta.FUERA_DE_PERIMETRO) {
                        return "El sistema detectó que el animal salió del perímetro asignado.";
                }

                if (confianzaPct != null) {
                        return String.format(
                                        "El sistema detectó un comportamiento que se aparta de lo habitual para este animal, "
                                                        + "con una confianza del %d%%.",
                                        confianzaPct);
                }
                return "El sistema detectó un comportamiento que se aparta de lo habitual para este animal.";
        }

        private Integer formatConfianzaPct(Double valor) {
                if (valor == null || valor < 0.0 || valor > 1.0) {
                        return null;
                }
                return (int) Math.round(valor * 100.0);
        }

        private DetalleSanitarioResponse construirDetalleSanitario(Alerta alerta) {
                boolean desvio = alerta.getDesvioDetectado() != null ? alerta.getDesvioDetectado() : true;
                String severidad = alerta.getSeveridad() != null ? alerta.getSeveridad().name() : null;
                String posibleCausa = alerta.getPosibleCausa() != null
                                ? alerta.getPosibleCausa()
                                : ("SALUD".equals(alerta.getTipo()) ? null : alerta.getTipo());
                Double confianza = alerta.getConfianzaModelo() != null
                                ? alerta.getConfianzaModelo().doubleValue()
                                : null;

                SerieComparativaSnapshot snap = null;
                String json = alerta.getSerieComparativaJson();
                if (json != null && !json.isBlank()) {
                        try {
                                snap = objectMapper.readValue(json, SerieComparativaSnapshot.class);
                        } catch (Exception e) {
                                log.warn("Snapshot de serie comparativa inválido para alerta {}: {}",
                                                alerta.getId(), e.getMessage());
                        }
                }

                String descripcion = (snap != null && snap.getDescripcionDesvio() != null)
                                ? snap.getDescripcionDesvio()
                                : descripcionGenerica(severidad, posibleCausa);

                return DetalleSanitarioResponse.builder()
                                .desvioDetectado(desvio)
                                .severidad(severidad)
                                .posibleCausa(posibleCausa)
                                .descripcionDesvio(descripcion)
                                .confianzaModelo(confianza)
                                .perfilCalculadoEn(snap != null ? snap.getPerfilCalculadoEn() : null)
                                .ventanaDesde(snap != null ? snap.getVentanaDesde() : null)
                                .ventanaHasta(snap != null ? snap.getVentanaHasta() : null)
                                .serieActividad(snap != null ? snap.getSerieActividad() : null)
                                .serieDesplazamiento(snap != null ? snap.getSerieDesplazamiento() : null)
                                .build();
        }

        private DetalleGeoespacialResponse construirDetalleGeoespacial(Alerta alerta) {
                Long segundosFuera = null;
                if (alerta.getEstado() == EstadoAlerta.PENDIENTE) {
                        segundosFuera = java.time.Duration.between(alerta.getTsGeneracion(), LocalDateTime.now()).getSeconds();
                } else if (alerta.getTsConfirmacion() != null) {
                        segundosFuera = java.time.Duration.between(alerta.getTsGeneracion(), alerta.getTsConfirmacion()).getSeconds();
                }

                String nombreLote = (alerta.getAnimal() != null && alerta.getAnimal().getRodeoLote() != null)
                                ? alerta.getAnimal().getRodeoLote().getNombre()
                                : null;

                return DetalleGeoespacialResponse.builder()
                                .latitud(alerta.getLatitud())
                                .longitud(alerta.getLongitud())
                                .nombreLote(nombreLote)
                                .segundosFuera(segundosFuera)
                                .build();
        }

        private String descripcionGenerica(String severidad, String posibleCausa) {
                String sev = severidad != null ? severidad.toLowerCase() : "desconocida";
                String causa = (posibleCausa != null && !posibleCausa.isBlank()) ? posibleCausa : "no determinada";
                return String.format(
                                "Desvío sanitario de severidad %s detectado respecto del perfil de referencia del animal. Posible causa: %s.",
                                sev, causa);
        }

        private AlertaResponse toResponse(Alerta alerta) {
                LocalDateTime ultimaSenial = datoDesactualizadoEvaluator.aplica(alerta.getDisparador(),
                                alerta.getTipo(), alerta.getEstado()) ? ultimaSenialDe(alerta) : null;
                return AlertaResponse.builder()
                                .id(alerta.getId())
                                .tipo(alerta.getDisparador() != null ? alerta.getDisparador().name() : alerta.getTipo())
                                .subtipo(alerta.getTipo())
                                .severidad(alerta.getSeveridad() != null ? alerta.getSeveridad().name() : null)
                                .animalId(alerta.getAnimal() != null ? alerta.getAnimal().getId() : null)
                                .tsGeneracion(alerta.getTsGeneracion())
                                .tsConfirmacion(alerta.getTsConfirmacion())
                                .latitud(alerta.getLatitud())
                                .longitud(alerta.getLongitud())
                                .estado(alerta.getEstado())
                                .accionRecomendada(alerta.getAccionRecomendada())
                                .caravanaUltimaSenial(ultimaSenial)
                                .datoPosiblementeDesactualizado(esDatoDesactualizado(alerta, ultimaSenial))
                                .horasUmbralDatoDesactualizado(datoDesactualizadoEvaluator.getHorasUmbral())
                                .build();
        }

        @Override
        @SuppressWarnings("null")
        @org.springframework.transaction.annotation.Transactional
        public AlertaResponse actualizarEstadoAlerta(String emailUsuario, Long alertaId,
                        EstadoAlerta nuevoEstado, String notas) {

                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Alerta alerta = alertaRepository.findByIdForUpdate(alertaId)
                                .orElseThrow(() -> new IllegalArgumentException("Alerta no encontrada."));

                Long establecimientoId = alerta.getAnimal().getEstablecimiento().getId();
                List<Establecimiento> establecimientos = establecimientoRepository
                                .findActiveEstablecimientoByUsuarioEmail(emailUsuario);

                boolean perteneceAlEstablecimiento = establecimientos.stream()
                                .anyMatch(e -> e.getId().equals(establecimientoId));

                if (!perteneceAlEstablecimiento) {
                        throw new AccessDeniedException(
                                        "El usuario no pertenece al establecimiento indicado o no está activo.");
                }

                validarCamperoNoActuaSobreSalud(usuario, alerta);

                EstadoAlerta estadoAnterior = alerta.getEstado();

                if (estadoAnterior != EstadoAlerta.PENDIENTE) {
                        throw new AlertaYaProcesadaException(mensajeAlertaYaProcesada(alerta));
                }

                if (estadoAnterior == EstadoAlerta.PENDIENTE &&
                                (nuevoEstado == EstadoAlerta.CONFIRMADA || nuevoEstado == EstadoAlerta.DESCARTADA)) {
                        alerta.setTsConfirmacion(LocalDateTime.now());
                        alerta.setUsuarioConfirmacion(usuario);
                        alerta.setEstado(nuevoEstado);

                        if (nuevoEstado == EstadoAlerta.DESCARTADA && notas != null && !notas.trim().isEmpty()) {
                                alerta.setMotivoDescarte(notas.trim());
                        }

                        if (nuevoEstado == EstadoAlerta.CONFIRMADA && alerta.getDisparador() == DisparadorAlerta.CELO) {
                                EstadoReproductivo estadoCelo = new EstadoReproductivo();
                                estadoCelo.setAnimal(alerta.getAnimal());
                                estadoCelo.setUsuarioRegistrador(usuario);
                                // Confirmar un anestro deja el animal en ANESTRO: el detector no lo vuelve a alertar.
                                estadoCelo.setEstado(Alerta.TIPO_ANESTRO.equals(alerta.getTipo())
                                                ? TipoReproductivo.ANESTRO
                                                : TipoReproductivo.CELO);
                                estadoCelo.setOrigen(OrigenEstado.MANUAL);
                                estadoCelo.setFechaRegistro(LocalDateTime.now());

                                List<EstadoReproductivo> estadosActuales = alerta.getAnimal().getEstadosReproductivos();
                                if (estadosActuales != null && !estadosActuales.isEmpty()) {
                                        EstadoReproductivo ultimo = estadosActuales.get(0);
                                        estadoCelo.setFechaUltParto(ultimo.getFechaUltParto());
                                        estadoCelo.setFechaUltInseminacion(ultimo.getFechaUltInseminacion());
                                }
                                estadoReproductivoRepository.save(estadoCelo);
                        }
                } else {
                        throw new IllegalArgumentException("Estado inválido para la actualización.");
                }
                Alerta actualizado = alertaRepository.save(alerta);

                AlertaEventDTO eventDTO = AlertaEventDTO.builder()
                                .id(actualizado.getId())
                                .animalId(actualizado.getAnimal().getId())
                                .estado(actualizado.getEstado())
                                .tipo(actualizado.getTipo())
                                .disparador(actualizado.getDisparador())
                                .accionRecomendada(actualizado.getAccionRecomendada())
                                .tsGeneracion(actualizado.getTsGeneracion())
                                .build();
                rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", eventDTO);

                return this.toResponse(actualizado);
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public AlertaResponse archivarAlertaPorVencimiento(String emailUsuario, Long establecimientoId, Long alertaId) {
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

                Alerta alerta = alertaRepository.findById(alertaId)
                                .orElseThrow(() -> new IllegalArgumentException("Alerta no encontrada."));

                if (alerta.getAnimal() == null || alerta.getAnimal().getEstablecimiento() == null ||
                                !alerta.getAnimal().getEstablecimiento().getId().equals(establecimientoId)) {
                        throw new AccessDeniedException("La alerta no pertenece al establecimiento indicado.");
                }

                if (Alerta.TIPO_ANESTRO.equals(alerta.getTipo())) {
                        throw new IllegalArgumentException("Las alertas de anestro no tienen ventana de inseminación.");
                }

                if (alerta.getEstado() != EstadoAlerta.PENDIENTE) {
                        throw new IllegalArgumentException("La alerta ya no está PENDIENTE.");
                }

                alerta.setEstado(EstadoAlerta.ARCHIVADA);
                alerta.setMotivoDescarte("oportunidad_no_aprovechada");
                alerta.setTsConfirmacion(LocalDateTime.now());
                Alerta actualizado = alertaRepository.save(alerta);

                AlertaEventDTO eventDTO = AlertaEventDTO.builder()
                                .id(actualizado.getId())
                                .animalId(actualizado.getAnimal().getId())
                                .estado(actualizado.getEstado())
                                .tipo(actualizado.getTipo())
                                .tsGeneracion(actualizado.getTsGeneracion())
                                .build();
                rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "alerta.update", eventDTO);

                return this.toResponse(actualizado);
        }

        @Override
        @SuppressWarnings("null")
        @Transactional(readOnly = true)
        public VentanaInseminacionResponse obtenerEstadoVentanaInseminacion(String emailUsuario, Long establecimientoId,
                        Long alertaId) {
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

                Alerta alerta = alertaRepository.findById(alertaId)
                                .orElseThrow(() -> new IllegalArgumentException("Alerta no encontrada."));

                if (alerta.getAnimal() == null || alerta.getAnimal().getEstablecimiento() == null ||
                                !alerta.getAnimal().getEstablecimiento().getId().equals(establecimientoId)) {
                        throw new AccessDeniedException("La alerta no pertenece al establecimiento indicado.");
                }

                if (alerta.getDisparador() != DisparadorAlerta.CELO) {
                        throw new IllegalArgumentException("La alerta no es de tipo CELO.");
                }
                if (Alerta.TIPO_ANESTRO.equals(alerta.getTipo())) {
                        throw new IllegalArgumentException("Las alertas de anestro no tienen ventana de inseminación.");
                }

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime inicio = alerta.getVentanaInicioIns();
                LocalDateTime fin = alerta.getVentanaFinIns();

                if (inicio == null || fin == null) {
                        inicio = alerta.getTsGeneracion().plusHours(6);
                        fin = alerta.getTsGeneracion().plusHours(24);
                }

                long tiempoRestanteSegundos = 0;
                double porcentajeTranscurrido = 0.0;
                boolean proximaAVencer = false;
                String estado;

                if (now.isAfter(fin) || now.isEqual(fin)) {
                        estado = "VENCIDA";
                        tiempoRestanteSegundos = 0;
                        porcentajeTranscurrido = 100.0;
                        proximaAVencer = false;
                } else if (now.isBefore(inicio)) {
                        estado = "FUTURA";
                        tiempoRestanteSegundos = Duration.between(now, fin).getSeconds();
                        porcentajeTranscurrido = 0.0;
                        proximaAVencer = false;
                } else {
                        estado = "ACTIVA";
                        tiempoRestanteSegundos = Duration.between(now, fin).getSeconds();

                        long duracionTotal = Duration.between(inicio, fin).getSeconds();
                        long duracionTranscurrida = Duration.between(inicio, now).getSeconds();

                        porcentajeTranscurrido = ((double) duracionTranscurrida / duracionTotal) * 100.0;

                        if (porcentajeTranscurrido > 100.0)
                                porcentajeTranscurrido = 100.0;
                        if (porcentajeTranscurrido < 0.0)
                                porcentajeTranscurrido = 0.0;

                        if (tiempoRestanteSegundos <= 7200) {
                                proximaAVencer = true;
                        }
                }

                return VentanaInseminacionResponse.builder()
                                .tiempoRestanteSegundos(tiempoRestanteSegundos)
                                .porcentajeTranscurrido(porcentajeTranscurrido)
                                .proximaAVencer(proximaAVencer)
                                .estado(estado)
                                .build();
        }

        @Override
        @org.springframework.transaction.annotation.Transactional
        @SuppressWarnings("null")
        public AlertaResponse intervenirAlerta(String emailUsuario, Long alertaId, IntervencionRequest request) {

                Alerta alerta = alertaRepository.findById(alertaId)
                                .orElseThrow(() -> new IllegalArgumentException("Alerta no encontrada"));

                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

                List<Establecimiento> establecimientos = establecimientoRepository
                                .findActiveEstablecimientoByUsuarioEmail(emailUsuario);

                boolean perteneceAlEstablecimiento = establecimientos.stream()
                                .anyMatch(e -> e.getId().equals(alerta.getAnimal().getEstablecimiento().getId()));

                if (!perteneceAlEstablecimiento) {
                        throw new AccessDeniedException("No tiene permiso para intervenir esta alerta");
                }

                validarCamperoNoActuaSobreSalud(usuario, alerta);

                if (alerta.getEstado() != EstadoAlerta.CONFIRMADA) {
                        throw new IllegalArgumentException(
                                        "La alerta debe estar confirmada antes de registrar una intervención.");
                }

                if (intervencionRepository.existsByAlertaId(alertaId)) {
                        throw new IllegalArgumentException("Ya existe una intervención registrada para esta alerta.");
                }

                if (request.getDescripcion() == null || request.getDescripcion().trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                        "La descripción es obligatoria para registrar una intervención.");
                }

                if (request.getTipo() == null) {
                        throw new IllegalArgumentException(
                                        "El tipo de intervención es obligatorio para registrar una intervención.");
                }

                Intervencion intervencion = new Intervencion();
                intervencion.setAlerta(alerta);
                intervencion.setAnimal(alerta.getAnimal());
                intervencion.setInterventor(usuario);
                intervencion.setDescripcion(request.getDescripcion().trim());
                intervencion.setFechaHora(
                                request.getFechaHora() != null ? request.getFechaHora() : LocalDateTime.now());
                intervencion.setTipo(request.getTipo());
                intervencionRepository.save(intervencion);

                registrarEstadoDerivado(intervencion, request, usuario);

                return this.toResponse(alerta);
        }

        /**
         * Registra un diagnóstico veterinario sobre una alerta sanitaria confirmada
         * de un establecimiento del usuario. Solo inserta en diagnosticos: no
         * modifica la alerta ni publica eventos.
         */
        @Override
        @Transactional
        @SuppressWarnings("null")
        public DiagnosticoResponse registrarDiagnostico(String emailUsuario, Long alertaId, DiagnosticoRequest request) {

                Alerta alerta = alertaRepository.findById(alertaId)
                                .orElseThrow(() -> new IllegalArgumentException("Alerta no encontrada"));

                Usuario veterinario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

                Long establecimientoId = alerta.getAnimal().getEstablecimiento().getId();
                boolean perteneceAlEstablecimiento = establecimientoRepository
                                .findActiveEstablecimientoByUsuarioEmail(emailUsuario).stream()
                                .anyMatch(e -> e.getId().equals(establecimientoId));
                if (!perteneceAlEstablecimiento) {
                        throw new AccessDeniedException("No tiene permiso para diagnosticar esta alerta");
                }

                if (alerta.getDisparador() != DisparadorAlerta.SALUD) {
                        throw new IllegalArgumentException(
                                        "Solo se pueden registrar diagnósticos sobre alertas sanitarias.");
                }

                if (alerta.getEstado() != EstadoAlerta.CONFIRMADA) {
                        throw new IllegalArgumentException(
                                        "La alerta debe estar confirmada antes de registrar un diagnóstico.");
                }

                String texto = request.getDiagnostico() != null ? request.getDiagnostico().trim() : "";
                String tratamiento = request.getTratamiento() != null ? request.getTratamiento().trim() : "";
                if (texto.isEmpty()) {
                        throw new IllegalArgumentException("El diagnóstico es obligatorio.");
                }
                if (texto.length() > 200) {
                        throw new IllegalArgumentException("El diagnóstico no puede superar los 200 caracteres.");
                }
                if (tratamiento.length() > 500) {
                        throw new IllegalArgumentException(
                                        "La indicación o tratamiento no puede superar los 500 caracteres.");
                }

                Diagnostico diagnostico = new Diagnostico();
                diagnostico.setAlerta(alerta);
                diagnostico.setAnimal(alerta.getAnimal());
                diagnostico.setVeterinario(veterinario);
                diagnostico.setDiagnostico(texto);
                diagnostico.setIndicaciones(tratamiento);
                diagnostico.setFecha(LocalDateTime.now());

                return toDiagnosticoResponse(diagnosticoRepository.save(diagnostico));
        }

        /**
         * Lista los diagnósticos de los establecimientos activos del usuario,
         * opcionalmente restringidos a un establecimiento y/o a un animal.
         */
        @Override
        @Transactional(readOnly = true)
        public List<DiagnosticoResponse> listarDiagnosticos(String emailUsuario, Long animalId, Long establecimientoId) {

                List<Long> ids = establecimientoRepository.findActiveEstablecimientoByUsuarioEmail(emailUsuario)
                                .stream().map(Establecimiento::getId).toList();

                if (establecimientoId != null) {
                        if (!ids.contains(establecimientoId)) {
                                throw new AccessDeniedException(
                                                "El usuario no pertenece al establecimiento indicado o no está activo.");
                        }
                        ids = List.of(establecimientoId);
                }

                if (ids.isEmpty()) {
                        return List.of();
                }

                // ponytail: sin paginación, agregar Pageable cuando un veterinario tenga cientos de diagnósticos
                return diagnosticoRepository.findVisibles(ids, animalId).stream()
                                .map(this::toDiagnosticoResponse)
                                .toList();
        }

        private DiagnosticoResponse toDiagnosticoResponse(Diagnostico d) {
                Alerta alerta = d.getAlerta();
                Animal animal = d.getAnimal();

                DetalleSanitarioResponse detalleSanitario = alerta.getDisparador() == DisparadorAlerta.SALUD
                                ? construirDetalleSanitario(alerta)
                                : null;

                return DiagnosticoResponse.builder()
                                .id(d.getId())
                                .alertaId(alerta.getId())
                                .animalId(animal.getId())
                                .animalAlias(animal.getAlias())
                                .animalCaravanaOficial(animal.getIdCaravanaOficial())
                                .establecimientoNombre(animal.getEstablecimiento().getNombre())
                                .diagnostico(d.getDiagnostico())
                                .tratamiento(d.getIndicaciones())
                                .fechaRegistro(d.getFecha())
                                .veterinarioNombre(nombreVisible(d.getVeterinario()))
                                .disparador(alerta.getDisparador() != null ? alerta.getDisparador().name() : null)
                                .datoOrigen(construirDescripcionDatoOrigen(alerta, detalleSanitario))
                                .build();
        }

        @Override
        @Transactional(readOnly = true)
        @SuppressWarnings("null")
        public List<LineaTiempoEventoResponse> obtenerLineaTiempoAlertas(
                        String emailUsuario,
                        Long animalId,
                        String tipo,
                        LocalDateTime desde,
                        LocalDateTime hasta) {

                usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Long establecimientoId = animal.getEstablecimiento() != null
                                ? animal.getEstablecimiento().getId()
                                : null;

                List<Establecimiento> establecimientos = establecimientoRepository
                                .findActiveEstablecimientoByUsuarioEmail(emailUsuario);

                boolean perteneceAlEstablecimiento = establecimientoId != null &&
                                establecimientos.stream()
                                                .anyMatch(e -> e.getId().equals(establecimientoId));

                if (!perteneceAlEstablecimiento) {
                        throw new AccessDeniedException(
                                        "El usuario no pertenece al establecimiento del animal indicado.");
                }

                DisparadorAlerta disparadorEnum = resolverDisparador(tipo);

                List<Alerta> alertas = alertaRepository.findByAnimalConFiltros(
                                animalId, disparadorEnum, desde, hasta);
                List<Intervencion> intervenciones = intervencionRepository
                                .findByAnimalConRango(animalId, desde, hasta);

                List<LineaTiempoEventoResponse> eventos = new ArrayList<>();

                alertas.stream()
                                .map(this::alertaToLineaTiempo)
                                .forEach(eventos::add);

                intervenciones.stream()
                                .map(this::intervencionToLineaTiempo)
                                .forEach(eventos::add);
                eventos.sort(Comparator.comparing(
                                LineaTiempoEventoResponse::getFecha,
                                Comparator.nullsLast(Comparator.naturalOrder())));

                return eventos;
        }

        @SuppressWarnings("null")
        private DisparadorAlerta resolverDisparador(String tipo) {
                if (tipo == null || tipo.isBlank()) {
                        return null;
                }
                return switch (tipo.trim().toLowerCase()) {
                        case "celo", "reproductivo" -> DisparadorAlerta.CELO;
                        case "sanitario" -> DisparadorAlerta.SALUD;
                        case "geoespacial" -> DisparadorAlerta.ABIGEATO;
                        case "perimetro" -> DisparadorAlerta.FUERA_DE_PERIMETRO;
                        default -> null;
                };
        }

        private String resolverEstadoResolucion(EstadoAlerta estado) {
                if (estado == null)
                        return "SIN_ATENDER";
                return switch (estado) {
                        case CONFIRMADA -> "CONFIRMADA";
                        case DESCARTADA -> "DESCARTADA";
                        case ARCHIVADA -> "ARCHIVADA";
                        default -> "SIN_ATENDER";
                };
        }

        private LineaTiempoEventoResponse alertaToLineaTiempo(Alerta alerta) {
                return LineaTiempoEventoResponse.builder()
                                .tipoEvento("ALERTA")
                                .id(alerta.getId())
                                .fecha(alerta.getTsGeneracion())
                                .tipo(alerta.getDisparador() != null
                                                ? alerta.getDisparador().name()
                                                : alerta.getTipo())
                                .subtipo(alerta.getTipo())
                                .severidad(alerta.getSeveridad() != null
                                                ? alerta.getSeveridad().name()
                                                : null)
                                .estadoResolucion(resolverEstadoResolucion(alerta.getEstado()))
                                .usuarioResolucion(nombreVisible(alerta.getUsuarioConfirmacion()))
                                .tsResolucion(alerta.getTsConfirmacion())
                                .descripcion(alerta.getAccionRecomendada())
                                .build();
        }

        /** "Nombre Apellido", o el email si el usuario no cargó nombre; null si no hay usuario. */
        private static String nombreVisible(Usuario u) {
                if (u == null) {
                        return null;
                }
                String nombre = ((u.getNombre() != null ? u.getNombre() : "") + " "
                                + (u.getApellido() != null ? u.getApellido() : "")).trim();
                return nombre.isEmpty() ? u.getEmail() : nombre;
        }

        private LineaTiempoEventoResponse intervencionToLineaTiempo(Intervencion intervencion) {
                return LineaTiempoEventoResponse.builder()
                                .tipoEvento("INTERVENCION")
                                .id(intervencion.getId())
                                .fecha(intervencion.getFechaHora())
                                .tipoIntervencion(intervencion.getTipo() != null
                                                ? intervencion.getTipo().name()
                                                : null)
                                .descripcion(intervencion.getDescripcion())
                                .interventor(nombreVisible(intervencion.getInterventor()))
                                .alertaAsociadaId(intervencion.getAlerta() != null
                                                ? intervencion.getAlerta().getId()
                                                : null)
                                .build();
        }

        private static final DateTimeFormatter FECHA_ACCION = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

        private String mensajeAlertaYaProcesada(Alerta alerta) {
                if (alerta.getEstado() == EstadoAlerta.ARCHIVADA) {
                        return "Esta alerta ya fue archivada porque venció la ventana de inseminación.";
                }
                if (alerta.getEstado() == EstadoAlerta.VENCIDA) {
                        return "Esta alerta ya venció y no admite cambios.";
                }
                String accion = switch (alerta.getEstado()) {
                        case CONFIRMADA -> "confirmada";
                        case DESCARTADA -> "descartada";
                        default -> "procesada";
                };
                StringBuilder msg = new StringBuilder("Esta alerta ya fue ").append(accion);
                String quien = nombreVisible(alerta.getUsuarioConfirmacion());
                if (quien != null) {
                        msg.append(" por ").append(quien);
                }
                if (alerta.getTsConfirmacion() != null) {
                        msg.append(" el ").append(alerta.getTsConfirmacion().format(FECHA_ACCION));
                }
                return msg.append(".").toString();
        }

        private void validarCamperoNoActuaSobreSalud(Usuario usuario, Alerta alerta) {
                if (usuario.getRol() == Rol.CAMPERO && alerta.getDisparador() == DisparadorAlerta.SALUD) {
                        throw new AccessDeniedException("El campero no puede actuar sobre alertas sanitarias.");
                }
        }

        private void registrarEstadoDerivado(Intervencion intervencion, IntervencionRequest request, Usuario usuario) {
                if (intervencion.getTipo() == TipoIntervencion.INSEMINACION) {
                        EstadoReproductivo estado = new EstadoReproductivo();
                        estado.setAnimal(intervencion.getAnimal());
                        estado.setUsuarioRegistrador(usuario);
                        estado.setEstado(TipoReproductivo.INSEMINADA);
                        estado.setFechaUltInseminacion(intervencion.getFechaHora().toLocalDate());
                        estado.setOrigen(OrigenEstado.MANUAL);
                        estado.setFechaRegistro(intervencion.getFechaHora());
                        estadoReproductivoRepository.save(estado);
                } else if (intervencion.getTipo() == TipoIntervencion.REVISION_VETERINARIA
                                && request.getEstadoSalud() != null) {
                        EstadoSalud estado = new EstadoSalud();
                        estado.setAnimal(intervencion.getAnimal());
                        estado.setUsuarioVeterinario(usuario);
                        estado.setFechaRevision(intervencion.getFechaHora().toLocalDate());
                        estado.setEstado(request.getEstadoSalud());
                        estado.setDiagnosticoNotas(intervencion.getDescripcion());
                        estado.setOrigen(OrigenEstado.MANUAL);
                        estado.setFechaRegistro(intervencion.getFechaHora());
                        estadoSaludRepository.save(estado);
                }
        }
}
