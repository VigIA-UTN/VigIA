package dev.vigia.backend.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import dev.vigia.backend.dto.AlertaDetalleResponse;
import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.AlertaResumenResponse;
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
import dev.vigia.backend.service.AlertaService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AlertaServiceImpl implements AlertaService {

        private final AlertaRepository alertaRepository;
        private final UsuarioRepository usuarioRepository;
        private final EstablecimientoRepository establecimientoRepository;
        private final IntervencionRepository intervencionRepository;

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

                List<dev.vigia.backend.dto.EstadoReproductivoResponse> ultimosEstados = alerta.getAnimal().getEstadosReproductivos().stream()
                                .limit(5)
                                .map(e -> {
                                        String registradoPor = null;
                                        if (e.getUsuarioRegistrador() != null) {
                                                registradoPor = e.getUsuarioRegistrador().getNombre();
                                                if (e.getUsuarioRegistrador().getApellido() != null && !e.getUsuarioRegistrador().getApellido().isEmpty()) {
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
                        if (alerta.getUsuarioConfirmacion().getApellido() != null && !alerta.getUsuarioConfirmacion().getApellido().isEmpty()) {
                                usuarioAccionStr += " " + alerta.getUsuarioConfirmacion().getApellido();
                        }
                        if (usuarioAccionStr == null || usuarioAccionStr.isBlank()) {
                                usuarioAccionStr = alerta.getUsuarioConfirmacion().getEmail();
                        }
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
                                .build();
        }

        private AlertaResponse toResponse(Alerta alerta) {
                return AlertaResponse.builder()
                                .id(alerta.getId())
                                .tipo(alerta.getTipo())
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

                if (nuevoEstado == EstadoAlerta.CONFIRMADA) {
                        Intervencion intervencion = new Intervencion();
                        intervencion.setAlerta(actualizado);
                        intervencion.setAnimal(actualizado.getAnimal());
                        intervencion.setInterventor(usuario);
                        
                        String descripcion = (notas != null && !notas.trim().isEmpty()) ? notas.trim() : "Inseminación confirmada";
                        intervencion.setDescripcion(descripcion);
                        
                        intervencion.setFechaHora(LocalDateTime.now());
                        intervencionRepository.save(intervencion);
                }

                /*
                 * String ventanaOptima = null;
                 * if (actualizado.getVentanaInicioIns() != null &&
                 * actualizado.getVentanaFinIns() != null) {
                 * long horas = java.time.Duration.between(actualizado.getVentanaInicioIns(),
                 * actualizado.getVentanaFinIns()).toHours();
                 * ventanaOptima = horas + " horas";
                 * }
                 */

                return this.toResponse(actualizado);
        }
}
