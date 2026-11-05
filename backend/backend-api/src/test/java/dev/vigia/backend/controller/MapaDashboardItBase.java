package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vigia.backend.model.Alerta;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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

    // ── Alertas ──────────────────────────────────────────────────────────────

    /**
     * Inserta una alerta sobre un animal del escenario, con los campos obligatorios y los que usan el mapa y el
     * Dashboard. Devuelve el id.
     */
    protected Long insertarAlerta(String establecimiento, String identificador, String tipo, String disparador,
            String severidad, String estado, LocalDateTime tsGeneracion) {
        return jdbcTemplate.queryForObject("INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, "
                        + "valor_umbral, valor_detectado, estado) VALUES (?, ?, ?, ?, ?, 1.0, 1.0, ?) RETURNING id",
                Long.class, idAnimal(establecimiento, identificador), tipo, severidad,
                Timestamp.valueOf(tsGeneracion), disparador, estado);
    }

    /** Cambia el estado de las alertas del animal con ese disparador (las del escenario están en PENDIENTE). */
    protected void cambiarEstadoAlertas(String establecimiento, String identificador, String disparador,
            String estado) {
        jdbcTemplate.update("UPDATE alertas SET estado = ? WHERE animal_id = ? AND disparador = ?", estado,
                idAnimal(establecimiento, identificador), disparador);
    }

    protected Long idAlerta(String establecimiento, String identificador, String disparador) {
        return jdbcTemplate.queryForObject("SELECT id FROM alertas WHERE animal_id = ? AND disparador = ?",
                Long.class, idAnimal(establecimiento, identificador), disparador);
    }

    /**
     * Una alerta de celo generada en {@code tsGeneracion}, con su ventana de inseminación de 6 a 24 h, como la arma
     * CeloDetector.
     */
    protected Long insertarCelo(String establecimiento, String identificador, String tipo, String estado,
            LocalDateTime tsGeneracion) {
        return jdbcTemplate.queryForObject("INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, "
                        + "valor_umbral, valor_detectado, estado, ventana_inicio_ins, ventana_fin_ins, confianza_modelo) "
                        + "VALUES (?, ?, 'ALTA', ?, 'CELO', 0.947, 0.95, ?, ?, ?, 0.95) RETURNING id",
                Long.class, idAnimal(establecimiento, identificador), tipo, Timestamp.valueOf(tsGeneracion), estado,
                Timestamp.valueOf(tsGeneracion.plusHours(6)), Timestamp.valueOf(tsGeneracion.plusHours(24)));
    }

    /**
     * Una alerta ya resuelta (CONFIRMADA o DESCARTADA) en {@code tsConfirmacion}. Se inserta resuelta porque una vez
     * resuelta la base no deja cambiarle la resolución.
     */
    protected Long insertarAlertaResuelta(String establecimiento, String identificador, String tipo,
            String disparador, String estado, LocalDateTime tsGeneracion, LocalDateTime tsConfirmacion) {
        return jdbcTemplate.queryForObject("INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, "
                        + "valor_umbral, valor_detectado, estado, ts_confirmacion) "
                        + "VALUES (?, ?, 'MEDIA', ?, ?, 1.0, 1.0, ?, ?) RETURNING id",
                Long.class, idAnimal(establecimiento, identificador), tipo, Timestamp.valueOf(tsGeneracion),
                disparador, estado, Timestamp.valueOf(tsConfirmacion));
    }

    /** Una alerta de anestro como la arma AnestroDetectorService: disparador CELO, sin ventana de inseminación. */
    protected Long insertarAnestro(String establecimiento, String identificador, String estado,
            LocalDateTime tsGeneracion) {
        return jdbcTemplate.queryForObject("INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, "
                        + "valor_umbral, valor_detectado, estado) VALUES (?, ?, 'MEDIA', ?, 'CELO', 120.0, 150.0, ?) "
                        + "RETURNING id",
                Long.class, idAnimal(establecimiento, identificador), Alerta.TIPO_ANESTRO,
                Timestamp.valueOf(tsGeneracion), estado);
    }

    // ── Estado del animal ────────────────────────────────────────────────────

    /** Perfil de salud calculado: el animal sale de calibración. */
    protected void perfilSalud(String establecimiento, String identificador) {
        jdbcTemplate.update("INSERT INTO perfiles_referencia (animal_id, perfil_json, fecha_calculo) VALUES (?, '{}', ?)",
                idAnimal(establecimiento, identificador), Timestamp.valueOf(ahora().minusDays(1)));
    }

    protected void bateria(String codigoCaravana, double porcentaje) {
        jdbcTemplate.update("UPDATE caravanas SET bateria = ? WHERE codigo = ?", porcentaje, codigoCaravana);
    }

    protected void ultimaSenial(String codigoCaravana, LocalDateTime ultima) {
        jdbcTemplate.update("UPDATE caravanas SET ultima_senial = ? WHERE codigo = ?", Timestamp.valueOf(ultima),
                codigoCaravana);
    }

    /** Estado reproductivo cargado a mano, registrado en {@code registro}; las fechas pueden ser null. */
    protected void estadoReproductivo(String establecimiento, String identificador, String estado, LocalDate parto,
            LocalDate inseminacion, LocalDateTime registro) {
        jdbcTemplate.update("INSERT INTO estados_reproductivos (animal_id, usuario_registro_id, estado, "
                        + "fecha_ultimo_parto, fecha_ultima_inseminacion, origen, fecha_registro) "
                        + "VALUES (?, ?, ?, ?, ?, 'MANUAL', ?)",
                idAnimal(establecimiento, identificador), idUsuario(email("veterinarioA")), estado,
                parto == null ? null : Date.valueOf(parto), inseminacion == null ? null : Date.valueOf(inseminacion),
                Timestamp.valueOf(registro));
    }

    /** Estado de salud registrado por el veterinario en {@code registro}. */
    protected void estadoSalud(String establecimiento, String identificador, String estado, LocalDateTime registro) {
        jdbcTemplate.update("INSERT INTO estados_salud (animal_id, usuario_veterinario_id, fecha_revision, estado, "
                        + "origen, fecha_registro) VALUES (?, ?, ?, ?, 'MANUAL', ?)",
                idAnimal(establecimiento, identificador), idUsuario(email("veterinarioA")),
                Date.valueOf(registro.toLocalDate()), estado, Timestamp.valueOf(registro));
    }

    /** Intervención sobre el animal (sin alerta), registrada por el usuario de esa clave en {@code fechaHora}. */
    protected void intervencion(String establecimiento, String identificador, String tipo, String claveInterventor,
            LocalDateTime fechaHora) {
        jdbcTemplate.update("INSERT INTO intervenciones (animal_id, interventor_id, descripcion, fecha_hora, tipo) "
                        + "VALUES (?, ?, 'Intervención de prueba', ?, ?)",
                idAnimal(establecimiento, identificador), idUsuario(email(claveInterventor)),
                Timestamp.valueOf(fechaHora), tipo);
    }

    /** Rodeo nuevo del establecimiento, sin animales. */
    protected Long crearRodeo(String establecimiento, String nombre, boolean activo) {
        return jdbcTemplate.queryForObject("INSERT INTO rodeo_lote (establecimiento_id, nombre, activo, fecha_alta) "
                        + "VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, idEstablecimiento(establecimiento), nombre, activo ? 1 : 0,
                Timestamp.valueOf(ahora()));
    }

    /** Animal sin caravana en el Rodeo 1 del establecimiento, nacido en {@code nacimiento}. */
    protected void altaAnimalSinCaravana(String establecimiento, String identificador, LocalDate nacimiento) {
        String letra = establecimiento.substring(establecimiento.length() - 1);
        jdbcTemplate.update("INSERT INTO animales (establecimiento_id, rodeo_lote_id, id_caravana_oficial, alias, "
                        + "fecha_nacimiento, raza, estado_actual, fecha_alta) "
                        + "VALUES (?, ?, ?, ?, ?, 'Holando', 'ACTIVO', ?)",
                idEstablecimiento(establecimiento), idRodeo(establecimiento, "Rodeo " + letra + "1"), identificador,
                "Animal " + identificador, Date.valueOf(nacimiento), Date.valueOf(LocalDate.now()));
    }

    /** Le saca la caravana al animal, sin tocar la caravana. */
    protected void sinCaravana(String establecimiento, String identificador) {
        jdbcTemplate.update("UPDATE animales SET caravana_id = NULL WHERE id = ?",
                idAnimal(establecimiento, identificador));
    }

    // ── Dashboard ────────────────────────────────────────────────────────────

    /** GET de una pestaña del Dashboard ({@code centro-mando}, ...) con sus parámetros, verificando el 200. */
    protected JsonNode dashboardOk(String clave, String establecimiento, String pestania, Map<String, Object> params)
            throws Exception {
        MockHttpServletRequestBuilder pedido = get("/api/v1/establecimientos/{id}/dashboard/" + pestania,
                idEstablecimiento(establecimiento)).header("Authorization", bearer(clave));
        params.forEach((nombre, valor) -> pedido.param(nombre, valor.toString()));
        MvcResult resultado = mockMvc.perform(pedido).andReturn();
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        return leer(resultado);
    }

    /** Fecha y hora de la respuesta ({@code LocalDateTime} serializado por Jackson). */
    protected static LocalDateTime fecha(JsonNode valor) {
        return LocalDateTime.parse(valor.asText());
    }
}
