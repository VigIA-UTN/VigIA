package dev.vigia.backend.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CargaMasivaResponse {
    private int procesados;
    private int exitosos;
    private int fallidos;
    private List<AnimalResponse> animales;
    private List<String> errores;
}
