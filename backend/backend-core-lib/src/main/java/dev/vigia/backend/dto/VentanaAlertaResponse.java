package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Un celo pendiente con su ventana de inseminación, para las listas del Dashboard. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VentanaAlertaResponse {
    private AlertaResponse alerta;
    private AnimalResumenResponse animal;
    private VentanaInseminacionResponse ventana;
}
