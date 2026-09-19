package dev.vigia.backend.dto.ttn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TTNWebhookRequestDTO {

    @JsonProperty("end_device_ids")
    private EndDeviceIds endDeviceIds;

    @JsonProperty("uplink_message")
    private UplinkMessage uplinkMessage;

    // A veces se envuelve el mensaje entero en 'data' en algunos formatos de consola, 
    // pero el webhook suele mandar directamente end_device_ids. Lo agregamos para soportar ambos.
    @JsonProperty("data")
    private TTNWebhookRequestDTO data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EndDeviceIds {
        @JsonProperty("dev_eui")
        private String devEui;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UplinkMessage {
        @JsonProperty("decoded_payload")
        private DecodedPayload decodedPayload;
        
        @JsonProperty("received_at")
        private String receivedAt;

        @JsonProperty("f_cnt")
        private Integer fCnt;

        // Cambia en cada join OTAA; el f_cnt reinicia en 0 cuando cambia.
        @JsonProperty("session_key_id")
        private String sessionKeyId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DecodedPayload {
        private String type;
        
        // Heartbeat
        private String battery;
        private Integer moveState;
        
        // Position
        private Double latitude;
        private Double longitude;
    }
}
