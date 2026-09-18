package dev.vigia.backend.service.impl;

import java.util.Comparator;
import java.time.Duration;
import java.time.LocalDateTime;
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
import dev.vigia.backend.dto.EstadoReproductivoResponse;
import dev.vigia.backend.dto.IntervencionRequest;
import dev.vigia.backend.dto.LineaTiempoEventoResponse;
import dev.vigia.backend.dto.SerieComparativaSnapshot;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.EstadoSaludRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.EstadoSalud;
import dev.vigia.backend.model.Intervencion;
import dev.vigia.backend.model.OrigenEstado;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.TipoIntervencion;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.service.AlertaService;
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
        private final RabbitTemplate rabbitTemplate;
        private final ObjectMapper objectMapper;

        @Override
        public List<AlertaResponse> listarAlertas(String emailUsuario, Long establecimientoId, String tipo,
                        String severidad, EstadoAlerta estado) {
                // 1. Validar que el usuario existe
                usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                // 2. Verificar que el usuario pertenece al establecimiento
                List<Establecimiento> establecimientos = establecimientoRepository
                                .findActiveEstablecimientoByUsuarioEmail(emailUsuario);

                boolean perteneceAlEstablecimiento = establecimientos.stream()
                                .anyMatch(e -> e.getId().equals(establecimientoId));

                if (!perteneceAlEstablecimiento) {
                        throw new AccessDeniedException(
                                        "El usuario no pertenece al establecimiento indicado o no está activo.");
                }

                DisparadorAlerta disparadorEnum = null;
                if (tipo != null && !tipo.isBlank()) {
                        String t = tipo.trim().toLowerCase();
                        if (t.equals("reproductivo"))
                                disparadorEnum = DisparadorAlerta.CELO;
                        else if (t.equals("sanitario"))
                                disparadorEnum = DisparadorAlerta.SALUD;
                        else if (t.equals("geoespacial"))
                                disparadorEnum = DisparadorAlerta.ABIGEATO;
                }

                dev.vigia.backend.model.SeveridadAlerta severidadEnum = null;
                if (severidad != null && !severidad.isBlank()) {
                        String s = severidad.trim().toUpperCase();
                        if (s.equals("ALTA"))
                                severidadEnum = SeveridadAlerta.ALTA;
                        else if (s.equals("MEDIA"))
                                severidadEnum = SeveridadAlerta.MEDIA;
                        else if (s.equals("BAJA"))
                                severidadEnum = SeveridadAlerta.BAJA;
                }

                // 3. Consultar las alertas del establecimiento aplicando filtros
                List<Alerta> alertas = alertaRepository.findByFiltros(establecimientoId, estado, disparadorEnum,
                                severidadEnum);

                // 4. Mapear a DTO
                return alertas.stream()
                                .map(this::toResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public List<AlertaResponse> listarAlertasCelo(
                        String emailUsuario, Long establecimientoId, EstadoAlerta estado) {

                // 1. Validar que el usuario existe
                usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                // 2. Verificar que el usuario realmente pertenece al establecimiento
                // seleccionado.
                // Esto garantiza el aislamiento multi-tenant: un usuario no puede consultar
                // datos de un establecimiento ajeno aunque conozca su ID.
                List<Establecimiento> establecimientos = establecimientoRepository
                                .findActiveEstablecimientoByUsuarioEmail(emailUsuario);

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

        @Override
        public List<AlertaResponse> listarAlertasSalud(
                        String emailUsuario, Long establecimientoId, EstadoAlerta estado) {

                // 1. Validar que el usuario existe
                usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                // 2. Verificar que el usuario realmente pertenece al establecimiento
                // seleccionado.
                // Esto garantiza el aislamiento multi-tenant: un usuario no puede consultar
                // datos de un establecimiento ajeno aunque conozca su ID.
                List<Establecimiento> establecimientos = establecimientoRepository
                                .findActiveEstablecimientoByUsuarioEmail(emailUsuario);

                boolean perteneceAlEstablecimiento = establecimientos.stream()
                                .anyMatch(e -> e.getId().equals(establecimientoId));

                if (!perteneceAlEstablecimiento) {
                        throw new AccessDeniedException(
                                        "El usuario no pertenece al establecimiento indicado o no está activo.");
                }

                // 3. Consultar alertas del establecimiento seleccionado según filtro
                List<Alerta> alertas = (estado == null)
                                ? alertaRepository.findSaludByEstablecimiento(establecimientoId)
                                : alertaRepository.findSaludByEstablecimientoAndEstado(establecimientoId, estado);

                // 4. Mapear a DTO
                return alertas.stream()
                                .map(this::toResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public AlertaResumenResponse obtenerResumenAlertas(String emailUsuario, Long establecimientoId) {
                // 1. Validar que el usuario existe
                usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                // 2. Verificar pertenencia al establecimiento
                List<Establecimiento> establecimientos = establecimientoRepository
                                .findActiveEstablecimientoByUsuarioEmail(emailUsuario);

                boolean perteneceAlEstablecimiento = establecimientos.stream()
                                .anyMatch(e -> e.getId().equals(establecimientoId));

                if (!perteneceAlEstablecimiento) {
                        throw new AccessDeniedException(
                                        "El usuario no pertenece al establecimiento indicado o no está activo.");
                }

                // 3. Consultar la BD agrupando por disparador
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

                String usuarioAccionStr = null;
                if (alerta.getUsuarioConfirmacion() != null) {
                        usuarioAccionStr = alerta.getUsuarioConfirmacion().getNombre();
                        if (alerta.getUsuarioConfirmacion().getApellido() != null
                                        && !alerta.getUsuarioConfirmacion().getApellido().isEmpty()) {
                                usuarioAccionStr += " " + alerta.getUsuarioConfirmacion().getApellido();
                        }
                        if (usuarioAccionStr == null || usuarioAccionStr.isBlank()) {
                                usuarioAccionStr = alerta.getUsuarioConfirmacion().getEmail();
                        }
                }

                DetalleSanitarioResponse detalleSanitario = null;
                if (alerta.getDisparador() == DisparadorAlerta.SALUD) {
                        detalleSanitario = construirDetalleSanitario(alerta);
                }

                String descripcionDatoOrigen = construirDescripcionDatoOrigen(alerta, detalleSanitario);

                return AlertaDetalleResponse.builder()
                                .id(alerta.getId())
                                .animalId(alerta.getAnimal().getId())
                                .animalCaravanaOficial(alerta.getAnimal().getIdCaravanaOficial())
                                .animalAlias(alerta.getAnimal().getAlias())
                                .animalRaza(alerta.getAnimal().getRaza())
                                .tsGeneracion(alerta.getTsGeneracion())
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
                                .accionRecomendada(alerta.getAccionRecomendada())
                                .build();
        }

        private String construirDescripcionDatoOrigen(Alerta alerta, DetalleSanitarioResponse detalleSanitario) {
                if (detalleSanitario != null && detalleSanitario.getDescripcionDesvio() != null) {
                        return detalleSanitario.getDescripcionDesvio();
                }

                Integer confianzaPct = formatConfianzaPct(alerta.getValorDetectado());
                DisparadorAlerta disparador = alerta.getDisparador();

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

        private String descripcionGenerica(String severidad, String posibleCausa) {
                String sev = severidad != null ? severidad.toLowerCase() : "desconocida";
                String causa = (posibleCausa != null && !posibleCausa.isBlank()) ? posibleCausa : "no determinada";
                return String.format(
                                "Desvío sanitario de severidad %s detectado respecto del perfil de referencia del animal. Posible causa: %s.",
                                sev, causa);
        }

        private AlertaResponse toResponse(Alerta alerta) {
                return AlertaResponse.builder()
                                .id(alerta.getId())
                                .tipo(alerta.getDisparador() != null ? alerta.getDisparador().name() : alerta.getTipo())
                                .severidad(alerta.getSeveridad() != null ? alerta.getSeveridad().name() : null)
                                .animalId(alerta.getAnimal() != null ? alerta.getAnimal().getId() : null)
                                .tsGeneracion(alerta.getTsGeneracion())
                                .estado(alerta.getEstado())
                                .build();
        }

        @Override
        @SuppressWarnings("null")
        @org.springframework.transaction.annotation.Transactional
        public AlertaResponse actualizarEstadoAlerta(String emailUsuario, Long alertaId,
                        EstadoAlerta nuevoEstado, String notas) {

                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Alerta alerta = alertaRepository.findById(alertaId)
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

                EstadoAlerta estadoAnterior = alerta.getEstado();

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
                                estadoCelo.setEstado(TipoReproductivo.CELO);
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

                // Publicar evento para SSE
                AlertaEventDTO eventDTO = AlertaEventDTO.builder()
                                .id(actualizado.getId())
                                .animalId(actualizado.getAnimal().getId())
                                .estado(actualizado.getEstado())
                                .tipo(actualizado.getDisparador() != null ? actualizado.getDisparador().name()
                                                : actualizado.getTipo())
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

        // ── Línea de tiempo
        // ───────────────────────────────────────────────────────────

        @Override
        @Transactional(readOnly = true)
        @SuppressWarnings("null")
        public List<LineaTiempoEventoResponse> obtenerLineaTiempoAlertas(
                        String emailUsuario,
                        Long animalId,
                        String tipo,
                        LocalDateTime desde,
                        LocalDateTime hasta) {

                // 1. Validar que el usuario existe
                usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                // 2. Obtener el animal
                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                // 3. Verificar aislamiento multi-tenant: el animal debe pertenecer a un
                // establecimiento activo del usuario autenticado
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

                // 4. Resolver el filtro de tipo a DisparadorAlerta (solo se aplica a alertas)
                DisparadorAlerta disparadorEnum = resolverDisparador(tipo);

                // 5. Consultar alertas del animal con filtros
                List<Alerta> alertas = alertaRepository.findByAnimalConFiltros(
                                animalId, disparadorEnum, desde, hasta);

                // 6. Consultar intervenciones del animal (solo filtradas por rango de fechas)
                List<Intervencion> intervenciones = intervencionRepository
                                .findByAnimalConRango(animalId, desde, hasta);

                // 7. Mapear alertas a eventos de línea de tiempo
                List<LineaTiempoEventoResponse> eventos = new ArrayList<>();

                alertas.stream()
                                .map(this::alertaToLineaTiempo)
                                .forEach(eventos::add);

                // 8. Mapear intervenciones a eventos de línea de tiempo
                intervenciones.stream()
                                .map(this::intervencionToLineaTiempo)
                                .forEach(eventos::add);

                // 9. Ordenar la lista combinada cronológicamente
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
                                .severidad(alerta.getSeveridad() != null
                                                ? alerta.getSeveridad().name()
                                                : null)
                                .estadoResolucion(resolverEstadoResolucion(alerta.getEstado()))
                                .build();
        }

        private LineaTiempoEventoResponse intervencionToLineaTiempo(Intervencion intervencion) {
                String interventorNombre = null;
                if (intervencion.getInterventor() != null) {
                        interventorNombre = intervencion.getInterventor().getNombre();
                        if (intervencion.getInterventor().getApellido() != null
                                        && !intervencion.getInterventor().getApellido().isBlank()) {
                                interventorNombre += " " + intervencion.getInterventor().getApellido();
                        }
                        if (interventorNombre == null || interventorNombre.isBlank()) {
                                interventorNombre = intervencion.getInterventor().getEmail();
                        }
                }

                return LineaTiempoEventoResponse.builder()
                                .tipoEvento("INTERVENCION")
                                .id(intervencion.getId())
                                .fecha(intervencion.getFechaHora())
                                .tipoIntervencion(intervencion.getTipo() != null
                                                ? intervencion.getTipo().name()
                                                : null)
                                .descripcion(intervencion.getDescripcion())
                                .interventor(interventorNombre)
                                .alertaAsociadaId(intervencion.getAlerta() != null
                                                ? intervencion.getAlerta().getId()
                                                : null)
                                .build();
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
