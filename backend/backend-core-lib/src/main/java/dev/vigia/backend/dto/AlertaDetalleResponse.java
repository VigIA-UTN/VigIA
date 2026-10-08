package dev.vigia.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertaDetalleResponse {
    private Long id;

    private Long animalId;
    private String animalCaravanaOficial;
    private String animalAlias;
    private String animalRaza;

    private LocalDateTime tsGeneracion;
    private Double latitud;
    private Double longitud;
    private String ventanaOptimaHoras;
    private LocalDateTime ventanaInicioIns;
    private LocalDateTime ventanaFinIns;
    private DisparadorAlerta datoDisparador;
    private EstadoAlerta estadoActual;
    private String usuarioAccion;
    private List<EstadoReproductivoResponse> ultimosEstadosReproductivos;
    private Double valorDetectado;
    private Double valorUmbral;
    private String descripcionDatoOrigen;
    private DetalleSanitarioResponse detalleSanitario;
    private DetalleGeoespacialResponse detalleGeoespacial;
    private String accionRecomendada;

    private LocalDateTime caravanaUltimaSenial;
    private boolean datoPosiblementeDesactualizado;
    private long horasUmbralDatoDesactualizado;
}
