package dev.vigia.backend.dto.ttn;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TTNWebhookRequestDTO — Deserialización del payload real de TTN")
class TTNWebhookRequestDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Payload de ejemplo tomado del formato "Uplink message" documentado por TTN
    // (https://www.thethingsindustries.com/docs/reference/data-formats/), con los
    // campos que TTN agrega de más (application_ids, dev_addr, correlation_ids,
    // rx_metadata, settings, etc.) que el DTO debe ignorar sin romper.
    private static final String UPLINK_REAL_TTN = """
            {
              "end_device_ids": {
                "device_id": "caravana-01",
                "application_ids": {
                  "application_id": "vigia-app"
                },
                "dev_eui": "0004A30B001C0530",
                "join_eui": "0000000000000000",
                "dev_addr": "270FEDA9"
              },
              "correlation_ids": [
                "as:up:01H0000000000000000000000",
                "gs:uplink:01H0000000000000000000000"
              ],
              "received_at": "2026-01-01T00:00:00.123456789Z",
              "uplink_message": {
                "session_key_id": "AXAAAAAAAAAAAAAAAAAAAA==",
                "f_port": 1,
                "f_cnt": 42,
                "frm_payload": "AQIDBAU=",
                "decoded_payload": {
                  "type": "GNSSPosition",
                  "latitude": -34.603722,
                  "longitude": -58.381592,
                  "battery": "87%",
                  "moveState": 1
                },
                "rx_metadata": [
                  {
                    "gateway_ids": {
                      "gateway_id": "gw-01"
                    },
                    "rssi": -80,
                    "snr": 7.5
                  }
                ],
                "settings": {
                  "data_rate": {
                    "lora": {
                      "bandwidth": 125000,
                      "spreading_factor": 7
                    }
                  },
                  "frequency": "868100000"
                },
                "received_at": "2026-01-01T00:00:00.000000000Z"
              }
            }
            """;

    @Test
    @DisplayName("Deserializa dev_eui, decoded_payload, f_cnt y received_at ignorando el resto de los campos de TTN")
    void deserializaUplinkRealDeTTN() throws Exception {
        TTNWebhookRequestDTO dto = objectMapper.readValue(UPLINK_REAL_TTN, TTNWebhookRequestDTO.class);

        assertThat(dto.getEndDeviceIds()).isNotNull();
        assertThat(dto.getEndDeviceIds().getDevEui()).isEqualTo("0004A30B001C0530");

        assertThat(dto.getUplinkMessage()).isNotNull();
        assertThat(dto.getUplinkMessage().getFCnt()).isEqualTo(42);
        assertThat(dto.getUplinkMessage().getSessionKeyId()).isEqualTo("AXAAAAAAAAAAAAAAAAAAAA==");
        assertThat(dto.getUplinkMessage().getReceivedAt()).isEqualTo("2026-01-01T00:00:00.000000000Z");

        TTNWebhookRequestDTO.DecodedPayload decoded = dto.getUplinkMessage().getDecodedPayload();
        assertThat(decoded).isNotNull();
        assertThat(decoded.getType()).isEqualTo("GNSSPosition");
        assertThat(decoded.getLatitude()).isEqualTo(-34.603722);
        assertThat(decoded.getLongitude()).isEqualTo(-58.381592);
        assertThat(decoded.getBattery()).isEqualTo("87%");
        assertThat(decoded.getMoveState()).isEqualTo(1);
    }

    @Test
    @DisplayName("Deserializa un payload envuelto en 'data' (formato alternativo de algunas integraciones de TTN)")
    void deserializaPayloadEnvueltoEnData() throws Exception {
        String envuelto = "{\"data\": " + UPLINK_REAL_TTN + "}";

        TTNWebhookRequestDTO dto = objectMapper.readValue(envuelto, TTNWebhookRequestDTO.class);

        assertThat(dto.getData()).isNotNull();
        assertThat(dto.getData().getEndDeviceIds().getDevEui()).isEqualTo("0004A30B001C0530");
        assertThat(dto.getData().getUplinkMessage().getDecodedPayload().getType()).isEqualTo("GNSSPosition");
    }

    @Test
    @DisplayName("No falla si el payload trae campos completamente desconocidos a nivel raíz")
    void ignoraCamposDesconocidosSinFallar() throws Exception {
        String conCampoExtra = """
                {
                  "end_device_ids": { "dev_eui": "AABBCCDDEEFF0011" },
                  "uplink_message": {
                    "f_cnt": 1,
                    "received_at": "2026-01-01T00:00:00Z",
                    "decoded_payload": { "type": "Heartbeat", "battery": "100%" }
                  },
                  "simulator_source": true,
                  "another_unknown_field": { "nested": "value" }
                }
                """;

        TTNWebhookRequestDTO dto = objectMapper.readValue(conCampoExtra, TTNWebhookRequestDTO.class);

        assertThat(dto.getEndDeviceIds().getDevEui()).isEqualTo("AABBCCDDEEFF0011");
        assertThat(dto.getUplinkMessage().getDecodedPayload().getType()).isEqualTo("Heartbeat");
    }
}
