package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base de los IT de la regresión E5 (mapa, mapa de calor y Dashboard). Suma a {@link GestionRodeoItBase} el acceso al
 * mapa de posiciones y la carga de telemetría puntual sobre las caravanas del escenario.
 * <p>
 * Lo que agregan los tests cuelga de animales y caravanas de Fixture A o B, así que {@code limpiar.sql} lo borra en la
 * próxima carga.
 */
abstract class MapaDashboardItBase extends GestionRodeoItBase {

    // ── Mapa de posiciones ───────────────────────────────────────────────────

    protected MvcResult mapa(String clave, String establecimiento) throws Exception {
        return mockMvc.perform(get("/api/v1/animales/mapa")
                        .param("establecimientoId", idEstablecimiento(establecimiento).toString())
                        .header("Authorization", bearer(clave)))
                .andReturn();
    }

    /** Mapa del establecimiento, verificando el 200. */
    protected JsonNode mapaOk(String clave, String establecimiento) throws Exception {
        MvcResult resultado = mapa(clave, establecimiento);
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        return leer(resultado);
    }

    /** El animal del mapa por su identificador visible ({@code A-101}). */
    protected JsonNode animalDelMapa(JsonNode mapa, String identificador) {
        return StreamSupport.stream(mapa.spliterator(), false)
                .filter(a -> identificador.equals(a.get("identificador").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("El mapa no trae a " + identificador + ": " + mapa));
    }

    // ── Telemetría ───────────────────────────────────────────────────────────

    /** Ahora, al segundo: la base guarda microsegundos y así la comparación con la respuesta es exacta. */
    protected static LocalDateTime ahora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * Inserta un dato de telemetría como lo guarda la ingesta del worker, con las inferencias en {@code COMPLETADO}
     * para que los jobs no lo procesen.
     */
    protected void insertarTelemetria(String codigoCaravana, LocalDateTime fechaHora, double latitud,
            double longitud) {
        jdbcTemplate.update("INSERT INTO telemetria (fecha_hora, caravana_id, latitud, longitud, move_state, bateria, "
                        + "estado_inferencia_celo, estado_inferencia_salud, estado_inferencia_geo) "
                        + "VALUES (?, ?, ?, ?, 3, 80, 'COMPLETADO', 'COMPLETADO', 'COMPLETADO')",
                Timestamp.valueOf(fechaHora), idCaravana(codigoCaravana), latitud, longitud);
    }

    protected void borrarTelemetria(String codigoCaravana) {
        jdbcTemplate.update("DELETE FROM telemetria WHERE caravana_id = ?", idCaravana(codigoCaravana));
    }

    /** Último dato de la caravana en la base: fecha, latitud y longitud. */
    protected UltimoDato ultimoDato(String codigoCaravana) {
        return jdbcTemplate.queryForObject("SELECT fecha_hora, latitud, longitud FROM telemetria "
                        + "WHERE caravana_id = ? ORDER BY fecha_hora DESC LIMIT 1",
                (rs, n) -> new UltimoDato(rs.getTimestamp(1).toLocalDateTime(), rs.getDouble(2), rs.getDouble(3)),
                idCaravana(codigoCaravana));
    }

    protected record UltimoDato(LocalDateTime fechaHora, double latitud, double longitud) {
    }

    /** Fecha y hora de la respuesta ({@code LocalDateTime} serializado por Jackson). */
    protected static LocalDateTime fecha(JsonNode valor) {
        return LocalDateTime.parse(valor.asText());
    }
}
