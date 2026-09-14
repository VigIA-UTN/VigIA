package dev.vigia.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

//TODO creo que quedo obsoleto, revisar
@Data
public class IngestaRequestDTO {
    private String device_id;
    private LocalDateTime timestamp;
    private Payload payload;

    @Data
    public static class Payload {
        private Double latitud;
        private Double longitud;
        private Integer moveState;
        private Double bateria;
    }
}
