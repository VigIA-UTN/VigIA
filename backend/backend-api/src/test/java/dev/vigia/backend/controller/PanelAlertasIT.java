package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * GET /api/v1/alertas como panel de alertas: de la más severa a la menos y, a igual severidad, la más reciente primero.
 * En el escenario, Fixture A tiene cuatro ALTA y una MEDIA pendientes; por antigüedad, de la más nueva a la más vieja:
 * perímetro A-105 (30 min), celo A-102 (60 min), aislamiento A-104 (2 h) y celo A-106 (4 h).
 */
@DisplayName("H0028 - Panel de alertas ordenado por severidad — IT")
class PanelAlertasIT extends CicloAlertasItBase {

    @Test
    @DisplayName("H0028-01 - Panel - De la severidad más alta a la más baja")
    void H0028_01_ordenPorSeveridad() throws Exception {
        LocalDateTime ahora = ahora();
        // Insertadas en desorden y con el dato más nuevo en la severidad más baja: la fecha no puede pesar más.
        insertarAlerta(EST_A, "A-101", "SALUD", "SALUD", "BAJA", "PENDIENTE", ahora.minusMinutes(1));
        insertarAlerta(EST_A, "A-101", "Celo Detectado", "CELO", "MEDIA", "PENDIENTE", ahora.minusMinutes(2));
        insertarAlerta(EST_A, "A-101", "Abigeato", "ABIGEATO", "BAJA", "PENDIENTE", ahora.minusMinutes(3));

        JsonNode panel = listadoOk("productorA", EST_A, null, null, "PENDIENTE");

        assertThat(campo(panel, "severidad")).containsExactly(
                "ALTA", "ALTA", "ALTA", "ALTA", "MEDIA", "MEDIA", "BAJA", "BAJA");
        // Lo mismo para el resto de los roles.
        for (String clave : new String[] {"veterinarioA", "camperoA"}) {
            assertThat(ids(listadoOk(clave, EST_A, null, null, "PENDIENTE"))).as(clave).isEqualTo(ids(panel));
        }
    }

    @Test
    @DisplayName("H0028-02 - Panel - A igual severidad, la alerta más reciente primero")
    void H0028_02_masRecientePrimeroDentroDeLaSeveridad() throws Exception {
        Long perimetro = idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO");
        Long celoReciente = idAlerta(EST_A, "A-102", "CELO");
        Long aislamiento = idAlerta(EST_A, "A-104", "AISLAMIENTO_SOCIAL");
        Long celoViejo = idAlerta(EST_A, "A-106", "CELO");

        JsonNode panel = listadoOk("productorA", EST_A, null, "ALTA", null);

        assertThat(ids(panel)).containsExactly(perimetro, celoReciente, aislamiento, celoViejo);
        List<LocalDateTime> fechas = panel.findValuesAsText("tsGeneracion").stream().map(LocalDateTime::parse).toList();
        assertThat(fechas).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        // Con filtro de tipo se mantiene el mismo criterio.
        assertThat(ids(listadoOk("productorA", EST_A, "celo", null, null))).containsExactly(celoReciente, celoViejo);
    }

    @Test
    @DisplayName("H0028-03 - Panel - Las alertas nuevas y las resueltas se reubican")
    void H0028_03_nuevasYResueltasSeReubican() throws Exception {
        Long perimetro = idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO");
        Long salud = idAlerta(EST_A, "A-103", "SALUD");

        // Una ALTA recién generada pasa al primer lugar; una BAJA nueva queda última aunque sea la más reciente.
        Long altaNueva = insertarAlerta(EST_A, "A-101", "Abigeato", "ABIGEATO", "ALTA", "PENDIENTE", ahora());
        Long bajaNueva = insertarAlerta(EST_A, "A-101", "SALUD", "SALUD", "BAJA", "PENDIENTE", ahora());
        List<Long> panel = ids(listadoOk("productorA", EST_A, null, null, "PENDIENTE"));
        assertThat(panel).hasSize(7);
        assertThat(panel.get(0)).isEqualTo(altaNueva);
        assertThat(panel.get(1)).isEqualTo(perimetro);
        assertThat(panel.get(5)).isEqualTo(salud);
        assertThat(panel.get(6)).isEqualTo(bajaNueva);

        // Resolver una alerta la saca del panel de pendientes y la pasa al de confirmadas, ordenado igual.
        ok(resolver("veterinarioA", perimetro, "CONFIRMADA", null));
        ok(resolver("productorA", altaNueva, "DESCARTADA", null));
        assertThat(ids(listadoOk("productorA", EST_A, null, null, "PENDIENTE"))).doesNotContain(perimetro, altaNueva)
                .hasSize(5);
        assertThat(ids(listadoOk("productorA", EST_A, null, null, "CONFIRMADA"))).containsExactly(perimetro);
        assertThat(ids(listadoOk("productorA", EST_A, null, null, "DESCARTADA"))).containsExactly(altaNueva);
        // Sin filtrar por estado, el panel conserva todas, siempre por severidad.
        JsonNode todas = listadoOk("productorA", EST_A, null, null, null);
        assertThat(todas).hasSize(7);
        assertThat(campo(todas, "severidad")).isSortedAccordingTo(
                java.util.Comparator.comparingInt(s -> List.of("ALTA", "MEDIA", "BAJA").indexOf(s)));
    }

    @Test
    @DisplayName("H0028-04 - Panel - Contadores por tipo acordes al panel y datos de cada tarjeta")
    void H0028_04_resumenYTarjetas() throws Exception {
        insertarAlerta(EST_A, "A-101", "Abigeato", "ABIGEATO", "BAJA", "PENDIENTE", ahora().minusHours(1));

        // El resumen cuenta las pendientes por tipo, con "geoespacial" agrupando abigeato, perímetro y aislamiento.
        JsonNode resumen = leer(ok(mockMvc.perform(get("/api/v1/alertas/resumen")
                        .param("establecimientoId", idEstablecimiento(EST_A).toString())
                        .header("Authorization", bearer("camperoA"))).andReturn()));
        assertThat(resumen.get("reproductivo").asInt()).isEqualTo(2);
        assertThat(resumen.get("sanitario").asInt()).isEqualTo(1);
        assertThat(resumen.get("geoespacial").asInt()).isEqualTo(3);
        assertThat(listadoOk("camperoA", EST_A, null, null, "PENDIENTE")).hasSize(
                resumen.get("reproductivo").asInt() + resumen.get("sanitario").asInt()
                        + resumen.get("geoespacial").asInt());

        // Cada tarjeta trae lo que muestra el panel.
        JsonNode primera = listadoOk("camperoA", EST_A, null, null, "PENDIENTE").get(0);
        assertThat(primera.get("id").asLong()).isEqualTo(idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO"));
        assertThat(primera.get("tipo").asText()).isEqualTo("FUERA_DE_PERIMETRO");
        assertThat(primera.get("severidad").asText()).isEqualTo("ALTA");
        assertThat(primera.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(primera.get("animalId").asLong()).isEqualTo(idAnimal(EST_A, "A-105"));
        assertThat(primera.get("tsGeneracion").isNull()).isFalse();
        assertThat(primera.get("accionRecomendada").asText()).contains("Verificar ubicación");

        // El resumen de otro establecimiento no se mezcla ni se consulta sin ser miembro.
        assertThat(estado(mockMvc.perform(get("/api/v1/alertas/resumen")
                        .param("establecimientoId", idEstablecimiento(EST_A).toString())
                        .header("Authorization", bearer("productorB"))).andReturn())).isEqualTo(403);
        assertThat(listadoOk("productorB", EST_B, null, null, "PENDIENTE")).hasSize(5);
    }
}
