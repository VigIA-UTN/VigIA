package dev.vigia.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ValidateCodeRequest {
    
    @NotBlank(message = "El código de invitación es obligatorio")
    private String token;

    private String email;
}
