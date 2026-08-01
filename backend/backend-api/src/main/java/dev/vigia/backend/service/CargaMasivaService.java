package dev.vigia.backend.service;

import org.springframework.web.multipart.MultipartFile;
import dev.vigia.backend.dto.CargaMasivaResult;
import java.io.IOException;

public interface CargaMasivaService {
    CargaMasivaResult procesarCargaMasiva(MultipartFile file, Long establecimientoId, String email)
            throws IOException, IllegalArgumentException;
}
