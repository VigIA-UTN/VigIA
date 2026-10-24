package dev.vigia.backend.service.impl;

import dev.vigia.backend.service.FileParserService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvParserServiceImpl implements FileParserService {

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".csv");
    }

    @Override
    public List<String[]> parse(MultipartFile file) throws IOException {
        List<String[]> rowsData = new ArrayList<>();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setSkipHeaderRecord(true).build())) {
            
            boolean firstRow = true;
            for (CSVRecord csvRecord : csvParser) {
                if (firstRow) {
                    firstRow = false;
                    if (csvRecord.size() < COLUMNAS_MINIMAS) {
                        throw new IllegalArgumentException("El archivo CSV debe tener al menos 5 columnas.");
                    }
                    continue;
                }

                if (csvRecord.size() < COLUMNAS_MINIMAS) {
                    throw new IllegalArgumentException("El archivo CSV debe tener al menos 5 columnas.");
                }

                String[] data = new String[COLUMNAS_LEIDAS];
                for (int i = 0; i < Math.min(COLUMNAS_LEIDAS, csvRecord.size()); i++) {
                    data[i] = csvRecord.get(i);
                }
                rowsData.add(data);
            }
        }
        return rowsData;
    }
}
