package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Sobre el escenario: en Fixture A hay 6 animales con caravana, batería al 80 % y sin perfil de salud (todos en
 * calibración). Alertas pendientes: celo en A-102 (hace 1 h, la ventana abre en ~5 h) y en A-106 (hace 4 h, abre en
 * ~2 h, sin señal hace 3 h), sanitaria MEDIA en A-103 (hace 2 h, único animal del Rodeo A2), aislamiento ALTA en A-104
 * (hace 2 h) y perímetro ALTA en A-105 (hace 30 min). A-101 no tiene alertas.
 * <p>
 * Criterios de la HU que se definieron al diseñar los casos: el indicador "Ventanas abiertas" cuenta solo las ya
 * abiertas (la lista muestra además las próximas a abrir); en la cola van primero los celos con la ventana abierta,
 * y dentro de cada grupo la alerta más antigua es la más urgente.
 * <p>
 * El botón "Atender", "Ver todas" y el destacado de las ventanas que vencen en 6 h son del frontend, y el mapa en vivo
 * lo cubre H0012 (N/A en la API).
 */
@DisplayName("H0043 - Dashboard: Centro de mando — IT")
class DashboardCentroMandoIT extends MapaDashboardItBase {

    private static final String PESTANIA = "centro-mando";

    @Test
    @DisplayName("H0043-01 - Centro de mando - Indicadores con el escenario")
    void H0043_01_indicadoresConElEscenario() throws Exception {
        JsonNode cm = centroMando(Map.of());

        JsonNode pendientes = cm.get("alertasPendientes");
        assertThat(pendientes.get("total").asLong()).isEqualTo(5);
        assertThat(pendientes.get("celo").asLong()).isEqualTo(2);
        assertThat(pendientes.get("salud").asLong()).isEqualTo(1);
        assertThat(pendientes.get("perimetro").asLong()).isEqualTo(1);

        // Las dos ventanas del escenario todavía no abrieron.
        JsonNode ventanas = cm.get("ventanasAbiertas");
        assertThat(ventanas.get("total").asLong()).as("ventanas abiertas").isZero();
        assertThat(ventanas.get("porVencer").asLong()).isZero();
        assertThat(ventanas.get("perdidasUltimos30Dias").asLong()).isZero();

        assertThat(cm.get("bajoObservacion").get("total").asLong()).isZero();
        assertThat(cm.get("bajoObservacion").get("enfermos").asLong()).isZero();

        JsonNode cobertura = cm.get("cobertura");
        assertThat(cobertura.get("total").asLong()).isEqualTo(6);
        assertThat(cobertura.get("calibrando").asLong()).isEqualTo(6);
        assertThat(cobertura.get("ok").asLong()).isZero();
        assertThat(cobertura.get("porcentaje").asInt()).isZero();
    }

    @Test
    @DisplayName("H0043-02 - Centro de mando - Filtro por lote")
    void H0043_02_filtroPorLote() throws Exception {
        JsonNode rodeo2 = centroMando(Map.of("rodeoId", idRodeo(EST_A, "Rodeo A2")));
        assertThat(rodeo2.get("alertasPendientes").get("total").asLong()).isEqualTo(1);
        assertThat(rodeo2.get("alertasPendientes").get("salud").asLong()).isEqualTo(1);
        assertThat(rodeo2.get("alertasPendientes").get("celo").asLong()).isZero();
        assertThat(rodeo2.get("cobertura").get("total").asLong()).isEqualTo(1);
        assertThat(cola(rodeo2)).containsExactly("A-103 SALUD");

        JsonNode rodeo1 = centroMando(Map.of("rodeoId", idRodeo(EST_A, "Rodeo A1")));
        assertThat(rodeo1.get("alertasPendientes").get("total").asLong()).isEqualTo(4);
        assertThat(rodeo1.get("alertasPendientes").get("celo").asLong()).isEqualTo(2);
        assertThat(rodeo1.get("alertasPendientes").get("salud").asLong()).isZero();
        assertThat(rodeo1.get("alertasPendientes").get("perimetro").asLong()).isEqualTo(1);
        assertThat(rodeo1.get("cobertura").get("total").asLong()).isEqualTo(5);
        assertThat(cola(rodeo1)).hasSize(4).noneMatch(item -> item.startsWith("A-103"));
    }

    @Test
    @DisplayName("H0043-03 - Centro de mando - Cola de atención por urgencia y hasta 8 alertas")
    void H0043_03_colaDeAtencion() throws Exception {
        LocalDateTime ahora = ahora();
        insertarCelo(EST_A, "A-101", "Celo Detectado", "PENDIENTE", ahora.minusHours(22)); // abierta, quedan ~2 h
        insertarCelo(EST_A, "A-103", "Celo Detectado", "PENDIENTE", ahora.minusHours(10)); // abierta, quedan ~14 h
        insertarAlerta(EST_A, "A-102", "Fuera de Perímetro", "FUERA_DE_PERIMETRO", "MEDIA", "PENDIENTE",
                ahora.minusHours(3));
        insertarAlerta(EST_A, "A-101", "SALUD", "SALUD", "BAJA", "PENDIENTE", ahora.minusDays(1));

        JsonNode cm = centroMando(Map.of());

        assertThat(cm.get("alertasPendientes").get("total").asLong()).isEqualTo(9);
        assertThat(cola(cm)).containsExactly(
                "A-101 CELO",                // ventana abierta, ~2 h restantes
                "A-103 CELO",                // ventana abierta, ~14 h restantes
                "A-102 FUERA_DE_PERIMETRO",  // hace 3 h
                "A-105 FUERA_DE_PERIMETRO",  // hace 30 min
                "A-106 CELO",                // ALTA, ventana cerrada, hace 4 h
                "A-104 AISLAMIENTO_SOCIAL",  // ALTA, hace 2 h
                "A-102 CELO",                // ALTA, ventana cerrada, hace 1 h
                "A-103 SALUD");              // MEDIA; la BAJA de A-101 es la 9.ª y queda afuera

        JsonNode primero = cm.get("cola").get(0);
        assertThat(primero.get("animal").get("idCaravanaOficial").asText()).isEqualTo("A-101");
        assertThat(primero.get("ventana").get("estado").asText()).isEqualTo("ACTIVA");
        assertThat(primero.get("ventana").get("tiempoRestanteSegundos").asLong())
                .isCloseTo(2 * 3600L, within(120L));
        JsonNode perimetro = cm.get("cola").get(2);
        assertThat(perimetro.get("ventana").isNull()).isTrue();
        assertThat(fecha(perimetro.get("alerta").get("tsGeneracion"))).isEqualTo(ahora.minusHours(3));
    }

    @Test
    @DisplayName("H0043-04 - Centro de mando - Ventanas de inseminación abiertas y perdidas")
    void H0043_04_ventanasDeInseminacion() throws Exception {
        LocalDateTime ahora = ahora();
        insertarCelo(EST_A, "A-101", "Celo Detectado", "PENDIENTE", ahora.minusHours(22));
        insertarCelo(EST_A, "A-103", "Celo Detectado", "PENDIENTE", ahora.minusHours(10));
        insertarCelo(EST_A, "A-104", "Celo Detectado", "ARCHIVADA", ahora.minusDays(10));
        insertarCelo(EST_A, "A-105", "Celo Detectado", "VENCIDA", ahora.minusDays(20));
        insertarCelo(EST_A, "A-101", "Celo Detectado", "ARCHIVADA", ahora.minusDays(40));
        insertarAnestro(EST_A, "A-104", "ARCHIVADA", ahora.minusDays(5));
        insertarCelo(EST_A, "A-105", "Celo Detectado", "CONFIRMADA", ahora.minusDays(5));

        JsonNode cm = centroMando(Map.of());

        JsonNode indicador = cm.get("ventanasAbiertas");
        assertThat(indicador.get("total").asLong()).as("ventanas abiertas").isEqualTo(2);
        assertThat(indicador.get("porVencer").asLong()).isEqualTo(1);
        assertThat(indicador.get("perdidasUltimos30Dias").asLong()).isEqualTo(2);

        List<String> lista = new ArrayList<>();
        cm.get("ventanas").forEach(v -> lista.add(v.get("animal").get("idCaravanaOficial").asText() + " "
                + v.get("ventana").get("estado").asText()));
        assertThat(lista).containsExactly("A-101 ACTIVA", "A-103 ACTIVA", "A-106 FUTURA", "A-102 FUTURA");
        long[] restantes = {2, 14, 2, 5};
        for (int i = 0; i < restantes.length; i++) {
            assertThat(cm.get("ventanas").get(i).get("ventana").get("tiempoRestanteSegundos").asLong())
                    .as(lista.get(i)).isCloseTo(restantes[i] * 3600, within(120L));
        }
    }

    @Test
    @DisplayName("H0043-05 - Centro de mando - ¿Puedo confiar en el monitoreo?")
    void H0043_05_coberturaDelMonitoreo() throws Exception {
        for (String animal : List.of("A-101", "A-102", "A-103", "A-105", "A-106")) {
            perfilSalud(EST_A, animal);
        }
        ultimaSenial("FX-A-01", ahora().minusHours(6).minusMinutes(5));
        bateria("FX-A-02", 20);
        bateria("FX-A-06", 21);
        sinCaravana(EST_A, "A-105");

        JsonNode cm = centroMando(Map.of());

        JsonNode cobertura = cm.get("cobertura");
        assertThat(cobertura.get("total").asLong()).isEqualTo(6);
        assertThat(cobertura.get("ok").asLong()).as("monitoreados").isEqualTo(2);
        assertThat(cobertura.get("calibrando").asLong()).isEqualTo(1);
        assertThat(cobertura.get("sinSenial").asLong()).isEqualTo(1);
        assertThat(cobertura.get("bateriaBaja").asLong()).isEqualTo(1);
        assertThat(cobertura.get("sinCaravana").asLong()).isEqualTo(1);
        assertThat(cobertura.get("porcentaje").asInt()).isEqualTo(33);

        // A-101 no tiene alertas: la caravana que no transmite entra a la cola como un ítem propio.
        Long a101 = idAnimal(EST_A, "A-101");
        assertThat(cola(cm)).contains("A-101 CARAVANA");
        cm.get("cola").forEach(item -> {
            if ("CARAVANA".equals(item.get("alerta").get("tipo").asText())) {
                assertThat(item.get("alerta").get("id").asLong()).isEqualTo(-a101);
            }
        });
    }

    @Test
    @DisplayName("H0043-06 - Centro de mando - Embudo reproductivo")
    void H0043_06_embudoReproductivo() throws Exception {
        altaAnimalSinCaravana(EST_A, "A-190", LocalDate.now().minusMonths(6));

        JsonNode embudo = centroMando(Map.of()).get("embudo");

        assertThat(embudo.get("totalReproductivos").asLong()).isEqualTo(6);
        JsonNode distribucion = embudo.get("distribucion");
        assertThat(distribucion.get("GESTANTE").asLong()).isEqualTo(4);
        assertThat(distribucion.get("POSIBLE_CELO").asLong()).isEqualTo(2);
        long total = 0;
        for (JsonNode cantidad : distribucion) {
            total += cantidad.asLong();
        }
        assertThat(total).as("la vaquillona no entra al embudo").isEqualTo(6);
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private JsonNode centroMando(Map<String, Object> params) throws Exception {
        return dashboardOk("productorA", EST_A, PESTANIA, params);
    }

    /** La cola como "animal tipo", en orden. */
    private static List<String> cola(JsonNode cm) {
        List<String> items = new ArrayList<>();
        cm.get("cola").forEach(item -> items.add(item.get("animal").get("idCaravanaOficial").asText() + " "
                + item.get("alerta").get("tipo").asText()));
        return items;
    }
}
