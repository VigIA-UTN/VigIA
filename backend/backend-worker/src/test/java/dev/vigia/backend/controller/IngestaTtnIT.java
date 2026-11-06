package dev.vigia.backend.controller;

import dev.vigia.backend.IntegracionBase;
import dev.vigia.backend.fixture.EscenarioFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * INT01 - Contrato entre The Things Network y el backend-worker: el webhook {@code POST /api/v1/ingesta/uplink} sobre
 * TimescaleDB y RabbitMQ reales (Testcontainers).
 * <p>
 * Cada test parte del escenario de {@code fixtures/escenario} recién cargado y manda los uplinks a la caravana de
 * A-103 (FX-A-03). Ese animal está en un rodeo sin perímetro, así que las posiciones no generan alertas de perímetro
 * que ensucien la base compartida.
 * <p>
 * Cuando el webhook ignora un paquete responde 200 con el motivo en el cuerpo: TTN reintenta los envíos que no reciben
 * 2xx, y un paquete que no se va a procesar no tiene que volver a llegar.
 * <p>
 * Los nombres de los tests llevan el caso de la hoja "Ciclo 1" del documento de Casos de Prueba (INT01-01 a INT01-06).
 */
@AutoConfigureMockMvc
@DisplayName("INT01 - Webhook TTN e ingesta (idempotencia por Frame Counter) — IT")
class IngestaTtnIT extends IntegracionBase {

    private static final String URL = "/api/v1/ingesta/uplink";
    /** La de src/test/resources/application.yaml (vigia.ttn.webhook.api-key). */
    private static final String API_KEY = "test-api-key";
    private static final String DEV_EUI = "FX-A-03";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private Long caravanaId;

    @BeforeEach
    void prepararEscenario() {
        EscenarioFixture.cargar(dataSource);
        caravanaId = jdbcTemplate.queryForObject("SELECT id FROM caravanas WHERE dev_eui = ?", Long.class, DEV_EUI);
    }

    @Test
    @DisplayName("INT01-01 - Uplink válido - Persiste la telemetría y actualiza la caravana")
    void INT01_01_uplinkValidoPersisteYActualizaCaravana() throws Exception {
        int antes = telemetrias();
        Instant recibido = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        enviar(posicion(DEV_EUI, 10, null, recibido, -36.61, -64.29, 3, "76%"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        assertThat(telemetrias()).isEqualTo(antes + 1);
        Map<String, Object> lectura = jdbcTemplate.queryForMap("SELECT * FROM telemetria WHERE caravana_id = ? "
                + "ORDER BY fecha_hora DESC LIMIT 1", caravanaId);
        LocalDateTime fechaEsperada = LocalDateTime.ofInstant(recibido, java.time.ZoneId.systemDefault());
        assertThat(lectura.get("fecha_hora")).isEqualTo(java.sql.Timestamp.valueOf(fechaEsperada));
        assertThat(lectura.get("latitud")).isEqualTo(-36.61);
        assertThat(lectura.get("longitud")).isEqualTo(-64.29);
        assertThat(lectura.get("move_state")).isEqualTo(3);
        assertThat(lectura.get("bateria")).isEqualTo(76.0);
        assertThat(lectura.get("estado_inferencia_celo")).isEqualTo("PENDIENTE");
        assertThat(lectura.get("estado_inferencia_salud")).isEqualTo("PENDIENTE");

        Map<String, Object> caravana = caravana();
        assertThat(caravana.get("ultimo_frame_counter")).isEqualTo(10);
        assertThat(caravana.get("ultima_senial")).isEqualTo(java.sql.Timestamp.valueOf(fechaEsperada));
        assertThat(caravana.get("bateria")).isEqualTo(76.0);
    }

    @Test
    @DisplayName("INT01-02 - Idempotencia - El mismo paquete enviado dos veces no repite registros")
    void INT01_02_mismoFrameCounterNoRepiteRegistros() throws Exception {
        int antes = telemetrias();
        String paquete = posicion(DEV_EUI, 20, null, Instant.now(), -36.61, -64.29, 2, "70%");

        enviar(paquete).andExpect(status().isOk()).andExpect(content().string("OK"));
        Map<String, Object> caravanaTrasElPrimero = caravana();

        // TTN reintenta el mismo uplink (mismo f_cnt) si no le llegó la respuesta del primero.
        enviar(paquete)
                .andExpect(status().isOk())
                .andExpect(content().string("Ignorado: frame duplicado (idempotencia)"));

        assertThat(telemetrias()).isEqualTo(antes + 1);
        assertThat(caravana()).isEqualTo(caravanaTrasElPrimero);

        // Un f_cnt menor que llega tarde (fuera de orden) también se descarta.
        enviar(posicion(DEV_EUI, 19, null, Instant.now(), -36.62, -64.30, 2, "70%"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ignorado: frame duplicado (idempotencia)"));
        assertThat(telemetrias()).isEqualTo(antes + 1);
    }

    /**
     * Comportamiento implementado ante un reinicio del Frame Counter: la caravana guarda el último f_cnt y el
     * session_key_id de la sesión LoRaWAN en la que llegó.
     * <ul>
     * <li>Con un session_key_id nuevo (rejoin OTAA, que reinicia el f_cnt) es una sesión nueva: el paquete se procesa
     * aunque su f_cnt sea menor y el contador vuelve a empezar desde ahí.</li>
     * <li>Sin cambio de sesión, un f_cnt menor o igual al último se trata como duplicado: los paquetes se descartan
     * hasta que el contador supere al que estaba guardado.</li>
     * </ul>
     */
    @Test
    @DisplayName("INT01-03 - Reinicio del Frame Counter - Sesión nueva se procesa; misma sesión se descarta")
    void INT01_03_reinicioDelFrameCounter() throws Exception {
        enviar(posicion(DEV_EUI, 500, "sesion-1", Instant.now(), -36.61, -64.29, 2, "70%"))
                .andExpect(content().string("OK"));
        int antes = telemetrias();

        // Misma sesión y el contador vuelve a 1: se descarta como duplicado.
        enviar(posicion(DEV_EUI, 1, "sesion-1", Instant.now(), -36.62, -64.30, 2, "70%"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ignorado: frame duplicado (idempotencia)"));
        assertThat(telemetrias()).isEqualTo(antes);
        assertThat(caravana().get("ultimo_frame_counter")).isEqualTo(500);

        // Rejoin: sesión nueva con el contador en 1. Se procesa y el contador arranca de nuevo.
        enviar(posicion(DEV_EUI, 1, "sesion-2", Instant.now(), -36.62, -64.30, 2, "70%"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
        assertThat(telemetrias()).isEqualTo(antes + 1);
        assertThat(caravana().get("ultimo_frame_counter")).isEqualTo(1);
        assertThat(caravana().get("ultima_session_key_id")).isEqualTo("sesion-2");

        // En la sesión nueva la idempotencia sigue: el 1 repetido se descarta y el 2 entra.
        enviar(posicion(DEV_EUI, 1, "sesion-2", Instant.now(), -36.62, -64.30, 2, "70%"))
                .andExpect(content().string("Ignorado: frame duplicado (idempotencia)"));
        enviar(posicion(DEV_EUI, 2, "sesion-2", Instant.now(), -36.63, -64.31, 2, "70%"))
                .andExpect(content().string("OK"));
        assertThat(telemetrias()).isEqualTo(antes + 2);
    }

    @Test
    @DisplayName("INT01-04 - API key - Sin API key o con una inválida devuelve 401 y no persiste nada")
    void INT01_04_apiKeyAusenteOInvalida() throws Exception {
        int antes = telemetrias();
        Map<String, Object> caravanaAntes = caravana();
        String paquete = posicion(DEV_EUI, 30, null, Instant.now(), -36.61, -64.29, 2, "70%");

        // Sin header, con otra clave y con la clave correcta pero sin el esquema Bearer.
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(paquete))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(paquete)
                .header("Authorization", "Bearer otra-clave"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(paquete)
                .header("Authorization", API_KEY))
                .andExpect(status().isUnauthorized());

        assertThat(telemetrias()).isEqualTo(antes);
        assertThat(caravana()).isEqualTo(caravanaAntes);

        // El mismo paquete con la clave correcta sí entra: el rechazo no lo dejó marcado como procesado.
        enviar(paquete).andExpect(status().isOk()).andExpect(content().string("OK"));
        assertThat(telemetrias()).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("INT01-05 - Dispositivo desconocido - Se ignora sin persistir y la ingesta sigue")
    void INT01_05_dispositivoDesconocido() throws Exception {
        int totalAntes = telemetriasTotales();

        // dev_eui que no existe y caravana libre (registrada pero sin animal).
        enviar(posicion("DEV-INEXISTENTE", 1, null, Instant.now(), -36.61, -64.29, 2, "70%"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ignorado: Dispositivo no registrado"));
        enviar(posicion("FX-A-L1", 1, null, Instant.now(), -36.61, -64.29, 2, "70%"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ignorado: Sin animal asociado"));

        assertThat(telemetriasTotales()).isEqualTo(totalAntes);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM caravanas WHERE dev_eui = 'DEV-INEXISTENTE'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT ultimo_frame_counter FROM caravanas WHERE dev_eui = 'FX-A-L1'",
                Integer.class)).isNull();

        // Un paquete válido de una caravana registrada se procesa igual después.
        enviar(posicion(DEV_EUI, 40, null, Instant.now(), -36.61, -64.29, 2, "70%"))
                .andExpect(content().string("OK"));
        assertThat(telemetriasTotales()).isEqualTo(totalAntes + 1);
    }

    @Test
    @DisplayName("INT01-06 - Payload mal formado - Se rechaza sin persistir y la ingesta sigue")
    void INT01_06_payloadMalFormado() throws Exception {
        int antes = telemetrias();
        Map<String, Object> caravanaAntes = caravana();

        // JSON roto y un campo con un tipo que no corresponde: 400.
        enviar("{\"end_device_ids\": {\"dev_eui\": \"" + DEV_EUI + "\"}, \"uplink_message\": ")
                .andExpect(status().isBadRequest());
        enviar(posicion(DEV_EUI, 50, null, Instant.now(), -36.61, -64.29, 2, "70%")
                .replace("\"f_cnt\": 50", "\"f_cnt\": \"cincuenta\""))
                .andExpect(status().isBadRequest());

        // JSON válido pero incompleto o con contenido que no se puede usar: 200 con el motivo.
        enviar("{\"uplink_message\": {\"f_cnt\": 51}}")
                .andExpect(status().isOk())
                .andExpect(content().string("Ignorado: Sin dev_eui"));
        enviar("{\"end_device_ids\": {\"dev_eui\": \"" + DEV_EUI + "\"}, \"uplink_message\": {\"f_cnt\": 52}}")
                .andExpect(status().isOk())
                .andExpect(content().string("Ignorado: Sin decoded_payload"));
        enviar(posicion(DEV_EUI, 53, null, Instant.now(), -36.61, -64.29, 2, "70%")
                .replace("GNSSPosition", "Desconocido"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ignorado: Tipo desconocido"));
        enviar(posicion(DEV_EUI, 54, null, Instant.now(), 0.0, 0.0, 2, "70%"))
                .andExpect(status().isOk())
                .andExpect(content().string("Ignorado: fix GNSS fallido"));

        assertThat(telemetrias()).isEqualTo(antes);
        assertThat(caravana()).isEqualTo(caravanaAntes);

        // La ingesta no quedó trabada: el siguiente paquete válido entra.
        enviar(posicion(DEV_EUI, 55, null, Instant.now(), -36.61, -64.29, 2, "70%"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
        assertThat(telemetrias()).isEqualTo(antes + 1);
        assertThat(caravana().get("ultimo_frame_counter")).isEqualTo(55);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions enviar(String cuerpo) throws Exception {
        MockHttpServletRequestBuilder pedido = post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + API_KEY)
                .content(cuerpo);
        return mockMvc.perform(pedido);
    }

    /** Uplink de posición GNSS como lo manda TTN, con los campos que lee el webhook. */
    private static String posicion(String devEui, int fCnt, String sesion, Instant recibido, double lat, double lng,
            int moveState, String bateria) {
        String campoSesion = sesion != null ? "\"session_key_id\": \"" + sesion + "\", " : "";
        return """
                {
                  "end_device_ids": {"device_id": "%s", "dev_eui": "%s"},
                  "uplink_message": {
                    %s"f_cnt": %d,
                    "received_at": "%s",
                    "decoded_payload": {
                      "type": "GNSSPosition",
                      "latitude": %s,
                      "longitude": %s,
                      "moveState": %d,
                      "battery": "%s"
                    }
                  }
                }
                """.formatted(devEui.toLowerCase(), devEui, campoSesion, fCnt, recibido, lat, lng, moveState, bateria);
    }

    private int telemetrias() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM telemetria WHERE caravana_id = ?", Integer.class,
                caravanaId);
    }

    private int telemetriasTotales() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM telemetria", Integer.class);
    }

    private Map<String, Object> caravana() {
        return jdbcTemplate.queryForMap("SELECT ultimo_frame_counter, ultima_session_key_id, ultima_senial, bateria "
                + "FROM caravanas WHERE id = ?", caravanaId);
    }
}
