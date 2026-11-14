package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * GET /api/v1/alertas/{id} sobre una alerta sanitaria con la serie comparativa que guarda el job de salud (lo que
 * persiste el worker lo cubren H0018-01 a H0018-04 en backend-worker).
 */
@DisplayName("H0018 - Detalle de alerta sanitaria con causa probable — IT")
class DetalleSanitarioIT extends DeteccionItBase {

    private static final String SERIE = "{\"descripcionDesvio\": \"Actividad promedio 40% por debajo del perfil de "
            + "referencia del animal en el bloque 6-12h. Severidad alta. Posible causa: Posible cojera.\", "
            + "\"ventanaDesde\": \"2026-11-01T08:00:00\", \"ventanaHasta\": \"2026-11-02T08:00:00\", "
            + "\"perfilCalculadoEn\": \"2026-10-25T08:00:00\", "
            + "\"serieActividad\": [{\"bloque\": \"0-6h\", \"actual\": 2.0, \"referenciaMedia\": 3.0, "
            + "\"referenciaDesvio\": 0.5}, {\"bloque\": \"6-12h\", \"actual\": 2.1, \"referenciaMedia\": 3.5, "
            + "\"referenciaDesvio\": 0.5}, {\"bloque\": \"12-18h\", \"actual\": 2.5, \"referenciaMedia\": 3.2, "
            + "\"referenciaDesvio\": 0.5}, {\"bloque\": \"18-24h\", \"actual\": 2.2, \"referenciaMedia\": 2.8, "
            + "\"referenciaDesvio\": 0.5}], \"serieDesplazamiento\": []}";

    @Test
    @DisplayName("H0018-05 - Detalle sanitario - Descripción del desvío, severidad, causa, gráfico comparativo y acción")
    void H0018_05_detalleSanitario() throws Exception {
        Long alerta = jdbcTemplate.queryForObject("INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, "
                + "disparador, valor_umbral, valor_detectado, estado, confianza_modelo, desvio_detectado, posible_causa, "
                + "serie_comparativa_json, accion_recomendada) VALUES (?, 'SALUD', 'ALTA', ?, 'SALUD', 0.5, 0.93, "
                + "'PENDIENTE', 0.93, 1, 'Posible cojera', ?, 'Atención veterinaria urgente y aislamiento en "
                + "enfermería. Causa sospechada: Posible cojera (riesgo alto).') RETURNING id",
                Long.class, idAnimal(EST_A, "A-101"), Timestamp.valueOf(LocalDateTime.now()), SERIE);

        JsonNode detalle = leer(ok(getAlerta("veterinarioA", "/" + alerta, EST_A)));

        JsonNode sanitario = detalle.get("detalleSanitario");
        assertThat(sanitario.get("desvioDetectado").asBoolean()).isTrue();
        assertThat(sanitario.get("severidad").asText()).isEqualTo("ALTA");
        assertThat(sanitario.get("posibleCausa").asText()).isEqualTo("Posible cojera");
        assertThat(sanitario.get("confianzaModelo").asDouble()).isCloseTo(0.93, within(1e-6));
        assertThat(sanitario.get("descripcionDesvio").asText()).startsWith("Actividad promedio 40% por debajo");
        assertThat(sanitario.get("perfilCalculadoEn").asText()).isEqualTo("2026-10-25T08:00:00");
        JsonNode actividad = sanitario.get("serieActividad");
        assertThat(actividad).hasSize(4);
        assertThat(actividad.get(1).get("bloque").asText()).isEqualTo("6-12h");
        assertThat(actividad.get(1).get("actual").asDouble()).isEqualTo(2.1);
        assertThat(actividad.get(1).get("referenciaMedia").asDouble()).isEqualTo(3.5);
        // La descripción del dato origen es la del desvío, y la acción la que armó el job.
        assertThat(detalle.get("descripcionDatoOrigen").asText()).isEqualTo(sanitario.get("descripcionDesvio").asText());
        assertThat(detalle.get("accionRecomendada").asText()).contains("Causa sospechada: Posible cojera");

        // Sin serie guardada (la alerta del escenario de A-103), la descripción sale de la severidad y la causa.
        JsonNode sinSerie = leer(ok(getAlerta("productorA", "/" + idAlerta(EST_A, "A-103", "SALUD"), EST_A)))
                .get("detalleSanitario");
        assertThat(sinSerie.get("severidad").asText()).isEqualTo("MEDIA");
        assertThat(sinSerie.get("descripcionDesvio").asText()).isEqualTo("Desvío sanitario de severidad media "
                + "detectado respecto del perfil de referencia del animal. Posible causa: Caída de actividad respecto "
                + "de su perfil.");
        assertThat(sinSerie.get("serieActividad").isNull()).isTrue();
    }
}
