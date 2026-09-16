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
public class AnimalIndicadoresSaludResponse {
    private Long animalId;
    private String caravana;
    private List<IndicadorResponse> indicadores;
}
