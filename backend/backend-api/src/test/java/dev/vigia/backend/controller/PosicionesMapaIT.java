package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.TelemetriaEventDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sobre el escenario: las seis vacas de Fixture A transmiten cada 5 min; A-101 a A-105 tienen su último dato al
 * momento de la carga y A-106 hace 3 h. {@code /animales/mapa} devuelve la última posición de cada animal activo y
 * marca "sin datos recientes" a los que no transmitieron en los últimos 15 min; {@code /animales/mapa/stream} reenvía
 * por SSE cada telemetría nueva que el worker publica en {@code telemetria.exchange}.
 * <p>
 * El ícono de cada animal y el popup con sus datos básicos son del frontend (N/A en la API).
 */
@DisplayName("H0012 - Ver mapa de posiciones del rodeo en tiempo real — IT")
class PosicionesMapaIT extends MapaDashboardItBase {

    private static final long ESPERA_SSE_MS = 5000;

    private static final List<String> ANIMALES_A = List.of("A-101", "A-102", "A-103", "A-104", "A-105", "A-106");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    @DisplayName("H0012-01 - Mapa de posiciones - Última posición de cada animal")
    void H0012_01_ultimaPosicionDeCadaAnimal() throws Exception {
        // Un animal dado de baja no figura en el mapa.
        jdbcTemplate.update("UPDATE animales SET estado_actual = 'INACTIVO' WHERE id = ?", idAnimal(EST_A, "A-105"));

        JsonNode mapa = mapaOk("productorA", EST_A);

        Set<String> identificadores = new TreeSet<>();
        mapa.forEach(a -> identificadores.add(a.get("identificador").asText()));
        assertThat(identificadores).containsExactly("A-101", "A-102", "A-103", "A-104", "A-106");

        for (String identificador : identificadores) {
            String caravana = "FX-A-" + identificador.substring(3);
            UltimoDato esperado = ultimoDato(caravana);
            JsonNode animal = animalDelMapa(mapa, identificador);
            assertThat(animal.get("id").asLong()).isEqualTo(idAnimal(EST_A, identificador));
            assertThat(animal.get("latitud").asDouble()).as(identificador).isEqualTo(esperado.latitud());
            assertThat(animal.get("longitud").asDouble()).as(identificador).isEqualTo(esperado.longitud());
            assertThat(fecha(animal.get("tsUltimaPosicion"))).as(identificador).isEqualTo(esperado.fechaHora());
        }
    }

    @Test
    @DisplayName("H0012-02 - Mapa de posiciones - Actualización ante nueva telemetría")
    void H0012_02_actualizacionAnteNuevaTelemetria() throws Exception {
        UltimoDato anterior = ultimoDato("FX-A-01");
        LocalDateTime nueva = ahora();
        insertarTelemetria("FX-A-01", nueva, anterior.latitud() + 0.001, anterior.longitud() + 0.001);
        // Un frame atrasado (más viejo que el último) no pisa la posición.
        insertarTelemetria("FX-A-01", nueva.minusHours(1), anterior.latitud() - 0.01, anterior.longitud() - 0.01);

        JsonNode animal = animalDelMapa(mapaOk("veterinarioA", EST_A), "A-101");

        assertThat(animal.get("latitud").asDouble()).isEqualTo(anterior.latitud() + 0.001);
        assertThat(animal.get("longitud").asDouble()).isEqualTo(anterior.longitud() + 0.001);
        assertThat(fecha(animal.get("tsUltimaPosicion"))).isEqualTo(nueva);
        assertThat(animal.get("sinDatosRecientes").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("H0012-03 - Mapa de posiciones - Posición en tiempo real por SSE")
    void H0012_03_posicionEnTiempoRealPorSse() throws Exception {
        Long estA = idEstablecimiento(EST_A);
        Long estB = idEstablecimiento(EST_B);
        MvcResult stream = mockMvc.perform(get("/api/v1/animales/mapa/stream")
                        .param("establecimientoId", estA.toString())
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", bearer("camperoA")))
                .andReturn();
        assertThat(stream.getResponse().getStatus()).isEqualTo(200);
        assertThat(stream.getRequest().isAsyncStarted()).isTrue();

        // Como la ingesta del worker: primero una de Fixture B (no le llega a A) y después una de A-102.
        Long animalB = idAnimal(EST_B, "B-101");
        Long animalA = idAnimal(EST_A, "A-102");
        LocalDateTime fechaHora = ahora();
        publicar(TelemetriaEventDTO.builder().animalId(animalB).establecimientoId(estB)
                .latitud(-34.1).longitud(-58.1).fechaHora(fechaHora).build());
        publicar(TelemetriaEventDTO.builder().animalId(animalA).establecimientoId(estA)
                .latitud(-34.2).longitud(-58.2).fechaHora(fechaHora).build());

        List<JsonNode> eventos = esperarEventos(stream, 1);
        assertThat(eventos).hasSize(1);
        JsonNode evento = eventos.get(0);
        assertThat(evento.get("animalId").asLong()).isEqualTo(animalA);
        assertThat(evento.get("establecimientoId").asLong()).isEqualTo(estA);
        assertThat(evento.get("latitud").asDouble()).isEqualTo(-34.2);
        assertThat(evento.get("longitud").asDouble()).isEqualTo(-58.2);
        assertThat(fecha(evento.get("fechaHora"))).isEqualTo(fechaHora);
    }

    @Test
    @DisplayName("H0012-04 - Mapa de posiciones - Sin datos recientes (> 15 min)")
    void H0012_04_sinDatosRecientes() throws Exception {
        // A-102 con el último dato hace 20 min y A-103 hace 10 min, a cada lado del umbral de 15.
        borrarTelemetria("FX-A-02");
        insertarTelemetria("FX-A-02", ahora().minusMinutes(20), -34.5, -58.5);
        borrarTelemetria("FX-A-03");
        insertarTelemetria("FX-A-03", ahora().minusMinutes(10), -34.6, -58.6);

        JsonNode mapa = mapaOk("productorA", EST_A);

        JsonNode hace20 = animalDelMapa(mapa, "A-102");
        assertThat(hace20.get("sinDatosRecientes").asBoolean()).isTrue();
        assertThat(hace20.get("latitud").asDouble()).isEqualTo(-34.5);
        assertThat(hace20.get("longitud").asDouble()).isEqualTo(-58.5);

        JsonNode hace10 = animalDelMapa(mapa, "A-103");
        assertThat(hace10.get("sinDatosRecientes").asBoolean()).isFalse();

        // A-106 (último dato hace 3 h, del escenario) en su última posición conocida.
        UltimoDato ultimo106 = ultimoDato("FX-A-06");
        JsonNode sinDatos = animalDelMapa(mapa, "A-106");
        assertThat(sinDatos.get("sinDatosRecientes").asBoolean()).isTrue();
        assertThat(sinDatos.get("latitud").asDouble()).isEqualTo(ultimo106.latitud());
        assertThat(sinDatos.get("longitud").asDouble()).isEqualTo(ultimo106.longitud());

        for (String identificador : List.of("A-101", "A-104", "A-105")) {
            assertThat(animalDelMapa(mapa, identificador).get("sinDatosRecientes").asBoolean())
                    .as(identificador).isFalse();
        }
    }

    @Test
    @DisplayName("H0012-05 - Mapa de posiciones - Animal sin caravana o sin telemetría")
    void H0012_05_sinCaravanaOSinTelemetria() throws Exception {
        // A-101 sin caravana; A-102 con una caravana que todavía no transmitió (FX-A-L1).
        jdbcTemplate.update("UPDATE animales SET caravana_id = NULL WHERE id = ?", idAnimal(EST_A, "A-101"));
        jdbcTemplate.update("UPDATE animales SET caravana_id = ? WHERE id = ?", idCaravana("FX-A-L1"),
                idAnimal(EST_A, "A-102"));

        JsonNode mapa = mapaOk("productorA", EST_A);

        assertThat(mapa).hasSize(ANIMALES_A.size());
        for (String identificador : List.of("A-101", "A-102")) {
            JsonNode animal = animalDelMapa(mapa, identificador);
            assertThat(animal.get("sinDatosRecientes").asBoolean()).as(identificador).isTrue();
            assertThat(animal.get("latitud").isNull()).as(identificador).isTrue();
            assertThat(animal.get("longitud").isNull()).as(identificador).isTrue();
            assertThat(animal.get("tsUltimaPosicion").isNull()).as(identificador).isTrue();
        }
    }

    // ── SSE ──────────────────────────────────────────────────────────────────

    private void publicar(TelemetriaEventDTO evento) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.TELEMETRIA_EXCHANGE, "", evento);
    }

    /**
     * Espera hasta que el stream tenga {@code cantidad} eventos (los heartbeats son comentarios y no cuentan) y deja
     * un margen por si llega alguno de más.
     */
    private List<JsonNode> esperarEventos(MvcResult stream, int cantidad) throws Exception {
        long limite = System.currentTimeMillis() + ESPERA_SSE_MS;
        List<JsonNode> eventos = eventos(stream);
        while (eventos.size() < cantidad && System.currentTimeMillis() < limite) {
            Thread.sleep(100);
            eventos = eventos(stream);
        }
        Thread.sleep(300);
        return eventos(stream);
    }

    private List<JsonNode> eventos(MvcResult stream) throws Exception {
        List<JsonNode> eventos = new ArrayList<>();
        for (String linea : stream.getResponse().getContentAsString(StandardCharsets.UTF_8).split("\n")) {
            if (linea.startsWith("data:")) {
                eventos.add(objectMapper.readTree(linea.substring("data:".length())));
            }
        }
        return eventos;
    }
}
