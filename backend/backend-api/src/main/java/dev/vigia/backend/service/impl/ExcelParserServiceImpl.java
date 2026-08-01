package dev.vigia.backend.service.impl;

import dev.vigia.backend.service.FileParserService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class ExcelParserServiceImpl implements FileParserService {

    @Override
    public boolean supports(String filename) {
        return filename != null
                && (filename.toLowerCase().endsWith(".xlsx") || filename.toLowerCase().endsWith(".xls"));
    }

    @Override
    public List<String[]> parse(MultipartFile file) throws IOException {
        List<String[]> rowsData = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // Validar las columnas en la primera fila (encabezado o datos)
            Row firstRow = sheet.getRow(0);
            if (firstRow == null || firstRow.getLastCellNum() < 5) {
                // If row 0 is null, try row 1
                Row secondRow = sheet.getRow(1);
                if (secondRow == null || secondRow.getLastCellNum() < 5) {
                    throw new IllegalArgumentException("El archivo Excel debe tener al menos 5 columnas.");
                }
            }
            
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                if (row.getLastCellNum() < 5) {
                    throw new IllegalArgumentException("Todas las filas del archivo Excel deben tener al menos 5 columnas.");
                }

                String[] data = new String[5];
                for (int i = 0; i < 5; i++) {
                    data[i] = getStringCellValue(row.getCell(i));
                }
                rowsData.add(data);
            }
        }
        return rowsData;
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
        return new SimpleDateFormat("yyyy-MM-dd").format(date);
    }
}
