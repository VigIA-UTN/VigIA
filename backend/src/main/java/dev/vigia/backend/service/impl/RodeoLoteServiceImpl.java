package dev.vigia.backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.vigia.backend.dto.RodeoLoteRequest;
import dev.vigia.backend.dto.RodeoLoteResponse;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.service.RodeoLoteService;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RodeoLoteServiceImpl implements RodeoLoteService {

        private final RodeoLoteRepository rodeoLoteRepository;
        private final EstablecimientoRepository establecimientoRepository;
        private final UsuarioRepository usuarioRepository;

        @Override
        @SuppressWarnings("null")
        public RodeoLoteResponse generarRodeoLote(RodeoLoteRequest request, String emailProductor) {

                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                Establecimiento establecimiento = establecimientoRepository.findById(request.getEstablecimientoId())
                                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

                boolean productorActivoEnEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!productorActivoEnEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                RodeoLote rodeoLote = new RodeoLote(establecimiento);
                rodeoLote.setNombre(request.getNombre());
                RodeoLote savedRodeo = rodeoLoteRepository.save(rodeoLote);
                return mapToResponse(savedRodeo);
        }

        private RodeoLoteResponse mapToResponse(RodeoLote rodeoLote) {
                return RodeoLoteResponse.builder()
                                .id(rodeoLote.getId())
                                .nombre(rodeoLote.getNombre())
                                .establecimientoId(rodeoLote.getEstablecimiento().getId())
                                .activo(rodeoLote.isActivo())
                                .fechaAlta(rodeoLote.getFechaAlta())
                                .build();
        }

        @Override
        @SuppressWarnings("null")
        public void desactivarRodeoLote(Long rodeoLoteId, String emailProductor) {

                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                RodeoLote rodeoLote = rodeoLoteRepository.findById(rodeoLoteId)
                                .orElseThrow(() -> new IllegalArgumentException("Rodeo no encontrado."));

                boolean productorActivoEnEstablecimiento = rodeoLote.getEstablecimiento().getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!productorActivoEnEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                rodeoLote.setActivo(false);
                rodeoLoteRepository.save(rodeoLote);
        }

        @Override
        @SuppressWarnings("null")
        public RodeoLoteResponse getRodeoLoteById(Long id, String emailProductor) {

                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                RodeoLote rodeoLote = rodeoLoteRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Rodeo no encontrado."));

                boolean productorActivoEnEstablecimiento = rodeoLote.getEstablecimiento().getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!productorActivoEnEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                return mapToResponse(rodeoLote);
        }

        @Override
        public List<RodeoLoteResponse> getAllRodeoLotes() {
                List<RodeoLote> rodeoLotes = rodeoLoteRepository.findAll();
                return rodeoLotes.stream()
                                .map(this::mapToResponse)
                                .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<RodeoLoteResponse> getRodeoLotesByUsuario(String emailProductor) {
                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                List<RodeoLote> rodeoLotes = rodeoLoteRepository.findByEstablecimiento_Empleados_Usuario(productorUser);
                return rodeoLotes.stream()
                                .map(this::mapToResponse)
                                .collect(java.util.stream.Collectors.toList());
        }

}
