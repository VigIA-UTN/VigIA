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
                    continue;
                }
                
                String[] data = new String[Math.max(5, csvRecord.size())];
                for (int i = 0; i < csvRecord.size() && i < 5; i++) {
                    data[i] = csvRecord.get(i);
                }
                rowsData.add(data);
            }
        }
        return rowsData;
    }
}
