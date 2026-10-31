package dev.vigia.backend.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import dev.vigia.backend.dto.EstadoReproductivoCsvRequest;
import dev.vigia.backend.dto.EstadoReproductivoParseResult;
import dev.vigia.backend.dto.FilaEstadoInvalida;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.service.EstadoReproductivoCsvService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstadoReproductivoCsvServiceImpl implements EstadoReproductivoCsvService {

    // POSIBLE_CELO lo registra solo la detección automática; el productor carga el celo confirmado (CELO).
    private static final Set<TipoReproductivo> ESTADOS_PERMITIDOS = EnumSet.complementOf(
            EnumSet.of(TipoReproductivo.POSIBLE_CELO));

    private static final String MENSAJE_ESTADO_INVALIDO = "Estado inválido. Valores permitidos: "
            + ESTADOS_PERMITIDOS.stream().map(Enum::name).collect(Collectors.joining(", "));

    @Override
    public EstadoReproductivoParseResult parseAndValidate(MultipartFile file) throws IOException, IllegalArgumentException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        
        String filename = file.getOriginalFilename();
        boolean isCsv = filename != null && filename.toLowerCase().endsWith(".csv");
        boolean isExcel = filename != null && (filename.toLowerCase().endsWith(".xlsx") || filename.toLowerCase().endsWith(".xls"));
        
        if (!isCsv && !isExcel) {
            throw new IllegalArgumentException("El archivo debe ser un CSV (.csv) o Excel (.xlsx, .xls)");
        }
        
        // 5MB limit is handled by Spring Boot multipart configuration, but we can double check here
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("El archivo excede el tamaño máximo permitido (5MB)");
        }

        List<EstadoReproductivoCsvRequest> validos = new ArrayList<>();
        List<FilaEstadoInvalida> invalidos = new ArrayList<>();
        int[] procesadosWrapper = {0};

        if (isExcel) {
            parseExcel(file, validos, invalidos, procesadosWrapper);
        } else {
            parseCsv(file, validos, invalidos, procesadosWrapper);
        }
        
        return EstadoReproductivoParseResult.builder()
                .procesados(procesadosWrapper[0])
                .validos(validos)
                .invalidos(invalidos)
                .build();
    }

    private void parseExcel(MultipartFile file, List<EstadoReproductivoCsvRequest> validos, List<FilaEstadoInvalida> invalidos, int[] procesadosWrapper) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            Row headerRow = sheet.getRow(0);
            if (headerRow == null || headerRow.getPhysicalNumberOfCells() < 3) {
                throw new IllegalArgumentException("El archivo no tiene las columnas requeridas (IdCaravana, Alias, Estado)");
            }

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                
                procesadosWrapper[0]++;
                String[] cols = new String[5];
                
                for(int c = 0; c < 5; c++) {
                    Cell cell = row.getCell(c);
                    cols[c] = getCellValueAsString(cell);
                }
                
                try {
                    EstadoReproductivoCsvRequest request = parseRow(cols);
                    request.setFila(rowIndex + 1);
                    validos.add(request);
                } catch (Exception e) {
                    invalidos.add(new FilaEstadoInvalida(rowIndex + 1, e.getMessage()));
                }
            }
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
            } else {
                return String.valueOf((long) cell.getNumericCellValue());
            }
        }
        return "";
    }

    private void parseCsv(MultipartFile file, List<EstadoReproductivoCsvRequest> validos, List<FilaEstadoInvalida> invalidos, int[] procesadosWrapper) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("El archivo no tiene encabezados");
            }
            
            String[] headers = headerLine.split(",");
            if (headers.length < 3) {
                throw new IllegalArgumentException("El archivo no tiene las columnas requeridas (IdCaravana, Alias, Estado)");
            }

            String line;
            int rowNumber = 1; 
            
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.trim().isEmpty()) {
                    continue; 
                }
                
                procesadosWrapper[0]++;
                String[] cols = line.split(",", -1);
                
                try {
                    EstadoReproductivoCsvRequest request = parseRow(cols);
                    request.setFila(rowNumber);
                    validos.add(request);
                } catch (Exception e) {
                    invalidos.add(new FilaEstadoInvalida(rowNumber, e.getMessage()));
                }
            }
        }
    }

    private EstadoReproductivoCsvRequest parseRow(String[] cols) {
        if (cols.length < 3) {
            throw new IllegalArgumentException("Faltan columnas obligatorias (IdCaravana, Alias, Estado)");
        }
        
        String idCaravana = cols[0].trim();
        String alias = cols[1].trim();
        String estadoStr = cols[2].trim();
        
        if (idCaravana.isEmpty() && alias.isEmpty()) {
            throw new IllegalArgumentException("IdCaravana o Alias son obligatorios");
        }
        
        if (estadoStr.isEmpty()) {
            throw new IllegalArgumentException("Estado es obligatorio");
        }
        
        TipoReproductivo estado;
        try {
            estado = TipoReproductivo.valueOf(estadoStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(MENSAJE_ESTADO_INVALIDO);
        }
        if (!ESTADOS_PERMITIDOS.contains(estado)) {
            throw new IllegalArgumentException(MENSAJE_ESTADO_INVALIDO);
        }
        
        LocalDate fechaParto = null;
        if (cols.length > 3 && !cols[3].trim().isEmpty()) {
            fechaParto = parseDate(cols[3].trim(), "FechaUltimoParto");
        }
        
        LocalDate fechaInseminacion = null;
        if (cols.length > 4 && !cols[4].trim().isEmpty()) {
            fechaInseminacion = parseDate(cols[4].trim(), "FechaUltimaInseminacion");
        }
        

        
        return EstadoReproductivoCsvRequest.builder()
                .idCaravana(idCaravana)
                .alias(alias)
                .estado(estado)
                .fechaUltimoParto(fechaParto)
                .fechaUltimaInseminacion(fechaInseminacion)
                .build();
    }
    
    private LocalDate parseDate(String dateStr, String fieldName) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            if (date.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException(fieldName + " no puede ser una fecha futura");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + " tiene formato inválido. Debe ser AAAA-MM-DD");
        }
    }
}
