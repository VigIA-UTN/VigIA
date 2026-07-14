package dev.vigia.backend.service;

import java.util.List;

import dev.vigia.backend.dto.AnimalRequest;
import dev.vigia.backend.dto.AnimalResponse;
import dev.vigia.backend.dto.EstadoReproductivoRequest;
import dev.vigia.backend.dto.EstadoReproductivoResponse;

public interface AnimalService {
    AnimalResponse cargarAnimal(String emailProductor, AnimalRequest request);

    void desactivarAnimal(Long animalId, String emailProductor);

    AnimalResponse getAnimalById(Long animalId, String emailProductor);

    List<AnimalResponse> getAllAnimales();

    List<AnimalResponse> listarAnimales(String emailUsuario);

    EstadoReproductivoResponse registrarEstadoReproductivo(Long animalId, String emailProductor,
            EstadoReproductivoRequest request);

    EstadoReproductivoResponse editarEstadoReproductivo(Long animalId, Long estadoId, String emailProductor,
            EstadoReproductivoRequest request);
}
