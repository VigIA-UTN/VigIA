package dev.vigia.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidateCodeResponse {
    private String email;
    private String rol;
    private String establecimiento;
}
