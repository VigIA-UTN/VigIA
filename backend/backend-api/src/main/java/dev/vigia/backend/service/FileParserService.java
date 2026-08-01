package dev.vigia.backend.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

public interface FileParserService {
    boolean supports(String filename);
    List<String[]> parse(MultipartFile file) throws IOException;
}
