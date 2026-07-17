package dev.vigia.backend.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import dev.vigia.backend.model.Animal;

public interface ExcelReaderService {

    List<Animal> leerYGuardarAnimalesExcel(MultipartFile file, String email)
            throws IOException, IllegalArgumentException;
}
