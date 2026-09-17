package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UltimaPosicionResponseDTO {
    private Double latitud;
    private Double longitud;
    private Double bateria;
    private Integer moveState;
    private LocalDateTime fechaHora;
}
