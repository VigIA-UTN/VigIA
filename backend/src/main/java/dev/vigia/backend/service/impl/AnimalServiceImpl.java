package dev.vigia.backend.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.vigia.backend.dto.AnimalRequest;
import dev.vigia.backend.dto.AnimalResponse;
import dev.vigia.backend.dto.EstadoReproductivoRequest;
import dev.vigia.backend.dto.EstadoReproductivoResponse;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.OrigenEstado;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.AnimalService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnimalServiceImpl implements AnimalService {

        private final AnimalRepository animalRepository;
        private final UsuarioRepository usuarioRepository;
        private final EstablecimientoRepository establecimientoRepository;
        private final RodeoLoteRepository rodeoLoteRepository;
        private final EstadoReproductivoRepository estadoReproductivoRepository;

        @Override
        @SuppressWarnings("null")
        public AnimalResponse cargarAnimal(String emailProductor, AnimalRequest request) {
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

                if (animalRepository.existsByIdCaravanaOficialAndEstablecimientoId(request.getIdCaravanaOficial(),
                                request.getEstablecimientoId())) {
                        throw new IllegalArgumentException(
                                        "El identificador ya se encuentra registrado en este establecimiento.");
                }

                if (request.getRodeoLoteId() != null) {
                        rodeoLoteRepository.findById(request.getRodeoLoteId())
                                        .orElseThrow(() -> new IllegalArgumentException("Rodeo lote no encontrado."));
                }

                Animal animal = new Animal(
                                establecimiento,
                                request.getRodeoLoteId() != null
                                                ? rodeoLoteRepository.findById(request.getRodeoLoteId()).get()
                                                : null);

                animal.setIdCaravanaOficial(request.getIdCaravanaOficial());
                animal.setAlias(request.getAlias());
                animal.setFechaNacimiento(request.getFechaNacimiento());
                animal.setRaza(request.getRaza());
                animal.setEstadoActual(request.getEstadoActual());

                Animal savedAnimal = animalRepository.save(animal);

                return mapToResponse(savedAnimal);
        }

        @Override
        @SuppressWarnings("null")
        public void desactivarAnimal(Long animalId, String emailProductor) {
                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean esProductorOEmpleadoDelEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!esProductorOEmpleadoDelEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                if (animal.getEstadoActual().equals(EstadoAnimal.INACTIVO)) {
                        throw new IllegalArgumentException("El animal no está activo.");
                }

                animal.setEstadoActual(EstadoAnimal.INACTIVO);
                animalRepository.save(animal);
        }

        @Override
        @SuppressWarnings("null")
        public AnimalResponse getAnimalById(Long animalId, String emailProductor) {
                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));
                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean esProductorOEmpleadoDelEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!esProductorOEmpleadoDelEstablecimiento && !productorUser.getRol().equals(Rol.ADMINISTRADOR)) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                return mapToResponse(animal);
        }

        @Override
        public List<AnimalResponse> getAllAnimales() {
                List<Animal> animales = animalRepository.findAll();
                return animales.stream().map(this::mapToResponse).toList();
        }

        private AnimalResponse mapToResponse(Animal animal) {
                EstadoReproductivo ultimoEstado = animal.getEstadosReproductivos().stream()
                                .findFirst()
                                .orElse(null);

                return AnimalResponse.builder()
                                .id(animal.getId())
                                .idCaravanaOficial(animal.getIdCaravanaOficial())
                                .alias(animal.getAlias())
                                .fechaNacimiento(animal.getFechaNacimiento())
                                .raza(animal.getRaza())
                                .estadoActual(animal.getEstadoActual())
                                .rodeoLoteId(animal.getRodeoLote() != null ? animal.getRodeoLote().getId() : null)
                                .rodeoLoteNombre(animal.getRodeoLote() != null ? animal.getRodeoLote().getNombre()
                                                : null)
                                .establecimientoId(
                                                animal.getEstablecimiento() != null
                                                                ? animal.getEstablecimiento().getId()
                                                                : null)
                                .fechaAlta(animal.getFechaAlta())
                                .estadoReproductivo(ultimoEstado != null ? ultimoEstado.getEstado() : null)
                                .fechaUltimoParto(ultimoEstado != null ? ultimoEstado.getFechaUltParto() : null)
                                .fechaUltimaInseminacion(
                                                ultimoEstado != null ? ultimoEstado.getFechaUltInseminacion() : null)
                                .build();
        }

        @Override
        @SuppressWarnings("null")
        public List<AnimalResponse> listarAnimales(String emailUsuario) {

                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                if (usuario.getRol().equals(Rol.ADMINISTRADOR)) {
                        return animalRepository.findByEstadoActualNot(EstadoAnimal.INACTIVO)
                                        .stream()
                                        .map(this::mapToResponse)
                                        .collect(Collectors.toList());
                }

                List<Establecimiento> establecimientos = establecimientoRepository
                                .findActiveEstablecimientoByUsuarioEmail(emailUsuario);

                if (establecimientos.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "El usuario no pertenece a ningún establecimiento activo.");
                }

                List<Long> establecimientoIds = establecimientos.stream()
                                .map(Establecimiento::getId)
                                .collect(Collectors.toList());

                return animalRepository
                                .findByEstablecimientoIdInAndEstadoActualNot(establecimientoIds, EstadoAnimal.INACTIVO)
                                .stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        @SuppressWarnings("null")
        public EstadoReproductivoResponse registrarEstadoReproductivo(Long animalId, String emailProductor,
                        EstadoReproductivoRequest request) {
                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean productorActivoEnEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!productorActivoEnEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                if (animal.getEstadoActual().equals(EstadoAnimal.INACTIVO)) {
                        throw new IllegalArgumentException("El animal no está activo.");
                }

                LocalDate hoy = LocalDate.now();
                if (request.getFechaUltimoParto() != null && request.getFechaUltimoParto().isAfter(hoy)) {
                        throw new IllegalArgumentException("La fecha de último parto no puede ser futura.");
                }
                if (request.getFechaUltimaInseminacion() != null && request.getFechaUltimaInseminacion().isAfter(hoy)) {
                        throw new IllegalArgumentException("La fecha de última inseminación no puede ser futura.");
                }

                if (request.getFechaUltimoParto() != null && request.getFechaUltimaInseminacion() != null) {
                        if (request.getFechaUltimoParto().isAfter(request.getFechaUltimaInseminacion())) {
                                throw new IllegalArgumentException(
                                                "La fecha de último parto no puede ser posterior a la última inseminación.");
                        }
                }

                EstadoReproductivo estado = new EstadoReproductivo();
                estado.setAnimal(animal);
                estado.setUsuarioRegistrador(productorUser);
                estado.setEstado(request.getEstado());
                estado.setFechaUltParto(request.getFechaUltimoParto());
                estado.setFechaUltInseminacion(request.getFechaUltimaInseminacion());
                estado.setOrigen(OrigenEstado.MANUAL);
                estado.setFechaRegistro(LocalDateTime.now());

                EstadoReproductivo saved = estadoReproductivoRepository.save(estado);

                String registradoPor = productorUser.getNombre();
                if (productorUser.getApellido() != null && !productorUser.getApellido().isEmpty()) {
                        registradoPor += " " + productorUser.getApellido();
                } else if (registradoPor == null || registradoPor.isEmpty()) {
                        registradoPor = productorUser.getEmail();
                }

                return EstadoReproductivoResponse.builder()
                                .id(saved.getId())
                                .animalId(animal.getId())
                                .estado(saved.getEstado().name())
                                .fechaUltimoParto(saved.getFechaUltParto())
                                .fechaUltimaInseminacion(saved.getFechaUltInseminacion())
                                .origen(saved.getOrigen().name())
                                .fechaRegistro(saved.getFechaRegistro())
                                .registradoPor(registradoPor)
                                .build();
        }

        @Override
        @SuppressWarnings("null")
        public EstadoReproductivoResponse editarEstadoReproductivo(Long animalId, Long estadoId, String emailProductor,
                        EstadoReproductivoRequest request) {
                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean productorActivoEnEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!productorActivoEnEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                if (animal.getEstadoActual().equals(EstadoAnimal.INACTIVO)) {
                        throw new IllegalArgumentException("El animal no está activo.");
                }

                EstadoReproductivo estado = estadoReproductivoRepository.findById(estadoId)
                                .orElseThrow(() -> new IllegalArgumentException("Estado reproductivo no encontrado."));

                if (!estado.getAnimal().getId().equals(animalId)) {
                        throw new IllegalArgumentException("El estado reproductivo no pertenece a este animal.");
                }

                LocalDate hoy = LocalDate.now();
                if (request.getFechaUltimoParto() != null && request.getFechaUltimoParto().isAfter(hoy)) {
                        throw new IllegalArgumentException("La fecha de último parto no puede ser futura.");
                }
                if (request.getFechaUltimaInseminacion() != null && request.getFechaUltimaInseminacion().isAfter(hoy)) {
                        throw new IllegalArgumentException("La fecha de última inseminación no puede ser futura.");
                }

                if (request.getFechaUltimoParto() != null && request.getFechaUltimaInseminacion() != null) {
                        if (request.getFechaUltimoParto().isAfter(request.getFechaUltimaInseminacion())) {
                                throw new IllegalArgumentException(
                                                "La fecha de último parto no puede ser posterior a la última inseminación.");
                        }
                }

                estado.setEstado(request.getEstado());
                estado.setFechaUltParto(request.getFechaUltimoParto());
                estado.setFechaUltInseminacion(request.getFechaUltimaInseminacion());
                estado.setUsuarioRegistrador(productorUser);
                estado.setFechaRegistro(LocalDateTime.now()); // Update timestamp to reflect edit

                EstadoReproductivo saved = estadoReproductivoRepository.save(estado);

                String registradoPor = productorUser.getNombre();
                if (productorUser.getApellido() != null && !productorUser.getApellido().isEmpty()) {
                        registradoPor += " " + productorUser.getApellido();
                } else if (registradoPor == null || registradoPor.isEmpty()) {
                        registradoPor = productorUser.getEmail();
                }

                return EstadoReproductivoResponse.builder()
                                .id(saved.getId())
                                .animalId(animal.getId())
                                .estado(saved.getEstado().name())
                                .fechaUltimoParto(saved.getFechaUltParto())
                                .fechaUltimaInseminacion(saved.getFechaUltInseminacion())
                                .origen(saved.getOrigen().name())
                                .fechaRegistro(saved.getFechaRegistro())
                                .registradoPor(registradoPor)
                                .build();
        }

}
