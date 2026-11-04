package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vigia.backend.client.MlGeoClient;
import dev.vigia.backend.dto.ml.geo.AlertaAnimalDTO;
import dev.vigia.backend.dto.ml.geo.CoordenadaGPSDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeRequestDTO;
import dev.vigia.backend.dto.ml.geo.GeoAnalyzeResponseDTO;
import dev.vigia.backend.dto.ml.geo.MatrizDensidadDTO;
import dev.vigia.backend.exception.MlGeoDegradadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * El mapa de calor lo calcula ml-geo; la API elige qué telemetría le manda (establecimiento, rango de fechas, rodeo o
 * animal) y completa los animales alejados que ml-geo marca. Por eso ml-geo está mockeado: se verifica, con un captor,
 * la telemetría que le llega, y que la respuesta de ml-geo vuelva al cliente.
 * <p>
 * Sobre el escenario: A-101 tiene 8 días de telemetría cada 5 min y el resto de Fixture A las últimas 24 h; Rodeo A1
 * tiene a A-101, A-102, A-104, A-105 y A-106, y Rodeo A2 solo a A-103.
 * <p>
 * La densidad (intensidad de cada celda) es de ml-geo, y el selector de período y de animal o rodeo, del frontend (N/A
 * en la API).
 */
@DisplayName("H0025 - Ver mapa de calor de zonas de pastoreo — IT")
class MapaCalorIT extends MapaDashboardItBase {

    @MockBean
    private MlGeoClient mlGeoClient;

    @BeforeEach
    void respuestaVaciaDeMlGeo() {
        reset(mlGeoClient);
        when(mlGeoClient.analyze(any())).thenReturn(respuestaMlGeo(List.of(), List.of()));
    }

    @Test
    @DisplayName("H0025-01 - Mapa de calor - Por rango de fechas")
    void H0025_01_porRangoDeFechas() throws Exception {
        // a) Hace 3 días a hace 28 h: solo A-101 tiene datos tan viejos (lo más viejo de A-106 es de hace ~27 h).
        LocalDateTime desde = ahora().minusDays(3);
        LocalDateTime hasta = ahora().minusHours(28);
        MvcResult resultado = mapaCalor("productorA", EST_A, desde, hasta, Map.of());
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);

        GeoAnalyzeRequestDTO enviado = enviadoAMlGeo();
        assertThat(enviado.getAnimalId()).isNull();
        assertThat(enviado.getUmbralAislamientoM()).isEqualTo(100.0);
        assertThat(identificadores(enviado)).containsExactly("A-101");
        assertDentroDelRango(enviado, desde, hasta);
        assertThat(enviado.getHistorial7d()).hasSize(cantidadTelemetria(EST_A, desde, hasta, null));

        // b) Sin fechas: los últimos 7 días, con los seis animales.
        reset(mlGeoClient);
        when(mlGeoClient.analyze(any())).thenReturn(respuestaMlGeo(List.of(), List.of()));
        LocalDateTime antes = ahora();
        resultado = mockMvc.perform(get("/api/v1/mapa-calor")
                        .param("establecimientoId", idEstablecimiento(EST_A).toString())
                        .header("Authorization", bearer("productorA")))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        enviado = enviadoAMlGeo();
        assertThat(identificadores(enviado)).containsExactly("A-101", "A-102", "A-103", "A-104", "A-105", "A-106");
        assertDentroDelRango(enviado, antes.minusDays(7), ahora().plusSeconds(1));

        // c) Rango invertido y d) otro establecimiento.
        assertThat(mapaCalor("productorA", EST_A, hasta, desde, Map.of()).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(mapaCalor("productorB", EST_A, desde, hasta, Map.of()).getResponse().getStatus())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("H0025-02 - Mapa de calor - Por rodeo")
    void H0025_02_porRodeo() throws Exception {
        LocalDateTime desde = ahora().minusDays(2);
        LocalDateTime hasta = ahora();
        Long rodeoA2 = idRodeo(EST_A, "Rodeo A2");

        MvcResult resultado = mapaCalor("veterinarioA", EST_A, desde, hasta, Map.of("rodeoId", rodeoA2));
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        GeoAnalyzeRequestDTO enviado = enviadoAMlGeo();
        assertThat(identificadores(enviado)).containsExactly("A-103");
        assertThat(enviado.getHistorial7d()).hasSize(cantidadTelemetria(EST_A, desde, hasta, "A-103"));
        assertDentroDelRango(enviado, desde, hasta);

        // Un rodeo de otro establecimiento no trae nada y no se consulta a ml-geo.
        reset(mlGeoClient);
        resultado = mapaCalor("veterinarioA", EST_A, desde, hasta, Map.of("rodeoId", idRodeo(EST_B, "Rodeo B1")));
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        assertThat(leer(resultado).get("mapa_calor")).isEmpty();
        verify(mlGeoClient, never()).analyze(any());

        // Animal y rodeo a la vez no se permite.
        resultado = mapaCalor("veterinarioA", EST_A, desde, hasta,
                Map.of("rodeoId", rodeoA2, "animalId", idAnimal(EST_A, "A-103")));
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("H0025-03 - Mapa de calor - Por animal")
    void H0025_03_porAnimal() throws Exception {
        LocalDateTime desde = ahora().minusDays(2);
        LocalDateTime hasta = ahora();
        Long animal = idAnimal(EST_A, "A-101");

        MvcResult resultado = mapaCalor("camperoA", EST_A, desde, hasta, Map.of("animalId", animal));
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);

        // ml-geo recibe el animal a dibujar y la telemetría de su rodeo (Rodeo A1), que usa como contexto del grupo.
        GeoAnalyzeRequestDTO enviado = enviadoAMlGeo();
        assertThat(enviado.getAnimalId()).isEqualTo(animal.toString());
        assertThat(identificadores(enviado)).containsExactly("A-101", "A-102", "A-104", "A-105", "A-106");
        assertDentroDelRango(enviado, desde, hasta);
    }

    @Test
    @DisplayName("H0025-04 - Mapa de calor - Respuesta de ml-geo y ml-geo degradado")
    void H0025_04_respuestaDeMlGeoYDegradado() throws Exception {
        LocalDateTime desde = ahora().minusDays(1);
        LocalDateTime hasta = ahora();
        String a104 = idAnimal(EST_A, "A-104").toString();
        String a102 = idAnimal(EST_A, "A-102").toString();
        when(mlGeoClient.analyze(any())).thenReturn(respuestaMlGeo(
                List.of(celda(-34.60, -58.40, 1.0), celda(-34.61, -58.41, 0.25)),
                List.of(alerta(a104, true), alerta(a102, false), alerta("999999", true))));

        MvcResult resultado = mapaCalor("productorA", EST_A, desde, hasta, Map.of());
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        JsonNode cuerpo = leer(resultado);
        assertThat(cuerpo.get("mapa_calor")).hasSize(2);
        assertThat(cuerpo.get("mapa_calor").get(0).get("intensidad").asDouble()).isEqualTo(1.0);
        assertThat(cuerpo.get("mapa_calor").get(1).get("intensidad").asDouble()).isEqualTo(0.25);
        // Solo el alejado que existe, con su caravana oficial y alias.
        JsonNode alejados = cuerpo.get("animales_alejados");
        assertThat(alejados).hasSize(1);
        assertThat(alejados.get(0).get("animal_id").asText()).isEqualTo(a104);
        assertThat(alejados.get(0).get("caravana").asText()).isEqualTo("A-104");
        assertThat(alejados.get(0).get("alias").asText()).isEqualTo("Aislada A");

        // ml-geo degradado: respuesta vacía con 206.
        when(mlGeoClient.analyze(any()))
                .thenThrow(new MlGeoDegradadoException("ml-geo caído (IT)", new RuntimeException()));
        resultado = mapaCalor("productorA", EST_A, desde, hasta, Map.of());
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(206);
        cuerpo = leer(resultado);
        assertThat(cuerpo.get("mapa_calor")).isEmpty();
        assertThat(cuerpo.get("alertas")).isEmpty();
        assertThat(cuerpo.get("animales_alejados")).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MvcResult mapaCalor(String clave, String establecimiento, LocalDateTime desde, LocalDateTime hasta,
            Map<String, Long> filtros) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/mapa-calor")
                .param("establecimientoId", idEstablecimiento(establecimiento).toString())
                .param("desde", desde.toString())
                .param("hasta", hasta.toString())
                .header("Authorization", bearer(clave));
        filtros.forEach((nombre, valor) -> request.param(nombre, valor.toString()));
        return mockMvc.perform(request).andReturn();
    }

    private GeoAnalyzeRequestDTO enviadoAMlGeo() {
        ArgumentCaptor<GeoAnalyzeRequestDTO> captor = ArgumentCaptor.forClass(GeoAnalyzeRequestDTO.class);
        verify(mlGeoClient).analyze(captor.capture());
        return captor.getValue();
    }

    /** Identificadores visibles ({@code A-101}) de los animales que vienen en la telemetría enviada. */
    private Set<String> identificadores(GeoAnalyzeRequestDTO enviado) {
        Set<Long> ids = enviado.getHistorial7d().stream()
                .map(c -> Long.valueOf(c.getAnimalId()))
                .collect(Collectors.toSet());
        Set<String> identificadores = new TreeSet<>();
        for (Long id : ids) {
            identificadores.add(jdbcTemplate.queryForObject(
                    "SELECT id_caravana_oficial FROM animales WHERE id = ?", String.class, id));
        }
        return identificadores;
    }

    private static void assertDentroDelRango(GeoAnalyzeRequestDTO enviado, LocalDateTime desde,
            LocalDateTime hasta) {
        List<CoordenadaGPSDTO> fuera = new ArrayList<>();
        for (CoordenadaGPSDTO c : enviado.getHistorial7d()) {
            LocalDateTime ts = LocalDateTime.parse(c.getTimestamp());
            if (ts.isBefore(desde) || ts.isAfter(hasta)) {
                fuera.add(c);
            }
        }
        assertThat(fuera).as("datos fuera de [%s, %s]", desde, hasta).isEmpty();
        assertThat(enviado.getHistorial7d()).isNotEmpty();
    }

    /** Telemetría con posición de las caravanas vinculadas del establecimiento (o de un animal) en el rango. */
    private int cantidadTelemetria(String establecimiento, LocalDateTime desde, LocalDateTime hasta,
            String identificador) {
        String sql = "SELECT count(*) FROM telemetria t JOIN animales a ON a.caravana_id = t.caravana_id "
                + "JOIN establecimientos e ON e.id = a.establecimiento_id WHERE e.nombre = ? "
                + "AND t.fecha_hora BETWEEN ? AND ? AND t.latitud IS NOT NULL AND t.longitud IS NOT NULL";
        if (identificador == null) {
            return jdbcTemplate.queryForObject(sql, Integer.class, establecimiento, Timestamp.valueOf(desde),
                    Timestamp.valueOf(hasta));
        }
        return jdbcTemplate.queryForObject(sql + " AND a.id_caravana_oficial = ?", Integer.class, establecimiento,
                Timestamp.valueOf(desde), Timestamp.valueOf(hasta), identificador);
    }

    private static GeoAnalyzeResponseDTO respuestaMlGeo(List<MatrizDensidadDTO> mapa, List<AlertaAnimalDTO> alertas) {
        GeoAnalyzeResponseDTO respuesta = new GeoAnalyzeResponseDTO();
        respuesta.setMapaCalor(new ArrayList<>(mapa));
        respuesta.setAlertas(new ArrayList<>(alertas));
        return respuesta;
    }

    private static MatrizDensidadDTO celda(double lat, double lng, double intensidad) {
        MatrizDensidadDTO celda = new MatrizDensidadDTO();
        celda.setLat(lat);
        celda.setLng(lng);
        celda.setIntensidad(intensidad);
        return celda;
    }

    private static AlertaAnimalDTO alerta(String animalId, boolean aislamiento) {
        AlertaAnimalDTO alerta = new AlertaAnimalDTO();
        alerta.setAnimalId(animalId);
        alerta.setAlertaAislamiento(aislamiento);
        return alerta;
    }
}
