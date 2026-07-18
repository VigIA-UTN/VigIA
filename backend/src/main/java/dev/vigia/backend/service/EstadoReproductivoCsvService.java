package dev.vigia.backend.service;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import dev.vigia.backend.dto.EstadoReproductivoParseResult;

public interface EstadoReproductivoCsvService {
    EstadoReproductivoParseResult parseAndValidate(MultipartFile file) throws IOException, IllegalArgumentException;
}
