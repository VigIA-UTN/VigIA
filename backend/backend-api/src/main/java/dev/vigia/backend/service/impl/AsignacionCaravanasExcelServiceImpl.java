package dev.vigia.backend.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.service.AsignacionCaravanasExcelService;

@Service
public class AsignacionCaravanasExcelServiceImpl implements AsignacionCaravanasExcelService {

    // "Colocado" queda vacía para tildarla a mano en la manga.
    private static final String[] COLUMNAS = { "Caravana oficial", "Alias", "Raza", "Fecha de nacimiento",
            "Rodeo / Lote", "Sensor", "DevEUI", "Colocado" };
    // Anchos fijos en caracteres: autoSizeColumn necesita fuentes del sistema que la imagen slim no trae.
    private static final int[] ANCHOS = { 18, 22, 16, 20, 22, 24, 24, 12 };
    private static final int FILA_ENCABEZADO = 3;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Por lote y después por caravana oficial, que es como se recorre la manga. */
    static final Comparator<Animal> ORDEN = Comparator
            .comparing((Animal a) -> a.getRodeoLote() != null ? a.getRodeoLote().getNombre() : null,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Animal::getIdCaravanaOficial,
                    Comparator.nullsLast(AsignacionCaravanasExcelServiceImpl::compararCaravanaOficial));

    @Override
    public byte[] generar(String establecimientoNombre, List<Animal> animales) {
        List<Animal> ordenados = animales.stream().sorted(ORDEN).toList();

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Asignaciones");
            CellStyle titulo = estiloTitulo(workbook);
            CellStyle encabezado = estiloEncabezado(workbook);
            CellStyle celda = estiloCelda(workbook);

            sheet.createRow(0).createCell(0).setCellValue("Asignación de caravanas — " + establecimientoNombre);
            sheet.getRow(0).getCell(0).setCellStyle(titulo);
            sheet.createRow(1).createCell(0).setCellValue("Generado el " + LocalDateTime.now().format(FECHA_HORA)
                    + " · " + ordenados.size() + (ordenados.size() == 1 ? " animal" : " animales"));

            Row filaEncabezado = sheet.createRow(FILA_ENCABEZADO);
            for (int i = 0; i < COLUMNAS.length; i++) {
                filaEncabezado.createCell(i).setCellValue(COLUMNAS[i]);
                filaEncabezado.getCell(i).setCellStyle(encabezado);
                sheet.setColumnWidth(i, ANCHOS[i] * 256);
            }

            int nroFila = FILA_ENCABEZADO + 1;
            for (Animal animal : ordenados) {
                Caravana caravana = animal.getCaravana();
                String[] valores = {
                        animal.getIdCaravanaOficial(),
                        animal.getAlias(),
                        animal.getRaza(),
                        animal.getFechaNacimiento() != null ? animal.getFechaNacimiento().format(FECHA) : null,
                        animal.getRodeoLote() != null ? animal.getRodeoLote().getNombre() : null,
                        nombreSensor(caravana),
                        caravana.getDevEui(),
                        null };
                Row fila = sheet.createRow(nroFila++);
                for (int i = 0; i < valores.length; i++) {
                    fila.createCell(i).setCellValue(valores[i] != null ? valores[i] : "");
                    fila.getCell(i).setCellStyle(celda);
                }
            }

            sheet.createFreezePane(0, FILA_ENCABEZADO + 1);
            sheet.setAutoFilter(new CellRangeAddress(FILA_ENCABEZADO, Math.max(FILA_ENCABEZADO, nroFila - 1),
                    0, COLUMNAS.length - 1));
            // Repite el encabezado en cada hoja impresa ("4:4" en notación de Excel, base 1).
            sheet.setRepeatingRows(CellRangeAddress.valueOf((FILA_ENCABEZADO + 1) + ":" + (FILA_ENCABEZADO + 1)));
            sheet.setFitToPage(true);
            PrintSetup impresion = sheet.getPrintSetup();
            impresion.setLandscape(true);
            impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
            impresion.setFitWidth((short) 1);
            impresion.setFitHeight((short) 0);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel de asignaciones.", e);
        }
    }

    /** El mismo nombre que muestra la UI al elegir una caravana. */
    private static String nombreSensor(Caravana caravana) {
        if (caravana.getAlias() != null && !caravana.getAlias().isBlank()) {
            return caravana.getAlias();
        }
        if (caravana.getCodigo() != null && !caravana.getCodigo().isBlank()) {
            return caravana.getCodigo();
        }
        return caravana.getDevEui();
    }

    /** Numérica si las dos son números ("9" antes que "10"); si no, alfabética. */
    private static int compararCaravanaOficial(String a, String b) {
        if (a.matches("\\d+") && b.matches("\\d+")) {
            return new BigInteger(a).compareTo(new BigInteger(b));
        }
        return String.CASE_INSENSITIVE_ORDER.compare(a, b);
    }

    private static CellStyle estiloTitulo(XSSFWorkbook workbook) {
        Font fuente = workbook.createFont();
        fuente.setBold(true);
        fuente.setFontHeightInPoints((short) 14);
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fuente);
        return estilo;
    }

    private static CellStyle estiloEncabezado(XSSFWorkbook workbook) {
        Font fuente = workbook.createFont();
        fuente.setBold(true);
        CellStyle estilo = estiloCelda(workbook);
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return estilo;
    }

    private static CellStyle estiloCelda(XSSFWorkbook workbook) {
        CellStyle estilo = workbook.createCellStyle();
        estilo.setBorderTop(BorderStyle.THIN);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBorderLeft(BorderStyle.THIN);
        estilo.setBorderRight(BorderStyle.THIN);
        return estilo;
    }
}
