package dev.vigia.backend;

import java.io.FileOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class GenerateExcelTemplate {
    public static void main(String[] args) throws Exception {
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
        System.out.println("Excel generated");
    }
}
