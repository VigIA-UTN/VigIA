package dev.vigia.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.TelemetriaRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;

/**
 * RNF02 (defecto del Ciclo 1, fila 431): el mapa pide la última posición de cada caravana. Con {@code DISTINCT ON}
 * dependía de que TimescaleDB eligiera SkipScan; cuando no lo hacía (como con estos datos: 2.880 filas para 3
 * caravanas) leía todo el historial de cada una, y en producción hay 14 días. Cada caravana tiene que costar una sola
 * fila del índice. El primer dato desde la asignación (lista y mapa, solo animales en calibración) se prueba acá
 * contra la base real porque los tests con H2 no cubren TimescaleDB.
 * <p>
 * Usa las caravanas de Fixture A, que transmiten cada 5 min.
 */
@DisplayName("RNF02 - Última posición y primer dato por caravana — IT")
class TelemetriaPorCaravanaIT extends MapaDashboardItBase {

    private static final List<String> CARAVANAS = List.of("FX-A-01", "FX-A-02", "FX-A-03");

    @Autowired
    private TelemetriaRepository telemetriaRepository;

    @Test
    @DisplayName("La última posición de cada caravana es su dato más reciente")
    void ultimaPorCaravana() {
        Map<Long, Telemetria> ultima = telemetriaRepository.findUltimaPorCaravana(ids()).stream()
                .collect(Collectors.toMap(t -> t.getCaravana().getId(), t -> t));

        assertThat(ultima).hasSize(CARAVANAS.size());
        for (String codigo : CARAVANAS) {
            UltimoDato esperado = ultimoDato(codigo);
            Telemetria t = ultima.get(idCaravana(codigo));
            assertThat(t.getFechaHora()).as(codigo).isEqualTo(esperado.fechaHora());
            assertThat(t.getLatitud()).as(codigo).isEqualTo(esperado.latitud());
            assertThat(t.getLongitud()).as(codigo).isEqualTo(esperado.longitud());
        }
    }

    @Test
    @DisplayName("El primer dato cuenta desde la asignación de la caravana")
    void primerDatoDesdeAsignacion() {
        // FX-A-01 asignada ayer: lo anterior puede ser de otro animal. Las otras, sin fecha de asignación.
        jdbcTemplate.update("UPDATE caravanas SET fecha_asignacion = current_date - 1 WHERE codigo = 'FX-A-01'");
        jdbcTemplate.update("UPDATE caravanas SET fecha_asignacion = NULL WHERE codigo IN ('FX-A-02', 'FX-A-03')");

        Map<Long, LocalDateTime> primero = telemetriaRepository.findPrimerDatoDesdeAsignacion(ids()).stream()
                .collect(Collectors.toMap(TelemetriaRepository.PrimerDato::getCaravanaId,
                        TelemetriaRepository.PrimerDato::getFechaHora));

        assertThat(primero).hasSize(CARAVANAS.size());
        for (String codigo : CARAVANAS) {
            LocalDateTime esperado = jdbcTemplate.queryForObject("SELECT min(t.fecha_hora) FROM telemetria t "
                            + "JOIN caravanas c ON c.id = t.caravana_id WHERE c.codigo = ? "
                            + "AND t.fecha_hora >= coalesce(c.fecha_asignacion::timestamp, '1970-01-01')",
                    LocalDateTime.class, codigo);
            assertThat(primero.get(idCaravana(codigo))).as(codigo).isEqualTo(esperado);
        }
        assertThat(primero.get(idCaravana("FX-A-01"))).isAfterOrEqualTo(LocalDateTime.now().toLocalDate()
                .minusDays(1).atStartOfDay());
    }

    @Test
    @DisplayName("La última posición no lee el historial: una fila de telemetría por caravana")
    void ultimaPorCaravanaLeeUnaFilaPorCaravana() throws Exception {
        assertThat(filasDeTelemetriaLeidas("findUltimaPorCaravana")).isBetween(1L, (long) CARAVANAS.size());
    }

    private List<Long> ids() {
        return CARAVANAS.stream().map(this::idCaravana).toList();
    }

    /** Corre la consulta del repositorio con EXPLAIN ANALYZE y suma las filas que devolvieron los chunks. */
    private long filasDeTelemetriaLeidas(String metodo) throws Exception {
        String sql = TelemetriaRepository.class.getMethod(metodo, Collection.class).getAnnotation(Query.class).value()
                .replace(":caravanaIds", ids().stream().map(String::valueOf).collect(Collectors.joining(", ")));
        String plan = jdbcTemplate.queryForObject("EXPLAIN (ANALYZE, FORMAT JSON) " + sql, String.class);
        return filasDeChunks(objectMapper.readTree(plan).get(0).get("Plan"));
    }

    private long filasDeChunks(JsonNode nodo) {
        long filas = 0;
        if (nodo.path("Relation Name").asText().startsWith("_hyper_")) {
            filas += nodo.get("Actual Rows").asLong() * nodo.get("Actual Loops").asLong();
        }
        for (JsonNode hijo : nodo.path("Plans")) {
            filas += filasDeChunks(hijo);
        }
        return filas;
    }
}
