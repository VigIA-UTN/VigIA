package dev.vigia.backend.dto.ml.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fija el contrato JSON entre ml-geo (snake_case), el backend y el frontend (que lee la respuesta tal cual). */
@DisplayName("DTOs de ml-geo — contrato JSON")
class GeoDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("La respuesta de ml-geo se deserializa y se vuelve a serializar con las mismas claves")
    void respuestaIdaYVuelta() throws Exception {
        String mlGeo = """
                {"mapa_calor":[{"lat":-34.0,"lng":-59.0,"intensidad":0.5,"subutilizada":false}],
                 "alertas":[{"animal_id":"1","indice_cohesion":12.5,"alerta_aislamiento":false}],
                 "celdas_subutilizadas":{"lat_step":0.05,"lng_step":0.04,"celdas":[{"lat":-34.975,"lng":-59.975}]}}
                """;

        GeoAnalyzeResponseDTO dto = mapper.readValue(mlGeo, GeoAnalyzeResponseDTO.class);

        assertThat(dto.getCeldasSubutilizadas().getLatStep()).isEqualTo(0.05);
        assertThat(dto.getCeldasSubutilizadas().getLngStep()).isEqualTo(0.04);
        assertThat(dto.getCeldasSubutilizadas().getCeldas()).hasSize(1);

        JsonNode out = mapper.readTree(mapper.writeValueAsString(dto));
        assertThat(out.at("/celdas_subutilizadas/lat_step").asDouble()).isEqualTo(0.05);
        assertThat(out.at("/celdas_subutilizadas/celdas/0/lat").asDouble()).isEqualTo(-34.975);
        assertThat(out.at("/mapa_calor/0/intensidad").asDouble()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Sin zonas: celdas_subutilizadas ausente en ml-geo queda null")
    void sinZonas() throws Exception {
        GeoAnalyzeResponseDTO dto = mapper.readValue("{\"mapa_calor\":[],\"alertas\":[],\"celdas_subutilizadas\":null}",
                GeoAnalyzeResponseDTO.class);

        assertThat(dto.getCeldasSubutilizadas()).isNull();
    }

    @Test
    @DisplayName("El request a ml-geo usa animal_ids e historial_7d")
    void request() throws Exception {
        GeoAnalyzeRequestDTO request = GeoAnalyzeRequestDTO.builder()
                .animalIds(List.of("1", "2"))
                .historial7d(List.of(CoordenadaGPSDTO.builder().animalId("1").lat(-34.0).lng(-59.0).timestamp("t").build()))
                .build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(request));

        assertThat(json.get("animal_ids")).hasSize(2);
        assertThat(json.get("historial_7d")).hasSize(1);
        assertThat(json.get("animal_id").isNull()).isTrue();
    }
}
