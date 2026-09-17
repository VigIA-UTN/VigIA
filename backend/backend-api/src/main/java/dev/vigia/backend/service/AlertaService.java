package dev.vigia.backend.service;

import dev.vigia.backend.dto.AlertaDetalleResponse;
import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.AlertaResumenResponse;
import dev.vigia.backend.dto.IntervencionRequest;
import dev.vigia.backend.dto.LineaTiempoEventoResponse;
import dev.vigia.backend.dto.VentanaInseminacionResponse;
import dev.vigia.backend.model.EstadoAlerta;
import java.time.LocalDateTime;
import java.util.List;

public interface AlertaService {

        AlertaResumenResponse obtenerResumenAlertas(String emailUsuario, Long establecimientoId);

        List<AlertaResponse> listarAlertas(String emailUsuario, Long establecimientoId, String tipo, String severidad,
                        EstadoAlerta estado);

        List<AlertaResponse> listarAlertasCelo(String emailUsuario, Long establecimientoId, EstadoAlerta estado);

        List<AlertaResponse> listarAlertasSalud(String emailUsuario, Long establecimientoId, EstadoAlerta estado);

        AlertaDetalleResponse obtenerDetalleAlerta(String emailUsuario, Long establecimientoId, Long alertaId);

        AlertaResponse actualizarEstadoAlerta(String emailUsuario, Long alertaId, EstadoAlerta nuevoEstado,
                        String notas);

        AlertaResponse archivarAlertaPorVencimiento(String emailUsuario, Long establecimientoId, Long alertaId);

        VentanaInseminacionResponse obtenerEstadoVentanaInseminacion(String emailUsuario, Long establecimientoId,
                        Long alertaId);

        AlertaResponse intervenirAlerta(String emailUsuario, Long alertaId, IntervencionRequest request);

        List<LineaTiempoEventoResponse> obtenerLineaTiempoAlertas(
                        String emailUsuario,
                        Long animalId,
                        String tipo,
                        LocalDateTime desde,
                        LocalDateTime hasta);
}
