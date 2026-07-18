package dev.vigia.backend.dto;

import java.util.List;

import dev.vigia.backend.model.Animal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CargaMasivaResult {
    private int procesados;
    private int fallidos;
    private List<Animal> animalesExitosos;
}
