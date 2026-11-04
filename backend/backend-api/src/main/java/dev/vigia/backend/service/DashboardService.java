package dev.vigia.backend.service;

import dev.vigia.backend.dto.CentroMandoResponse;

/** Métricas del Dashboard, calculadas en el backend por pestaña. */
public interface DashboardService {

    /**
     * Pestaña "Centro de mando" (H0043).
     *
     * @param rodeoId opcional: solo los animales de ese rodeo y sus alertas
     */
    CentroMandoResponse centroMando(String emailUsuario, Long establecimientoId, Long rodeoId);
}
