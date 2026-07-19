package dev.vigia.backend.service.impl;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import dev.vigia.backend.dto.CargaMasivaResult;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.service.CargaMasivaService;
import dev.vigia.backend.service.FileParserService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CargaMasivaServiceImpl implements CargaMasivaService {

    private final AnimalRepository animalRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final RodeoLoteRepository rodeoLoteRepository;
    private final List<FileParserService> parsers;

    @Override
    @SuppressWarnings("null")
    public CargaMasivaResult procesarCargaMasiva(MultipartFile file, Long establecimientoId, String email)
            throws IOException, IllegalArgumentException {

        Establecimiento establecimientoSeleccionado = establecimientoRepository.findById(establecimientoId)
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

        boolean activoEnEstablecimiento = establecimientoSeleccionado.getEmpleados().stream()
                .anyMatch(v -> v.getUsuario().getEmail().equals(email) && v.isActivo());

        if (!activoEnEstablecimiento) {
            throw new IllegalArgumentException(
                    "El productor no pertenece al establecimiento destino o no está activo.");
        }

        String filename = file.getOriginalFilename();
        FileParserService selectedParser = parsers.stream()
                .filter(parser -> parser.supports(filename))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El archivo debe ser un Excel (.xlsx, .xls) o CSV (.csv)"));

        List<String[]> parsedRows = selectedParser.parse(file);

        List<Animal> animals = new ArrayList<>();
        int procesados = 0;
        int fallidos = 0;

        for (String[] data : parsedRows) {
            procesados++;
            try {
                Animal animal = createAnimalFromData(data, establecimientoSeleccionado);
                if (animal.getEstablecimiento() != null) {
                    animals.add(animal);
                } else {
                    fallidos++;
                }
            } catch (Exception e) {
                fallidos++;
            }
        }

        List<Animal> saveAnimals = animalRepository.saveAll(animals);

        return CargaMasivaResult.builder()
                .procesados(procesados)
                .fallidos(fallidos)
                .animalesExitosos(saveAnimals)
                .build();
    }

    private Animal createAnimalFromData(String[] data, Establecimiento establecimiento) {
        Animal animal = new Animal(establecimiento, null);

        String idCaravana = data.length > 0 ? data[0] : null;
        if (idCaravana != null)
            idCaravana = idCaravana.trim();
        animal.setIdCaravanaOficial(idCaravana);

        String alias = data.length > 1 ? data[1] : null;
        if (alias != null)
            alias = alias.trim();
        animal.setAlias(alias);

        if ((idCaravana == null || idCaravana.isEmpty()) && (alias == null || alias.isEmpty())) {
            throw new IllegalArgumentException("El animal debe tener al menos un IdCaravana o un Alias.");
        }

        String fechaStr = data.length > 2 ? data[2] : null;
        if (fechaStr != null && !fechaStr.trim().isEmpty()) {
            try {
                LocalDate fecha = LocalDate.parse(fechaStr.trim());
                animal.setFechaNacimiento(fecha);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Formato de fecha inválido. Use YYYY-MM-DD.");
            }
        }

        String raza = data.length > 3 ? data[3] : null;
        if (raza != null)
            raza = raza.trim();
        animal.setRaza(raza);

        String nombreRodeoLote = data.length > 4 ? data[4] : null;

        RodeoLote rodeo = null;

        if (nombreRodeoLote != null && !nombreRodeoLote.trim().isEmpty()) {
            List<RodeoLote> rodeos = rodeoLoteRepository.findByEstablecimientoId(establecimiento.getId());
            Optional<RodeoLote> optRodeo = rodeos.stream()
                    .filter(e -> e.getNombre().equalsIgnoreCase(nombreRodeoLote.trim()))
                    .findFirst();

            if (optRodeo.isPresent()) {
                rodeo = optRodeo.get();
            } else {
                rodeo = new RodeoLote(establecimiento);
                rodeo.setNombre(nombreRodeoLote.trim());
                rodeo = rodeoLoteRepository.save(rodeo);
            }
        }
        animal.setRodeoLote(rodeo);

        animal.setEstadoActual(EstadoAnimal.ACTIVO);

        return animal;
    }
}
