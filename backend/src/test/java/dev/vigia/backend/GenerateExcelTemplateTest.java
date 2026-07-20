package dev.vigia.backend;

import org.junit.jupiter.api.Test;
import java.io.FileOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class GenerateExcelTemplateTest {
    @Test
    public void generateTemplate() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Animales");
        
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("IdCaravana");
        header.createCell(1).setCellValue("Alias");
        header.createCell(2).setCellValue("FechaNacimiento");
        header.createCell(3).setCellValue("Raza");
        header.createCell(4).setCellValue("RodeoLote");
        
        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("123");
        row1.createCell(1).setCellValue("Vaquita");
        row1.createCell(2).setCellValue("2023-01-15");
        row1.createCell(3).setCellValue("Angus");
        row1.createCell(4).setCellValue("Lote 1");
        
        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("456");
        row2.createCell(1).setCellValue("Toro");
        row2.createCell(2).setCellValue("");
        row2.createCell(3).setCellValue("Hereford");
        row2.createCell(4).setCellValue("Lote Principal");
        
        Row row3 = sheet.createRow(3);
        row3.createCell(0).setCellValue("");
        row3.createCell(1).setCellValue("Lola");
        row3.createCell(2).setCellValue("");
        row3.createCell(3).setCellValue("");
        row3.createCell(4).setCellValue("");
        
        try (FileOutputStream fileOut = new FileOutputStream("../frontend/public/template_animales.xlsx")) {
            workbook.write(fileOut);
        }
        workbook.close();
    }

    @Test
    public void generateTemplateEstados() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Estados");
        
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("IdCaravana");
        header.createCell(1).setCellValue("Alias");
        header.createCell(2).setCellValue("Estado");
        header.createCell(3).setCellValue("FechaUltimoParto");
        header.createCell(4).setCellValue("FechaUltimaInseminacion");
        
        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("123");
        row1.createCell(1).setCellValue("Vaquita");
        row1.createCell(2).setCellValue("GESTANTE");
        row1.createCell(3).setCellValue("2023-05-10");
        row1.createCell(4).setCellValue("2023-01-15");
        
        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("456");
        row2.createCell(1).setCellValue("Toro");
        row2.createCell(2).setCellValue("VACIA");
        row2.createCell(3).setCellValue("");
        row2.createCell(4).setCellValue("");
        
        Row row3 = sheet.createRow(3);
        row3.createCell(0).setCellValue("");
        row3.createCell(1).setCellValue("Lola");
        row3.createCell(2).setCellValue("CELO");
        row3.createCell(3).setCellValue("");
        row3.createCell(4).setCellValue("");
        
        try (FileOutputStream fileOut = new FileOutputStream("../frontend/public/template_estados.xlsx")) {
            workbook.write(fileOut);
        }
        workbook.close();
    }
}
