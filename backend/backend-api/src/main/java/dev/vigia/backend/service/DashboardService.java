package dev.vigia.backend.service;

import dev.vigia.backend.dto.CentroMandoResponse;
import dev.vigia.backend.dto.SaludReproduccionResponse;

/** Métricas del Dashboard, calculadas en el backend por pestaña. */
public interface DashboardService {

    /**
     * Pestaña "Centro de mando" (H0043).
     *
     * @param rodeoId opcional: solo los animales de ese rodeo y sus alertas
     */
    CentroMandoResponse centroMando(String emailUsuario, Long establecimientoId, Long rodeoId);

    /**
     * Pestaña "Salud y reproducción" (H0044).
     *
     * @param rodeoId     opcional: solo los animales de ese rodeo y sus alertas
     * @param periodoDias días hacia atrás para el desempeño reproductivo y la reincidencia
     */
    SaludReproduccionResponse saludReproduccion(String emailUsuario, Long establecimientoId, Long rodeoId,
            int periodoDias);
}
