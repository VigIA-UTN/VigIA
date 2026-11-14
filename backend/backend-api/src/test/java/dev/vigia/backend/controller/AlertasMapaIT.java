package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sobre el escenario: en cada establecimiento hay una alerta pendiente de cada tipo: celo en A-102 y en A-106 (esta con
 * dato desactualizado), sanitaria en A-103, aislamiento en A-104 y salida de perímetro en A-105; A-101 no tiene.
 * {@code /animales/mapa} trae las alertas activas (PENDIENTE) de cada animal ordenadas por severidad, y
 * {@code /alertas/resumen} las cuenta por grupo: reproductivo (celo), sanitario y geoespacial (perímetro, aislamiento y
 * abigeato).
 * <p>
 * El color del marcador según el tipo y la elección de la alerta más urgente para pintarlo son del frontend (N/A en la
 * API).
 */
@DisplayName("H0013 - Identificar alertas activas desde el mapa — IT")
class AlertasMapaIT extends MapaDashboardItBase {

    @Test
    @DisplayName("H0013-01 - Alertas en el mapa - Alerta activa de cada animal según su tipo")
    void H0013_01_alertaActivaDeCadaAnimal() throws Exception {
        JsonNode mapa = mapaOk("productorA", EST_A);

        assertThat(animalDelMapa(mapa, "A-101").get("alertasAnimal")).isEmpty();

        JsonNode celo = unicaAlerta(mapa, "A-102");
        assertThat(celo.get("id").asLong()).isEqualTo(idAlerta(EST_A, "A-102", "CELO"));
        assertThat(celo.get("tipo").asText()).isEqualTo("Celo Detectado");
        assertThat(celo.get("severidad").asText()).isEqualTo("ALTA");
        assertThat(celo.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(celo.get("datoPosiblementeDesactualizado").asBoolean()).isFalse();

        JsonNode salud = unicaAlerta(mapa, "A-103");
        assertThat(salud.get("id").asLong()).isEqualTo(idAlerta(EST_A, "A-103", "SALUD"));
        assertThat(salud.get("tipo").asText()).isEqualTo("SALUD");
        assertThat(salud.get("severidad").asText()).isEqualTo("MEDIA");

        JsonNode aislamiento = unicaAlerta(mapa, "A-104");
        assertThat(aislamiento.get("id").asLong()).isEqualTo(idAlerta(EST_A, "A-104", "AISLAMIENTO_SOCIAL"));
        assertThat(aislamiento.get("tipo").asText()).isEqualTo("AISLAMIENTO_SOCIAL");
        assertThat(aislamiento.get("animalesAislados")).hasSize(1);
        assertThat(aislamiento.get("animalesAislados").get(0).get("identificador").asText()).isEqualTo("A-104");

        JsonNode perimetro = unicaAlerta(mapa, "A-105");
        assertThat(perimetro.get("id").asLong()).isEqualTo(idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO"));
        assertThat(perimetro.get("tipo").asText()).isEqualTo("Fuera de Perímetro");
        assertThat(perimetro.get("severidad").asText()).isEqualTo("ALTA");
        assertThat(perimetro.get("latitud").isNumber()).isTrue();
        assertThat(perimetro.get("longitud").isNumber()).isTrue();

        JsonNode celoSinDatos = unicaAlerta(mapa, "A-106");
        assertThat(celoSinDatos.get("id").asLong()).isEqualTo(idAlerta(EST_A, "A-106", "CELO"));
        assertThat(celoSinDatos.get("datoPosiblementeDesactualizado").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("H0013-02 - Alertas en el mapa - Varias alertas activas simultáneas")
    void H0013_02_variasAlertasSimultaneas() throws Exception {
        // A-102 suma una sanitaria BAJA, una de perímetro MEDIA y queda listada en el aislamiento de A-104.
        Long saludBaja = insertarAlerta(EST_A, "A-102", "SALUD", "SALUD", "BAJA", "PENDIENTE", ahora().minusHours(1));
        Long perimetroMedia = insertarAlerta(EST_A, "A-102", "Fuera de Perímetro", "FUERA_DE_PERIMETRO", "MEDIA",
                "PENDIENTE", ahora().minusMinutes(30));
        Long aislamiento = idAlerta(EST_A, "A-104", "AISLAMIENTO_SOCIAL");
        jdbcTemplate.update("INSERT INTO alerta_animales_aislados (alerta_id, animal_id, segundos_fuera, "
                        + "distancia_grupo_m, ts_inicio) VALUES (?, ?, 3600, 150.0, ?)",
                aislamiento, idAnimal(EST_A, "A-102"), Timestamp.valueOf(ahora().minusHours(1)));

        JsonNode mapa = mapaOk("productorA", EST_A);

        List<Long> ids = new ArrayList<>();
        List<String> severidades = new ArrayList<>();
        animalDelMapa(mapa, "A-102").get("alertasAnimal").forEach(a -> {
            ids.add(a.get("id").asLong());
            severidades.add(a.get("severidad").asText());
        });
        // Todas, de la más severa a la menos (entre las dos ALTA el orden no está definido).
        assertThat(ids).containsExactlyInAnyOrder(idAlerta(EST_A, "A-102", "CELO"), aislamiento, perimetroMedia,
                saludBaja);
        assertThat(severidades).containsExactly("ALTA", "ALTA", "MEDIA", "BAJA");
        assertThat(ids.subList(2, 4)).containsExactly(perimetroMedia, saludBaja);

        // El aislamiento sigue también en A-104.
        assertThat(unicaAlerta(mapa, "A-104").get("id").asLong()).isEqualTo(aislamiento);
    }

    @Test
    @DisplayName("H0013-03 - Alertas en el mapa - Solo alertas activas")
    void H0013_03_soloAlertasActivas() throws Exception {
        cambiarEstadoAlertas(EST_A, "A-102", "CELO", "CONFIRMADA");
        cambiarEstadoAlertas(EST_A, "A-103", "SALUD", "DESCARTADA");
        cambiarEstadoAlertas(EST_A, "A-105", "FUERA_DE_PERIMETRO", "VENCIDA");
        cambiarEstadoAlertas(EST_A, "A-106", "CELO", "ARCHIVADA");

        JsonNode mapa = mapaOk("productorA", EST_A);

        for (String identificador : List.of("A-101", "A-102", "A-103", "A-105", "A-106")) {
            assertThat(animalDelMapa(mapa, identificador).get("alertasAnimal")).as(identificador).isEmpty();
        }
        assertThat(unicaAlerta(mapa, "A-104").get("id").asLong())
                .isEqualTo(idAlerta(EST_A, "A-104", "AISLAMIENTO_SOCIAL"));

        // Las de Fixture B no cambian.
        JsonNode mapaB = mapaOk("productorB", EST_B);
        for (String identificador : List.of("B-102", "B-103", "B-104", "B-105", "B-106")) {
            assertThat(animalDelMapa(mapaB, identificador).get("alertasAnimal")).as(identificador).hasSize(1);
        }
    }

    @Test
    @DisplayName("H0013-04 - Alertas en el mapa - Resumen numérico por tipo")
    void H0013_04_resumenPorTipo() throws Exception {
        assertResumen(resumen("productorA", EST_A), 2, 1, 2);

        // +1 sanitaria y +1 geoespacial (abigeato) pendientes en A-101; -1 reproductiva (se archiva el celo de A-106);
        // una sanitaria ya confirmada no suma.
        insertarAlerta(EST_A, "A-101", "SALUD", "SALUD", "MEDIA", "PENDIENTE", ahora().minusHours(1));
        insertarAlerta(EST_A, "A-101", "ABIGEATO", "ABIGEATO", "ALTA", "PENDIENTE", ahora().minusMinutes(20));
        cambiarEstadoAlertas(EST_A, "A-106", "CELO", "ARCHIVADA");
        insertarAlerta(EST_A, "A-103", "SALUD", "SALUD", "MEDIA", "CONFIRMADA", ahora().minusDays(2));

        assertResumen(resumen("veterinarioA", EST_A), 1, 2, 3);
        // Fixture B no cambia y cada uno ve solo el suyo.
        assertResumen(resumen("camperoB", EST_B), 2, 1, 2);
        assertThat(resumen("productorB", EST_A).getResponse().getStatus()).isEqualTo(403);
    }

    private JsonNode unicaAlerta(JsonNode mapa, String identificador) {
        JsonNode alertas = animalDelMapa(mapa, identificador).get("alertasAnimal");
        assertThat(alertas).as("alertas de %s", identificador).hasSize(1);
        return alertas.get(0);
    }

    private MvcResult resumen(String clave, String establecimiento) throws Exception {
        return mockMvc.perform(get("/api/v1/alertas/resumen")
                        .param("establecimientoId", idEstablecimiento(establecimiento).toString())
                        .header("Authorization", bearer(clave)))
                .andReturn();
    }

    private void assertResumen(MvcResult resultado, long reproductivo, long sanitario, long geoespacial)
            throws Exception {
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        JsonNode resumen = leer(resultado);
        assertThat(resumen.get("reproductivo").asLong()).as("reproductivo").isEqualTo(reproductivo);
        assertThat(resumen.get("sanitario").asLong()).as("sanitario").isEqualTo(sanitario);
        assertThat(resumen.get("geoespacial").asLong()).as("geoespacial").isEqualTo(geoespacial);
    }
}
