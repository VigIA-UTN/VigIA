package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * GET /api/v1/alertas/{id}/ventana: estado de la ventana de inseminación (de 6 a 24 h desde la detección). Cada caso
 * crea la alerta de celo de A-101 con la antigüedad que necesita. El archivado al vencer (H0016-03) lo hace el worker.
 */
@DisplayName("H0016 - Countdown ventana óptima de inseminación (6-24hs) — IT")
class VentanaInseminacionIT extends DeteccionItBase {

    @Test
    @DisplayName("H0016-01 - Ventana de inseminación - Ventana abierta con tiempo restante y progreso")
    void H0016_01_ventanaActiva() throws Exception {
        // Detectado hace 12 h: la ventana abrió hace 6 h y cierra en 12 h (un tercio transcurrido).
        Long alerta = crearAlertaCelo(idAnimal(EST_A, "A-101"), LocalDateTime.now().minusHours(12));

        JsonNode ventana = ventana("productorA", alerta);

        assertThat(ventana.get("estado").asText()).isEqualTo("ACTIVA");
        assertThat(ventana.get("tiempoRestanteSegundos").asLong()).isCloseTo(12 * 3600, within(60L));
        assertThat(ventana.get("porcentajeTranscurrido").asDouble()).isCloseTo(33.3, within(0.5));
        assertThat(ventana.get("proximaAVencer").asBoolean()).isFalse();

        // El alias histórico devuelve lo mismo.
        JsonNode alias = leer(ok(getAlerta("camperoA", "/" + alerta + "/ventana-inseminacion", EST_A)));
        assertThat(alias.get("estado").asText()).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("H0016-02 - Ventana de inseminación - Antes de abrir y a menos de 2 h de vencer")
    void H0016_02_futuraYProximaAVencer() throws Exception {
        // Detectado hace 1 h: la ventana todavía no abrió y el countdown cuenta hasta que abre (faltan 5 h).
        Long futura = crearAlertaCelo(idAnimal(EST_A, "A-101"), LocalDateTime.now().minusHours(1));
        JsonNode antes = ventana("veterinarioA", futura);
        assertThat(antes.get("estado").asText()).isEqualTo("FUTURA");
        assertThat(antes.get("tiempoRestanteSegundos").asLong()).isCloseTo(5 * 3600, within(60L));
        assertThat(antes.get("porcentajeTranscurrido").asDouble()).isZero();
        assertThat(antes.get("proximaAVencer").asBoolean()).isFalse();

        // Detectado hace 22 h 30 min: queda 1 h 30 min, se destaca la urgencia.
        Long urgente = crearAlertaCelo(idAnimal(EST_A, "A-103"), LocalDateTime.now().minusMinutes(22 * 60 + 30));
        JsonNode cierre = ventana("productorA", urgente);
        assertThat(cierre.get("estado").asText()).isEqualTo("ACTIVA");
        assertThat(cierre.get("tiempoRestanteSegundos").asLong()).isCloseTo(90 * 60, within(60L));
        assertThat(cierre.get("proximaAVencer").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("H0016-04 - Ventana de inseminación - Ventana vencida y alertas sin ventana")
    void H0016_04_vencidaYAlertasSinVentana() throws Exception {
        // Detectado hace 25 h: venció hace 1 h.
        Long vencida = crearAlertaCelo(idAnimal(EST_A, "A-101"), LocalDateTime.now().minusHours(25));
        JsonNode ventana = ventana("productorA", vencida);
        assertThat(ventana.get("estado").asText()).isEqualTo("VENCIDA");
        assertThat(ventana.get("tiempoRestanteSegundos").asLong()).isZero();
        assertThat(ventana.get("porcentajeTranscurrido").asDouble()).isEqualTo(100.0);
        assertThat(ventana.get("proximaAVencer").asBoolean()).isFalse();

        // Una alerta sanitaria y una de anestro no tienen ventana de inseminación.
        assertThat(texto(getAlerta("productorA", "/" + idAlerta(EST_A, "A-103", "SALUD") + "/ventana", EST_A)))
                .contains("no es de tipo CELO");
        Long anestro = jdbcTemplate.queryForObject("INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, "
                + "disparador, valor_umbral, valor_detectado, estado) VALUES (?, 'Anestro Detectado', 'MEDIA', ?, 'CELO', "
                + "45, 60, 'PENDIENTE') RETURNING id", Long.class, idAnimal(EST_A, "A-101"),
                Timestamp.valueOf(LocalDateTime.now()));
        assertThat(estado(getAlerta("productorA", "/" + anestro + "/ventana", EST_A))).isEqualTo(400);

        // Otro establecimiento no la ve.
        assertThat(estado(getAlerta("productorB", "/" + vencida + "/ventana", EST_B))).isEqualTo(403);
    }

    private JsonNode ventana(String clave, Long alertaId) throws Exception {
        return leer(ok(getAlerta(clave, "/" + alertaId + "/ventana", EST_A)));
    }
}
