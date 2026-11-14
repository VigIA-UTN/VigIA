package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La acción recomendada de la alerta viaja en el detalle (GET /api/v1/alertas/{id}), en el listado, en la respuesta
 * al resolverla y en la línea de tiempo del animal, así quien atiende la alerta no tiene que buscarla en otro lado.
 */
@DisplayName("H0029 - Acción recomendada visible en < 30 seg — IT")
class AccionRecomendadaIT extends CicloAlertasItBase {

    private static final String ACCION_PERIMETRO =
            "Verificar ubicación del animal en el mapa y retornarlo al perímetro autorizado.";
    private static final String ACCION_AISLAMIENTO =
            "Recorrer la zona y verificar el estado de los animales alejados del rodeo.";

    @Test
    @DisplayName("H0029-01 - Acción recomendada - Viene en el detalle y coincide con la del listado")
    void H0029_01_enDetalleYListado() throws Exception {
        Long celo = crearAlertaCelo(idAnimal(EST_A, "A-101"), LocalDateTime.now().minusHours(1).withNano(0));
        Long perimetro = idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO");
        Long aislamiento = idAlerta(EST_A, "A-104", "AISLAMIENTO_SOCIAL");

        JsonNode listado = listadoOk("productorA", EST_A, null, null, null);
        assertThat(detalle("productorA", EST_A, celo).get("accionRecomendada").asText()).isEqualTo(ACCION_CELO);
        assertThat(detalle("productorA", EST_A, perimetro).get("accionRecomendada").asText())
                .isEqualTo(ACCION_PERIMETRO);
        assertThat(detalle("productorA", EST_A, aislamiento).get("accionRecomendada").asText())
                .isEqualTo(ACCION_AISLAMIENTO);

        // La tarjeta del listado muestra la misma acción que el detalle, alerta por alerta.
        for (JsonNode tarjeta : listado) {
            JsonNode enDetalle = detalle("productorA", EST_A, tarjeta.get("id").asLong());
            assertThat(tarjeta.get("accionRecomendada")).as("alerta %s", tarjeta.get("id"))
                    .isEqualTo(enDetalle.get("accionRecomendada"));
        }
        assertThat(campo(listado, "accionRecomendada")).contains(ACCION_CELO, ACCION_PERIMETRO, ACCION_AISLAMIENTO);
    }

    @Test
    @DisplayName("H0029-02 - Acción recomendada - Alerta sin acción cargada: el campo viene vacío, sin error")
    void H0029_02_alertaSinAccion() throws Exception {
        // Las de celo y salud del escenario no traen acción.
        for (String[] alerta : new String[][] {{"A-102", "CELO"}, {"A-103", "SALUD"}}) {
            JsonNode detalle = detalle("productorA", EST_A, idAlerta(EST_A, alerta[0], alerta[1]));
            assertThat(detalle.has("accionRecomendada")).as(alerta[1]).isTrue();
            assertThat(detalle.get("accionRecomendada").isNull()).as(alerta[1]).isTrue();
            assertThat(detalle.get("descripcionDatoOrigen").asText()).as(alerta[1]).isNotBlank();
        }
        JsonNode sinAccion = listadoOk("productorA", EST_A, "sanitario", null, null);
        assertThat(sinAccion).hasSize(1);
        assertThat(sinAccion.get(0).get("accionRecomendada").isNull()).isTrue();
    }

    @Test
    @DisplayName("H0029-03 - Acción recomendada - Se conserva al confirmar o descartar la alerta")
    void H0029_03_seConservaAlResolver() throws Exception {
        Long perimetro = idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO");
        Long aislamiento = idAlerta(EST_A, "A-104", "AISLAMIENTO_SOCIAL");

        MvcResult confirmada = resolver("camperoA", perimetro, "CONFIRMADA", null);
        assertThat(leer(ok(confirmada)).get("accionRecomendada").asText()).isEqualTo(ACCION_PERIMETRO);
        MvcResult descartada = resolver("veterinarioA", aislamiento, "DESCARTADA", "Cerca del bebedero");
        assertThat(leer(ok(descartada)).get("accionRecomendada").asText()).isEqualTo(ACCION_AISLAMIENTO);

        assertThat(detalle("productorA", EST_A, perimetro).get("accionRecomendada").asText())
                .isEqualTo(ACCION_PERIMETRO);
        assertThat(detalle("productorA", EST_A, aislamiento).get("accionRecomendada").asText())
                .isEqualTo(ACCION_AISLAMIENTO);
        assertThat(listadoOk("productorA", EST_A, "geoespacial", null, "CONFIRMADA").get(0).get("accionRecomendada")
                .asText()).isEqualTo(ACCION_PERIMETRO);
    }

    @Test
    @DisplayName("H0029-04 - Acción recomendada - Visible para los tres roles, en la línea de tiempo y en menos de 30 s")
    void H0029_04_rolesLineaTiempoYTiempo() throws Exception {
        Long perimetro = idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO");

        // Cada rol del establecimiento llega a la acción con dos consultas (panel y detalle), en menos de 30 s.
        for (String clave : new String[] {"productorA", "veterinarioA", "camperoA"}) {
            long inicio = System.nanoTime();
            JsonNode tarjeta = listadoOk(clave, EST_A, "perimetro", null, "PENDIENTE").get(0);
            JsonNode detalle = detalle(clave, EST_A, tarjeta.get("id").asLong());
            Duration demora = Duration.ofNanos(System.nanoTime() - inicio);

            assertThat(detalle.get("accionRecomendada").asText()).as(clave).isEqualTo(ACCION_PERIMETRO);
            assertThat(demora).as(clave).isLessThan(Duration.ofSeconds(30));
        }

        // En la línea de tiempo del animal, la acción es la descripción de la alerta.
        JsonNode eventos = leer(ok(lineaTiempo("productorA", idAnimal(EST_A, "A-105"), null, null, null)));
        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).get("id").asLong()).isEqualTo(perimetro);
        assertThat(eventos.get(0).get("descripcion").asText()).isEqualTo(ACCION_PERIMETRO);

        // Quien no es del establecimiento no la ve.
        assertThat(estado(getAlerta("productorB", "/" + perimetro, EST_A))).isEqualTo(403);
        assertThat(estado(getAlerta("admin", "/" + perimetro, EST_A))).isEqualTo(403);
    }
}
