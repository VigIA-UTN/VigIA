package dev.vigia.backend.service.impl;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
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
    @Transactional
    @SuppressWarnings("null")
    public CargaMasivaResult procesarCargaMasiva(MultipartFile file, Long establecimientoId, String email)
            throws IOException, IllegalArgumentException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("El archivo excede el tamaño máximo permitido (5MB)");
        }

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

        Map<String, RodeoLote> rodeosActivos = rodeoLoteRepository.findByEstablecimientoId(establecimientoId).stream()
                .filter(RodeoLote::isActivo)
                .collect(Collectors.toMap(r -> normalizarNombre(r.getNombre()), Function.identity(), (a, b) -> a));

        List<Animal> animals = new ArrayList<>();
        int procesados = 0;
        int fallidos = 0;
        Set<String> caravanasEnArchivo = new HashSet<>();
        Set<String> aliasEnArchivo = new HashSet<>();
        List<String> errores = new ArrayList<>();

        for (int i = 0; i < parsedRows.size(); i++) {
            String[] data = parsedRows.get(i);
            if (esFilaVacia(data)) {
                continue;
            }
            int fila = i + 2;
            procesados++;
            try {
                String idCaravana = valor(data, 0);
                if (idCaravana == null) {
                    throw new IllegalArgumentException("El IdCaravana es obligatorio.");
                }
                if (!caravanasEnArchivo.add(idCaravana)) {
                    throw new IllegalArgumentException("ID Caravana duplicado en el archivo: " + idCaravana);
                }
                if (animalRepository.existsByIdCaravanaOficialAndEstablecimientoId(idCaravana, establecimientoId)) {
                    throw new IllegalArgumentException("El ID Caravana " + idCaravana + " ya existe en el establecimiento.");
                }

                String alias = valor(data, 1);
                if (alias != null) {
                    if (!aliasEnArchivo.add(alias)) {
                        throw new IllegalArgumentException("Alias duplicado en el archivo: " + alias);
                    }
                    if (animalRepository.existsByAliasAndEstablecimientoId(alias, establecimientoId)) {
                        throw new IllegalArgumentException("El Alias " + alias + " ya existe en el establecimiento.");
                    }
                }

                animals.add(createAnimalFromData(data, establecimientoSeleccionado, rodeosActivos));
            } catch (Exception e) {
                fallidos++;
                errores.add("Fila " + fila + ": " + e.getMessage());
            }
        }

        if (fallidos > 0) {
            try {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            } catch (org.springframework.transaction.NoTransactionException e) {

            }
            animals.clear();
        }

        List<Animal> saveAnimals = animalRepository.saveAll(animals);

        return CargaMasivaResult.builder()
                .procesados(procesados)
                .fallidos(fallidos)
                .animalesExitosos(saveAnimals)
                .errores(errores)
                .build();
    }

    private Animal createAnimalFromData(String[] data, Establecimiento establecimiento,
            Map<String, RodeoLote> rodeosActivos) {
        String fechaStr = valor(data, 2);
        if (fechaStr == null) {
            throw new IllegalArgumentException("La fecha de nacimiento es obligatoria.");
        }
        LocalDate fecha;
        try {
            fecha = LocalDate.parse(fechaStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Formato de fecha inválido. Use YYYY-MM-DD.");
        }
        if (fecha.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de nacimiento no puede ser futura.");
        }

        String nombreRodeoLote = valor(data, 4);
        if (nombreRodeoLote == null) {
            throw new IllegalArgumentException("El rodeo/lote es obligatorio.");
        }
        RodeoLote rodeo = rodeosActivos.get(normalizarNombre(nombreRodeoLote));
        if (rodeo == null) {
            throw new IllegalArgumentException("El rodeo/lote '" + nombreRodeoLote
                    + "' no existe o no está activo en el establecimiento.");
        }

        Animal animal = new Animal(establecimiento, rodeo);
        animal.setIdCaravanaOficial(valor(data, 0));
        animal.setAlias(valor(data, 1));
        animal.setFechaNacimiento(fecha);
        animal.setRaza(valor(data, 3));
        animal.setEstadoActual(EstadoAnimal.ACTIVO);
        return animal;
    }

    private static String valor(String[] data, int index) {
        if (data == null || data.length <= index || data[index] == null) {
            return null;
        }
        String v = data[index].trim();
        return v.isEmpty() ? null : v;
    }

    private static boolean esFilaVacia(String[] data) {
        for (int i = 0; i < (data == null ? 0 : data.length); i++) {
            if (valor(data, i) != null) {
                return false;
            }
        }
        return true;
    }

    private static String normalizarNombre(String nombre) {
        return nombre == null ? "" : nombre.trim().toLowerCase();
    }
}
