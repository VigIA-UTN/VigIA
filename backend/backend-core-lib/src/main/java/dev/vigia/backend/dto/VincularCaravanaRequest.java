package dev.vigia.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VincularCaravanaRequest {

    @NotNull(message = "El id de la caravana es obligatorio.")
    private Long caravanaId;
}
