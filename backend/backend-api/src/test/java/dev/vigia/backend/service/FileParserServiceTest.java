package dev.vigia.backend.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import dev.vigia.backend.service.impl.CsvParserServiceImpl;
import dev.vigia.backend.service.impl.ExcelParserServiceImpl;

@DisplayName("FileParserService - Columna Sensor opcional")
class FileParserServiceTest {

        private final CsvParserServiceImpl csvParser = new CsvParserServiceImpl();
        private final ExcelParserServiceImpl excelParser = new ExcelParserServiceImpl();

        private static MockMultipartFile csv(String contenido) {
                return new MockMultipartFile("file", "animales.csv", "text/csv",
                                contenido.getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("CSV de 5 columnas (formato anterior): el sensor queda en null")
        void csv_cincoColumnas_sensorNull() throws Exception {
                List<String[]> filas = csvParser.parse(csv(
                                "IdCaravana,Alias,FechaNacimiento,Raza,RodeoLote\n301,Valentina,2025-12-28,Holando,Lote Cuarentena\n"));
                assertArrayEquals(new String[] { "301", "Valentina", "2025-12-28", "Holando", "Lote Cuarentena", null },
                                filas.get(0));
        }

        @Test
        @DisplayName("CSV de 6 columnas: lee el sensor, y vacío cuando la fila no lo trae")
        void csv_seisColumnas_leeSensor() throws Exception {
                List<String[]> filas = csvParser.parse(csv(
                                "IdCaravana,Alias (opcional),FechaNacimiento,Raza (opcional),RodeoLote,Sensor (opcional)\n"
                                                + "1,A,2020-01-01,Angus,Lote,EUI-1\n"
                                                + "2,B,2020-01-01,Angus,Lote,\n"));
                assertEquals("EUI-1", filas.get(0)[5]);
                assertEquals("", filas.get(1)[5]);
        }

        @Test
        @DisplayName("Las plantillas que se descargan desde el frontend se leen con el sensor en la sexta columna")
        void plantillasDelFrontend_seLeen() throws Exception {
                Path publico = Path.of("..", "..", "frontend", "public");
                assumeTrue(Files.isDirectory(publico), "Sin el frontend al lado (por ejemplo, en el build de Docker)");

                List<String[]> desdeExcel = excelParser.parse(new MockMultipartFile("file", "template_animales.xlsx", null,
                                Files.readAllBytes(publico.resolve("template_animales.xlsx"))));
                List<String[]> desdeCsv = csvParser.parse(new MockMultipartFile("file", "template_animales.csv", null,
                                Files.readAllBytes(publico.resolve("template_animales.csv"))));

                assertEquals(3, desdeExcel.size());
                assertEquals("A84041C2F1B3D001", desdeExcel.get(0)[5]);
                for (int i = 0; i < desdeExcel.size(); i++) {
                        for (int c = 0; c < 6; c++) {
                                String excel = desdeExcel.get(i)[c] == null ? "" : desdeExcel.get(i)[c];
                                assertEquals(desdeCsv.get(i)[c], excel, "fila " + i + ", columna " + c);
                        }
                }
        }
}
