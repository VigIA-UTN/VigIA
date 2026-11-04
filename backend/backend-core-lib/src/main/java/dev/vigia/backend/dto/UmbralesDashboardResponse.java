package dev.vigia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Los umbrales con los que se calculó el Dashboard, para que el frontend los muestre sin repetirlos. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmbralesDashboardResponse {
    private int horasSinSenial;
    private int bateriaBajaPorcentaje;
    private int horasVentanaPorVencer;
    private int diasDiagnosticoInseminada;
    private int colaMax;
    private int horasSaludSinAtender;
    private int diasVaciaLarga;
    private int diasGestacion;
}
