package dev.vigia.backend.dto.ml.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DTOs de ml-geo — contrato JSON")
class GeoDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("La respuesta de ml-geo se deserializa y se vuelve a serializar con las mismas claves")
    void respuestaIdaYVuelta() throws Exception {
        String mlGeo = """
                {"mapa_calor":[{"lat":-34.0,"lng":-59.0,"intensidad":0.5}],
                 "alertas":[{"animal_id":"1","indice_cohesion":0.25,"distancia_centroide_m":300.0,"alerta_aislamiento":true}]}
                """;

        GeoAnalyzeResponseDTO dto = mapper.readValue(mlGeo, GeoAnalyzeResponseDTO.class);

        assertThat(dto.getMapaCalor()).hasSize(1);
        assertThat(dto.getAlertas().get(0).getIndiceCohesion()).isEqualTo(0.25);
        assertThat(dto.getAlertas().get(0).getDistanciaCentroideM()).isEqualTo(300.0);

        JsonNode out = mapper.readTree(mapper.writeValueAsString(dto));
        assertThat(out.at("/mapa_calor/0/intensidad").asDouble()).isEqualTo(0.5);
        assertThat(out.at("/alertas/0/distancia_centroide_m").asDouble()).isEqualTo(300.0);
        assertThat(out.at("/mapa_calor/0").has("subutilizada")).isFalse();
    }

    @Test
    @DisplayName("El request a ml-geo usa animal_ids e historial_7d; umbral_aislamiento_m es null cuando no se setea")
    void request() throws Exception {
        GeoAnalyzeRequestDTO request = GeoAnalyzeRequestDTO.builder()
                .animalIds(List.of("1", "2"))
                .historial7d(
                        List.of(CoordenadaGPSDTO.builder().animalId("1").lat(-34.0).lng(-59.0).timestamp("t").build()))
                .build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(request));

        assertThat(json.get("animal_ids")).hasSize(2);
        assertThat(json.get("historial_7d")).hasSize(1);
        assertThat(json.get("animal_id").isNull()).isTrue();
        assertThat(json.get("umbral_aislamiento_m").isNull()).isTrue();
    }

    @Test
    @DisplayName("umbral_aislamiento_m se serializa correctamente cuando se setea")
    void requestConUmbral() throws Exception {
        GeoAnalyzeRequestDTO request = GeoAnalyzeRequestDTO.builder()
                .historial7d(List.of())
                .umbralAislamientoM(150.0)
                .build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(request));

        assertThat(json.get("umbral_aislamiento_m").asDouble()).isEqualTo(150.0);
    }
}
