package dev.vigia.backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.vigia.backend.dto.AnimalRequest;
import dev.vigia.backend.dto.AnimalResponse;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.RodeoRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.service.AnimalService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnimalSerciveImpl implements AnimalService {

        private final AnimalRepository animalRepository;
        private final UsuarioRepository usuarioRepository;
        private final EstablecimientoRepository establecimientoRepository;
        private final RodeoRepository rodeoLoteRepository;

        @Override
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

                if (request.getRodeoLoteId() != null) {
                        rodeoLoteRepository.findById(request.getRodeoLoteId())
                                        .orElseThrow(() -> new IllegalArgumentException("Rodeo lote no encontrado."));
                }

                Animal animal = new Animal(
                                establecimiento,
                                request.getRodeoLoteId() != null
                                                ? rodeoLoteRepository.findById(request.getRodeoLoteId()).get()
                                                : null);

                animal.setAlias(request.getAlias());
                animal.setFechaNacimiento(request.getFechaNacimiento());
                animal.setRaza(request.getRaza());
                animal.setEstadoActual(request.getEstadoActual());

                Animal savedAnimal = animalRepository.save(animal);

                return mapToResponse(savedAnimal);
        }

        @Override
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

        @Override
        public List<AnimalResponse> getAnimalesByEstablecimiento(Long establecimientoId, String emailUsuario) {
                Usuario productorUser = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                Establecimiento establecimiento = establecimientoRepository.findById(establecimientoId)
                                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

                boolean esProductorOEmpleadoDelEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!esProductorOEmpleadoDelEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                List<Animal> animales = animalRepository.findByEstablecimientoId(establecimiento.getId());
                return animales.stream().map(this::mapToResponse).toList();
        }

        @Override
        public List<AnimalResponse> getAnimalesByRodeoLote(Long rodeoLoteId, String emailUsuario) {
                Usuario productorUser = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                RodeoLote rodeoLote = rodeoLoteRepository.findById(rodeoLoteId)
                                .orElseThrow(() -> new IllegalArgumentException("Rodeo lote no encontrado."));

                Establecimiento establecimiento = rodeoLote.getEstablecimiento();

                boolean esProductorOEmpleadoDelEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!esProductorOEmpleadoDelEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                List<Animal> animales = animalRepository.findByRodeoLoteId(rodeoLoteId);
                return animales.stream().map(this::mapToResponse).toList();
        }

        private AnimalResponse mapToResponse(Animal animal) {
                return AnimalResponse.builder()
                                .id(animal.getId())
                                .alias(animal.getAlias())
                                .fechaNacimiento(animal.getFechaNacimiento())
                                .raza(animal.getRaza())
                                .estadoActual(animal.getEstadoActual())
                                .fechaAlta(animal.getFechaAlta())
                                .build();
        }

}
