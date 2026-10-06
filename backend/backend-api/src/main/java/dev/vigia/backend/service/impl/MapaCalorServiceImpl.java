package dev.vigia.backend.service.impl;

import dev.vigia.backend.client.MlGeoClient;
import dev.vigia.backend.dto.ml.geo.CoordenadaGPSDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.MapaCalorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MapaCalorServiceImpl implements MapaCalorService {

        private final TelemetriaRepository telemetriaRepository;
        private final EstablecimientoRepository establecimientoRepository;
        private final AnimalRepository animalRepository;
        private final UsuarioRepository usuarioRepository;
        private final MlGeoClient mlGeoClient;

        @Override
        @SuppressWarnings("null")
        public GeoAnalyzeResponseDTO obtenerMapaCalor(String emailUsuario, Long establecimientoId, LocalDateTime desde,
                        LocalDateTime hasta, Long animalId, Long rodeoId) {

                if (animalId != null && rodeoId != null) {
                        throw new IllegalArgumentException(
                                        "Solo se puede filtrar por animal o por rodeo, no por ambos.");
                }
                if (desde.isAfter(hasta)) {
                        throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
                }

                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                if (!usuario.getRol().equals(Rol.ADMINISTRADOR)) {
                        List<Establecimiento> establecimientos = establecimientoRepository
                                        .findActiveEstablecimientoByUsuarioEmail(emailUsuario);
                        boolean pertenece = establecimientos.stream()
                                        .anyMatch(e -> e.getId().equals(establecimientoId));
                        if (!pertenece) {
                                throw new AccessDeniedException(
                                                "No tiene permisos para ver el mapa de calor de este establecimiento.");
                        }
                }

                Establecimiento establecimiento = establecimientoRepository.findById(establecimientoId)
                                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

                List<Telemetria> telemetriaList;
                if (rodeoId != null) {
                        List<Long> animalIds = animalRepository.findByRodeoLoteId(rodeoId).stream()
                                        .filter(a -> a.getEstablecimiento() != null && establecimientoId
                                                        .equals(a.getEstablecimiento().getId()))
                                        .map(Animal::getId)
                                        .toList();
                        if (animalIds.isEmpty()) {
                                return GeoAnalyzeResponseDTO.builder()
                                                .mapaCalor(new ArrayList<>())
                                                .alertas(new ArrayList<>())
                                                .build();
                        }
                        telemetriaList = telemetriaRepository.findByEstablecimientoAndAnimalesAndFechaHoraBetween(
                                        establecimientoId, desde, hasta, animalIds);
                } else if (animalId != null) {
                        Animal animal = animalRepository.findById(animalId)
                                        .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado"));
                        
                        if (animal.getRodeoLote() != null) {
                                List<Long> animalIds = animalRepository.findByRodeoLoteId(animal.getRodeoLote().getId()).stream()
                                                .filter(a -> a.getEstablecimiento() != null && establecimientoId
                                                                .equals(a.getEstablecimiento().getId()))
                                                .map(Animal::getId)
                                                .toList();
                                telemetriaList = telemetriaRepository.findByEstablecimientoAndAnimalesAndFechaHoraBetween(
                                                establecimientoId, desde, hasta, animalIds);
                        } else {
                                telemetriaList = telemetriaRepository
                                                .findByEstablecimientoAndFechaHoraBetween(establecimientoId, desde, hasta, null);
                        }
                } else {
                        telemetriaList = telemetriaRepository
                                        .findByEstablecimientoAndFechaHoraBetween(establecimientoId, desde, hasta, null);
                }

                List<CoordenadaGPSDTO> coordenadas = telemetriaList.stream()
                                .filter(t -> t.getLatitud() != null && t.getLongitud() != null)
                                .filter(t -> t.getCaravana().getAnimal() != null)
                                .map(t -> CoordenadaGPSDTO.builder()
                                                .animalId(t.getCaravana().getAnimal().getId().toString())
                                                .lat(t.getLatitud())
                                                .lng(t.getLongitud())
                                                .timestamp(t.getFechaHora().toString())
                                                .build())
                                .collect(Collectors.toList());

                GeoAnalyzeRequestDTO request = GeoAnalyzeRequestDTO.builder()
                                .animalId(animalId != null ? animalId.toString() : null)
                                .historial7d(coordenadas)
                                .umbralAislamientoM(establecimiento.getUmbralAislamientoM())
                                .build();

                return mlGeoClient.analyze(request);
        }
}
