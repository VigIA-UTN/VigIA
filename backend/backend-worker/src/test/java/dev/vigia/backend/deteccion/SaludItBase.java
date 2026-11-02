package dev.vigia.backend.deteccion;

import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Base de los IT del job de salud contra ml-salud simulado. En el escenario, A-101 tiene 8 días de telemetría (7 de
 * calibración y la ventana que se evalúa) y todavía no tiene perfil; A-102 tiene solo 24 h; A-103 ya tiene una alerta
 * sanitaria de hace 2 h.
 */
abstract class SaludItBase extends DeteccionItBase {

    /** Perfil como el que devuelve ml-salud: las claves que usan los indicadores y la serie comparativa. */
    protected static final String PERFIL = "{\"mean_moveState_mean\": 3.2, \"mean_moveState_std\": 1.1, "
            + "\"quarter_moveState_mean\": [3.0, 3.5, 3.2, 2.8], \"quarter_moveState_std\": [0.5, 0.5, 0.5, 0.5], "
            + "\"quarter_distancia_mean\": [1.0, 1.2, 1.1, 0.9], \"quarter_distancia_std\": [0.2, 0.2, 0.2, 0.2]}";

    protected static final String SIN_DESVIO =
            "{\"desvio_detectado\": false, \"severidad\": 0, \"posible_causa\": null, \"confianza_modelo\": 0.08}";

    @Autowired
    @Qualifier("saludJob")
    protected Job saludJob;

    protected void responderPerfil() {
        ML.responder(MlSimulado.SALUD_PERFIL, "{\"perfil\": " + PERFIL + "}");
    }

    protected static String desvio(int severidad, String posibleCausa, double confianza) {
        return "{\"desvio_detectado\": true, \"severidad\": " + severidad + ", \"posible_causa\": "
                + (posibleCausa == null ? "null" : "\"" + posibleCausa + "\"") + ", \"confianza_modelo\": " + confianza
                + "}";
    }

    /** El animal ya terminó la calibración: tiene el perfil calculado (hace un día). */
    protected LocalDateTime cargarPerfil(Long animalId) {
        LocalDateTime calculado = LocalDateTime.now().minusDays(1).withNano(0);
        jdbcTemplate.update("INSERT INTO perfiles_referencia (animal_id, perfil_json, fecha_calculo) VALUES (?, ?, ?)",
                animalId, PERFIL, Timestamp.valueOf(calculado));
        return calculado;
    }
}
