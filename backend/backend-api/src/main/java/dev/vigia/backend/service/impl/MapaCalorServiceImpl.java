package dev.vigia.backend.service.impl;

import dev.vigia.backend.client.MlGeoClient;
import dev.vigia.backend.dto.ml.geo.AnimalAlejadoDTO;
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
import java.util.Map;
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
                                                .animalesAlejados(new ArrayList<>())
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

                GeoAnalyzeResponseDTO mlGeoResponse = mlGeoClient.analyze(request);

                // Enriquecer la lista de animales alejados: resolvemos los IDs que
                // ml-geo marcó con alerta_aislamiento=true a sus datos de presentación.
                List<AnimalAlejadoDTO> animalesAlejados = resolverAnimalesAlejados(mlGeoResponse);
                mlGeoResponse.setAnimalesAlejados(animalesAlejados);

                return mlGeoResponse;
        }

        /**
         * Filtra las alertas de ml-geo con alerta_aislamiento=true, busca los animales
         * correspondientes en la base de datos y los convierte a {@link AnimalAlejadoDTO}
         * con caravana oficial y alias resueltos.
         *
         * <p>Se usa una única consulta por IDs (en lugar de N consultas) para evitar el
         * problema N+1. Los IDs que ml-geo no encuentre en la BD se ignoran silenciosamente.
         */
        private List<AnimalAlejadoDTO> resolverAnimalesAlejados(GeoAnalyzeResponseDTO mlGeoResponse) {
                if (mlGeoResponse == null || mlGeoResponse.getAlertas() == null) {
                        return new ArrayList<>();
                }

                List<Long> idsAlejados = mlGeoResponse.getAlertas().stream()
                                .filter(a -> Boolean.TRUE.equals(a.getAlertaAislamiento()))
                                .map(a -> {
                                        try { return Long.parseLong(a.getAnimalId()); }
                                        catch (NumberFormatException e) { return null; }
                                })
                                .filter(id -> id != null)
                                .collect(Collectors.toList());

                if (idsAlejados.isEmpty()) {
                        return new ArrayList<>();
                }

                // Una sola query para todos los IDs alejados
                Map<Long, Animal> animalesPorId = animalRepository.findAllById(idsAlejados).stream()
                                .collect(Collectors.toMap(Animal::getId, a -> a));

                return idsAlejados.stream()
                                .filter(animalesPorId::containsKey)
                                .map(id -> {
                                        Animal a = animalesPorId.get(id);
                                        return AnimalAlejadoDTO.builder()
                                                        .animalId(a.getId())
                                                        .caravana(a.getIdCaravanaOficial())
                                                        .alias(a.getAlias())
                                                        .build();
                                })
                                .collect(Collectors.toList());
        }
}
