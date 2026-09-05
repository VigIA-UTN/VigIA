package dev.vigia.backend.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
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
import dev.vigia.backend.dto.SerieComparativaSnapshot;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.model.Intervencion;
import dev.vigia.backend.model.SeveridadAlerta;
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
        private final UsuarioRepository usuarioRepository;
        private final EstablecimientoRepository establecimientoRepository;
        private final IntervencionRepository intervencionRepository;
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
                                .detalleSanitario(detalleSanitario)
                                .build();
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
                intervencion.setFechaHora(request.getFechaHora() != null ? request.getFechaHora() : LocalDateTime.now());
                intervencion.setTipo(request.getTipo());
                intervencionRepository.save(intervencion);

                return this.toResponse(alerta);
        }
}
