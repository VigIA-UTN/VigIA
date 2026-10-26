package dev.vigia.backend.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

public interface FileParserService {
    /** IdCaravana, Alias, FechaNacimiento, Raza y RodeoLote: el archivo tiene que traerlas. */
    int COLUMNAS_MINIMAS = 5;
    /** La sexta, Sensor, es opcional: los archivos de 5 columnas siguen siendo válidos. */
    int COLUMNAS_LEIDAS = 6;

    boolean supports(String filename);
    /** Una fila por animal (sin el encabezado), siempre con COLUMNAS_LEIDAS posiciones; las ausentes quedan en null. */
    List<String[]> parse(MultipartFile file) throws IOException;
}
