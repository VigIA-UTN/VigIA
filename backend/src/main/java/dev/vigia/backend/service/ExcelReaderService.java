package dev.vigia.backend.service;

import org.springframework.web.multipart.MultipartFile;

import dev.vigia.backend.dto.CargaMasivaResult;

import java.io.IOException;

public interface ExcelReaderService {

    CargaMasivaResult leerYGuardarAnimalesExcel(MultipartFile file, String email)
            throws IOException, IllegalArgumentException;
}
