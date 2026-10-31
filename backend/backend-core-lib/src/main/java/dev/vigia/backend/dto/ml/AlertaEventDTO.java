package dev.vigia.backend.dto.ml;

import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.SeveridadAlerta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import dev.vigia.backend.dto.AnimalAisladoResponse;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertaEventDTO {
    private Long id;
    private Long animalId;
    private String tipo;
    private SeveridadAlerta severidad;
    private LocalDateTime tsGeneracion;
    private DisparadorAlerta disparador;
    private Double valorUmbral;
    private Double valorDetectado;
    private EstadoAlerta estado;
    private String accionRecomendada;
    private Float confianzaModelo;
    private Integer ventanaOptimaHoras;

    private Boolean desvioDetectado;
    private String posibleCausa;

    private Double latitud;
    private Double longitud;

    /** Solo AISLAMIENTO_SOCIAL: la lista de animales, armada por el worker al publicar. */
    private List<AnimalAisladoResponse> animalesAislados;

    /**
     * true solo en el evento que crea la alerta. Los re-envíos de una alerta existente (cambio de
     * estado, animales que se suman o vuelven) van en false: actualizan la tarjeta pero no avisan por mail.
     */
    private boolean nueva;
}
