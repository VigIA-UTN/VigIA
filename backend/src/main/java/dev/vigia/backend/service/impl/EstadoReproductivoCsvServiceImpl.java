package dev.vigia.backend.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

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
            if (headerRow == null || headerRow.getPhysicalNumberOfCells() < 2) {
                throw new IllegalArgumentException("El archivo no tiene las columnas requeridas (IdCaravana, Estado)");
            }

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                
                procesadosWrapper[0]++;
                String[] cols = new String[4];
                
                for(int c = 0; c < 4; c++) {
                    Cell cell = row.getCell(c);
                    cols[c] = getCellValueAsString(cell);
                }
                
                try {
                    EstadoReproductivoCsvRequest request = parseRow(cols);
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
            if (headers.length < 2) {
                throw new IllegalArgumentException("El archivo no tiene las columnas requeridas (IdCaravana, Estado)");
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
                    validos.add(request);
                } catch (Exception e) {
                    invalidos.add(new FilaEstadoInvalida(rowNumber, e.getMessage()));
                }
            }
        }
    }

    private EstadoReproductivoCsvRequest parseRow(String[] cols) {
        if (cols.length < 2) {
            throw new IllegalArgumentException("Faltan columnas obligatorias (IdCaravana, Estado)");
        }
        
        String idCaravana = cols[0].trim();
        String estadoStr = cols[1].trim();
        
        if (idCaravana.isEmpty()) {
            throw new IllegalArgumentException("IdCaravana es obligatorio");
        }
        if (estadoStr.isEmpty()) {
            throw new IllegalArgumentException("Estado es obligatorio");
        }
        
        TipoReproductivo estado;
        try {
            estado = TipoReproductivo.valueOf(estadoStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado inválido. Valores permitidos: VACIA, GESTANTE, CELO, ANESTRO");
        }
        
        LocalDate fechaParto = null;
        if (cols.length > 2 && !cols[2].trim().isEmpty()) {
            fechaParto = parseDate(cols[2].trim(), "FechaUltimoParto");
        }
        
        LocalDate fechaInseminacion = null;
        if (cols.length > 3 && !cols[3].trim().isEmpty()) {
            fechaInseminacion = parseDate(cols[3].trim(), "FechaUltimaInseminacion");
        }
        
        if (fechaParto != null && fechaInseminacion != null) {
            if (fechaInseminacion.isBefore(fechaParto)) {
                throw new IllegalArgumentException("FechaUltimaInseminacion (" + fechaInseminacion + ") no puede ser anterior a FechaUltimoParto (" + fechaParto + ")");
            }
        }
        
        return EstadoReproductivoCsvRequest.builder()
                .idCaravanaOficial(idCaravana)
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
