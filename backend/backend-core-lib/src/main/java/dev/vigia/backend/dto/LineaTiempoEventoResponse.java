package dev.vigia.backend.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineaTiempoEventoResponse {


    private String tipoEvento;
    private Long id;
    private LocalDateTime fecha;

    private String tipo;
    // Tipo crudo de la alerta ("Anestro Detectado", ...); tipo lleva el disparador.
    private String subtipo;
    private String severidad;
    // Valor medido y umbral del disparador (en el anestro: días sin ciclo y días configurados).
    private Double valorDetectado;
    private Double valorUmbral;

    private String estadoResolucion;
    // Quién confirmó o descartó la alerta y cuándo; null mientras está pendiente.
    private String usuarioResolucion;
    private LocalDateTime tsResolucion;

    private String tipoIntervencion;
    private String descripcion;
    private String interventor;

    private Long alertaAsociadaId;
}
