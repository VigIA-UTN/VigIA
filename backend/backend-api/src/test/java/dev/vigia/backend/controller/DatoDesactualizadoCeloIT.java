package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dato desactualizado de las alertas de celo: la de A-106 es de una caravana sin datos hace 3 h y la de A-102, de una
 * que transmite. La última señal se fija relativa a la hora de la JVM en cada caso.
 */
@DisplayName("H0008 - Alerta de celo: dato desactualizado (más de 2 h sin datos) — IT")
class DatoDesactualizadoCeloIT extends DeteccionItBase {

    @Test
    @DisplayName("H0008-04 - Alerta de celo - Marca de dato desactualizado en el detalle y el listado")
    void H0008_04_marcaDatoDesactualizado() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        ultimaSenial("FX-A-06", ahora.minusHours(3));
        ultimaSenial("FX-A-02", ahora.minusMinutes(5));
        Long sinDatos = idAlerta(EST_A, "A-106", "CELO");
        Long conDatos = idAlerta(EST_A, "A-102", "CELO");

        JsonNode detalle = leer(ok(getAlerta("productorA", "/" + sinDatos, EST_A)));
        assertThat(detalle.get("datoPosiblementeDesactualizado").asBoolean()).isTrue();
        assertThat(detalle.get("horasUmbralDatoDesactualizado").asLong()).isEqualTo(2);
        assertThat(LocalDateTime.parse(detalle.get("caravanaUltimaSenial").asText()))
                .isEqualToIgnoringNanos(ahora.minusHours(3));
        assertThat(leer(ok(getAlerta("productorA", "/" + conDatos, EST_A)))
                .get("datoPosiblementeDesactualizado").asBoolean()).isFalse();

        // El listado de alertas de celo trae la misma marca.
        JsonNode listado = leer(ok(getAlerta("veterinarioA", "/celo", EST_A)));
        assertThat(marcaEnListado(listado, sinDatos)).isTrue();
        assertThat(marcaEnListado(listado, conDatos)).isFalse();
    }

    @Test
    @DisplayName("H0008-05 - Alerta de celo - Dato desactualizado en el límite de 2 h y casos que no aplican")
    void H0008_05_limitesDelDatoDesactualizado() throws Exception {
        Long alerta = idAlerta(EST_A, "A-102", "CELO");
        LocalDateTime ahora = LocalDateTime.now();

        // Justo antes de las 2 h todavía es un dato válido; pasadas, ya no.
        ultimaSenial("FX-A-02", ahora.minusHours(2).plusMinutes(1));
        assertThat(marca(alerta)).isFalse();
        ultimaSenial("FX-A-02", ahora.minusHours(2).minusMinutes(1));
        assertThat(marca(alerta)).isTrue();

        // Una caravana que nunca transmitió no respalda la alerta.
        ultimaSenial("FX-A-02", null);
        assertThat(marca(alerta)).isTrue();

        // Ya resuelta, la marca deja de aplicar.
        jdbcTemplate.update("UPDATE alertas SET estado = 'CONFIRMADA', ts_confirmacion = now() WHERE id = ?", alerta);
        assertThat(marca(alerta)).isFalse();

        // Tampoco aplica a otros disparadores (la sanitaria de A-103, con la caravana sin datos hace 3 h).
        ultimaSenial("FX-A-03", ahora.minusHours(3));
        assertThat(leer(ok(getAlerta("productorA", "/" + idAlerta(EST_A, "A-103", "SALUD"), EST_A)))
                .get("datoPosiblementeDesactualizado").asBoolean()).isFalse();
    }

    private boolean marca(Long alertaId) throws Exception {
        return leer(ok(getAlerta("productorA", "/" + alertaId, EST_A))).get("datoPosiblementeDesactualizado")
                .asBoolean();
    }

    private static boolean marcaEnListado(JsonNode listado, Long alertaId) {
        for (JsonNode alerta : listado) {
            if (alerta.get("id").asLong() == alertaId) {
                return alerta.get("datoPosiblementeDesactualizado").asBoolean();
            }
        }
        throw new AssertionError("La alerta " + alertaId + " no está en el listado");
    }

}
