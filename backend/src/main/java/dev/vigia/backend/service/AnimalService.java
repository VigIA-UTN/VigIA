package dev.vigia.backend.service;

import java.util.List;

import dev.vigia.backend.dto.AnimalRequest;
import dev.vigia.backend.dto.AnimalResponse;

public interface AnimalService {
    AnimalResponse cargarAnimal(String emailProductor, AnimalRequest request);

    void desactivarAnimal(Long animalId, String emailProductor);

    AnimalResponse getAnimalById(Long animalId, String emailProductor);

    List<AnimalResponse> getAllAnimales();

    List<AnimalResponse> listarAnimales(String emailUsuario);
}
