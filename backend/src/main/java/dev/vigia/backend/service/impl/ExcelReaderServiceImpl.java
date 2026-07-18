package dev.vigia.backend.service.impl;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.service.ExcelReaderService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExcelReaderServiceImpl implements ExcelReaderService {

    private final AnimalRepository animalRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final RodeoLoteRepository rodeoLoteRepository;

    @Override
    public List<Animal> leerYGuardarAnimalesExcel(MultipartFile file, String email) throws IOException {

        // Validar que sea un archivo Excel
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".xlsx")
                && !filename.toLowerCase().endsWith(".xls"))) {
            throw new IllegalArgumentException("El archivo debe ser un Excel (.xlsx o .xls)");
        }

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {

            Sheet sheet = workbook.getSheetAt(0);
            List<Animal> animals = new ArrayList<>();

            // Iterar sobre las filas (empezar en 1 para saltar encabezados)
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                // Crear animal desde la fila
                Animal animal = createAnimalFromRow(row, email);

                // Solo agregar si tiene información mínima
                if (animal.getIdCaravanaOficial() != null && !animal.getIdCaravanaOficial().isEmpty()) {
                    animals.add(animal);
                }
            }

            List<Animal> saveAnimals = animalRepository.saveAll(animals);
            return saveAnimals;
        }
    }

    private Animal createAnimalFromRow(Row row, String email) {
        Animal animal = new Animal();

        // Columna 0: IdCaravana
        animal.setIdCaravanaOficial(getStringCellValue(row.getCell(0)));

        // Columna 1: Alias
        animal.setAlias(getStringCellValue(row.getCell(1)));

        // Columna 2: FechaNacimiento (convertir a String)
        animal.setFechaNacimiento(LocalDate.parse(getFormattedDateCellValue(row.getCell(2))));

        // Columna 3: Raza
        animal.setRaza(getStringCellValue(row.getCell(3)));

        String nombreEstablecimiento = getStringCellValue(row.getCell(4));
        String nombreRodeoLote = getStringCellValue(row.getCell(5));

        List<Establecimiento> establecimientos = establecimientoRepository
                .findActiveEstablecimientoByUsuarioEmail(email);

        Establecimiento establecimiento = establecimientos.stream()
                .filter(e -> e.getNombre().equalsIgnoreCase(nombreEstablecimiento))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

        animal.setEstablecimiento(establecimiento);

        List<RodeoLote> rodeos = rodeoLoteRepository.findByEstablecimientoId(establecimiento.getId());

        RodeoLote rodeo = rodeos.stream()
                .filter(e -> e.getNombre().equalsIgnoreCase(nombreRodeoLote))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Rodeo lote no encontrado."));

        animal.setRodeoLote(rodeo);

        animal.setEstadoActual(EstadoAnimal.ACTIVO);

        return animal;
    }

    private String getStringCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();

            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return formatDate(cell.getDateCellValue());
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }

            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());

            case FORMULA:
                return evaluateFormulaCell(cell);

            default:
                return null;
        }
    }

    private String getFormattedDateCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        if (DateUtil.isCellDateFormatted(cell)) {
            return formatDate(cell.getDateCellValue());
        } else {
            // Intentar convertir número a fecha si no está formateado
            try {
                double numericValue = cell.getNumericCellValue();
                Date date = DateUtil.getJavaDate(numericValue);
                return formatDate(date);
            } catch (Exception e) {
                return String.valueOf((long) cell.getNumericCellValue());
            }
        }
    }

    private String evaluateFormulaCell(Cell cell) {
        try {
            if (cell.getCachedFormulaResultType() == CellType.STRING) {
                return cell.getStringCellValue();
            } else if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return formatDate(cell.getDateCellValue());
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
    }
}
