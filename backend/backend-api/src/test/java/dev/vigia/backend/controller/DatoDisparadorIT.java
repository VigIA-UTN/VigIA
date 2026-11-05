package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /api/v1/alertas/{id}: el detalle devuelve el dato que originó cada tipo de alerta (disparador, valor detectado,
 * umbral, hora y descripción), que es lo que permite auditar por qué se generó.
 */
@DisplayName("H0022 - Ver dato que disparó la alerta (trazabilidad) — IT")
class DatoDisparadorIT extends CicloAlertasItBase {

    @Test
    @DisplayName("H0022-01 - Dato disparador - Cada tipo de alerta del escenario trae su valor, umbral y hora")
    void H0022_01_datoDisparadorPorTipo() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();

        JsonNode celo = detalle("productorA", EST_A, idAlerta(EST_A, "A-102", "CELO"));
        assertThat(celo.get("datoDisparador").asText()).isEqualTo("CELO");
        assertThat(celo.get("valorDetectado").asDouble()).isEqualTo(0.95);
        assertThat(celo.get("valorUmbral").asDouble()).isEqualTo(0.947);
        assertThat(LocalDateTime.parse(celo.get("tsGeneracion").asText())).isBefore(ahora);
        assertThat(celo.get("descripcionDatoOrigen").asText()).contains("signos de celo").contains("95%");

        JsonNode salud = detalle("productorA", EST_A, idAlerta(EST_A, "A-103", "SALUD"));
        assertThat(salud.get("datoDisparador").asText()).isEqualTo("SALUD");
        assertThat(salud.get("valorDetectado").asDouble()).isEqualTo(0.72);
        assertThat(salud.get("valorUmbral").asDouble()).isEqualTo(0.5);
        assertThat(salud.get("detalleSanitario").get("posibleCausa").asText())
                .isEqualTo("Caída de actividad respecto de su perfil");
        assertThat(salud.get("descripcionDatoOrigen").asText())
                .contains("severidad media").contains("Caída de actividad respecto de su perfil");

        JsonNode fuera = detalle("productorA", EST_A, idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO"));
        assertThat(fuera.get("datoDisparador").asText()).isEqualTo("FUERA_DE_PERIMETRO");
        assertThat(fuera.get("valorDetectado").asDouble()).isZero();
        assertThat(fuera.get("valorUmbral").asDouble()).isEqualTo(1.0);
        assertThat(fuera.get("descripcionDatoOrigen").asText()).contains("salió del perímetro");
        JsonNode geo = fuera.get("detalleGeoespacial");
        assertThat(geo.get("nombreLote").asText()).isEqualTo("Rodeo A1");
        assertThat(geo.get("latitud").isNumber()).isTrue();
        assertThat(geo.get("longitud").isNumber()).isTrue();
        // La alerta se generó hace 30 min y sigue pendiente: lleva al menos ese tiempo afuera.
        assertThat(geo.get("segundosFuera").asLong()).isBetween(1790L, 3600L);

        JsonNode aislamiento = detalle("productorA", EST_A, idAlerta(EST_A, "A-104", "AISLAMIENTO_SOCIAL"));
        assertThat(aislamiento.get("datoDisparador").asText()).isEqualTo("AISLAMIENTO_SOCIAL");
        assertThat(aislamiento.get("valorDetectado").asDouble()).isEqualTo(280.0);
        assertThat(aislamiento.get("valorUmbral").asDouble()).isEqualTo(100.0);
        JsonNode aislados = aislamiento.get("detalleAislamiento").get("animalesAislados");
        assertThat(aislados).hasSize(1);
        assertThat(aislados.get(0).get("animalId").asLong()).isEqualTo(idAnimal(EST_A, "A-104"));
        assertThat(aislados.get(0).get("distanciaGrupoM").asLong()).isEqualTo(280L);
        assertThat(aislados.get(0).get("minutosFuera").asLong()).isEqualTo(180L);
        assertThat(aislados.get(0).get("severidad").asText()).isEqualTo("ALTA");

        // Todas con la hora de generación, que sale del dato y no de la consulta.
        for (JsonNode alerta : new JsonNode[] {celo, salud, fuera, aislamiento}) {
            assertThat(LocalDateTime.parse(alerta.get("tsGeneracion").asText())).isBefore(ahora);
        }
    }

    @Test
    @DisplayName("H0022-02 - Dato disparador - Se conserva al resolver la alerta y coincide con el listado")
    void H0022_02_seConservaAlResolver() throws Exception {
        Long alerta = idAlerta(EST_A, "A-103", "SALUD");
        JsonNode antes = detalle("productorA", EST_A, alerta);
        JsonNode listado = listadoOk("productorA", EST_A, "sanitario", null, null).get(0);

        ok(resolver("veterinarioA", alerta, "CONFIRMADA", null));
        JsonNode despues = detalle("productorA", EST_A, alerta);

        for (String campo : new String[] {"datoDisparador", "valorDetectado", "valorUmbral", "tsGeneracion",
                "descripcionDatoOrigen"}) {
            assertThat(despues.get(campo)).as(campo).isEqualTo(antes.get(campo));
        }
        assertThat(despues.get("detalleSanitario").get("posibleCausa"))
                .isEqualTo(antes.get("detalleSanitario").get("posibleCausa"));
        // Lo único que cambia es la resolución, con quién la hizo.
        assertThat(antes.get("estadoActual").asText()).isEqualTo("PENDIENTE");
        assertThat(antes.get("usuarioAccion").isNull()).isTrue();
        assertThat(despues.get("estadoActual").asText()).isEqualTo("CONFIRMADA");
        assertThat(despues.get("usuarioAccion").asText()).isEqualTo("Veterinario A");
        // El listado nombra el mismo disparador y la misma hora.
        assertThat(listado.get("id").asLong()).isEqualTo(alerta);
        assertThat(listado.get("tipo").asText()).isEqualTo(antes.get("datoDisparador").asText());
        assertThat(listado.get("tsGeneracion")).isEqualTo(antes.get("tsGeneracion"));
    }

    @Test
    @DisplayName("H0022-03 - Dato disparador - Anestro con días sin celo, acceso y alerta inexistente")
    void H0022_03_anestroYAcceso() throws Exception {
        // El anestro usa el mismo par de campos como "días sin celo" y "días configurados".
        Long anestro = jdbcTemplate.queryForObject("INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, "
                        + "disparador, valor_umbral, valor_detectado, estado) VALUES (?, 'Anestro Detectado', 'MEDIA', "
                        + "now(), 'CELO', 30, 45, 'PENDIENTE') RETURNING id",
                Long.class, idAnimal(EST_A, "A-101"));

        JsonNode detalle = detalle("camperoA", EST_A, anestro);
        assertThat(detalle.get("valorDetectado").asDouble()).isEqualTo(45.0);
        assertThat(detalle.get("valorUmbral").asDouble()).isEqualTo(30.0);
        assertThat(detalle.get("descripcionDatoOrigen").asText())
                .isEqualTo("El animal no registra celo hace 45 días (umbral: 30 días).");

        // Otro establecimiento, el administrador y una alerta inexistente no obtienen el dato.
        assertThat(estado(getAlerta("productorB", "/" + anestro, EST_A))).isEqualTo(403);
        assertThat(estado(getAlerta("admin", "/" + anestro, EST_A))).isEqualTo(403);
        Long inexistente = jdbcTemplate.queryForObject("SELECT coalesce(max(id), 0) + 1000 FROM alertas", Long.class);
        assertThat(estado(getAlerta("productorA", "/" + inexistente, EST_A))).isEqualTo(404);
    }
}
