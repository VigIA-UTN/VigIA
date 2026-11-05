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

/**
 * Sobre el escenario: en Fixture A hay 6 animales adultos sin perfil de salud (todos en calibración). A-101, A-103,
 * A-104 y A-105 están gestantes (inseminadas hace 60 días); A-102 y A-106 parieron hace 60 días y su último estado es
 * el POSIBLE_CELO automático de hace 4 h, que no trae fecha de parto. Alertas pendientes: celo en A-102 y A-106 (las
 * dos ventanas por abrir), sanitaria en A-103 (hace 2 h, único animal del Rodeo A2), aislamiento en A-104 y perímetro
 * en A-105. No hay intervenciones ni estados de salud.
 * <p>
 * Criterios de la HU que se definieron al diseñar los casos: la fecha del último parto y de la última inseminación se
 * toman del historial del animal (el último registro que las tiene), no solo de su último estado; y la pestaña
 * informa los animales en calibración.
 * <p>
 * La ganancia de peso queda pendiente en la HU; cómo se destacan las excepciones y la vista de partos son del
 * frontend (N/A en la API).
 */
@DisplayName("H0044 - Dashboard: Salud y reproducción — IT")
class DashboardSaludReproduccionIT extends MapaDashboardItBase {

    private static final String PESTANIA = "salud-reproduccion";

    @Test
    @DisplayName("H0044-01 - Salud y reproducción - Indicadores")
    void H0044_01_indicadores() throws Exception {
        LocalDateTime ahora = ahora();
        insertarAlerta(EST_A, "A-101", "SALUD", "SALUD", "MEDIA", "PENDIENTE", ahora.minusHours(30));
        insertarCelo(EST_A, "A-104", "Celo Detectado", "ARCHIVADA", ahora.minusDays(10));
        insertarCelo(EST_A, "A-105", "Celo Detectado", "VENCIDA", ahora.minusDays(40));

        JsonNode sr = saludReproduccion(Map.of());
        JsonNode indicadores = sr.get("indicadores");

        assertThat(sr.get("desempenyo").get("ventanasPerdidas").asLong()).as("ventanas perdidas en 30 días")
                .isEqualTo(1);
        assertThat(indicadores.get("saludSinAtender").asLong()).as("sanitarias sin atender hace más de 24 h")
                .isEqualTo(1);
        assertThat(indicadores.get("celosSinAtender").asLong()).isEqualTo(2);
        assertThat(indicadores.get("celosPorVencer").asLong()).isZero();

        // A-102 y A-106 parieron hace 60 días: según la hora, el promedio redondea a 60 o 61.
        assertThat(indicadores.get("vacasAbiertas").asLong()).as("vacas abiertas").isEqualTo(2);
        assertThat(indicadores.get("diasAbiertosPromedio").asLong()).isBetween(60L, 61L);
        assertThat(indicadores.has("animalesEnCalibracion")).as("indicador de animales en calibración").isTrue();
        assertThat(indicadores.get("animalesEnCalibracion").asLong()).isEqualTo(6);
    }

    @Test
    @DisplayName("H0044-02 - Salud y reproducción - Filtro por lote")
    void H0044_02_filtroPorLote() throws Exception {
        JsonNode rodeo2 = saludReproduccion(Map.of("rodeoId", idRodeo(EST_A, "Rodeo A2")));
        assertThat(animales(rodeo2.get("alertasSaludSinAtender"))).containsExactly("A-103");
        assertThat(rodeo2.get("indicadores").get("celosSinAtender").asLong()).isZero();
        assertThat(rodeo2.get("embudo").get("totalReproductivos").asLong()).isEqualTo(1);
        assertThat(rodeo2.get("embudo").get("distribucion").get("GESTANTE").asLong()).isEqualTo(1);

        JsonNode rodeo1 = saludReproduccion(Map.of("rodeoId", idRodeo(EST_A, "Rodeo A1")));
        assertThat(rodeo1.get("alertasSaludSinAtender")).isEmpty();
        assertThat(rodeo1.get("indicadores").get("celosSinAtender").asLong()).isEqualTo(2);
        assertThat(rodeo1.get("embudo").get("totalReproductivos").asLong()).isEqualTo(5);
    }

    @Test
    @DisplayName("H0044-03 - Salud y reproducción - Excepciones del embudo")
    void H0044_03_excepcionesDelEmbudo() throws Exception {
        LocalDateTime ahora = ahora();
        LocalDate hoy = ahora.toLocalDate();
        estadoReproductivo(EST_A, "A-101", "VACIA", hoy.minusDays(100), null, ahora);
        estadoReproductivo(EST_A, "A-104", "VACIA", hoy.minusDays(80), null, ahora);
        estadoReproductivo(EST_A, "A-105", "INSEMINADA", null, hoy.minusDays(40), ahora);
        estadoReproductivo(EST_A, "A-103", "INSEMINADA", null, hoy.minusDays(20), ahora);
        insertarAnestro(EST_A, "A-104", "PENDIENTE", ahora.minusDays(2));
        insertarAnestro(EST_A, "A-101", "CONFIRMADA", ahora.minusDays(5));

        JsonNode embudo = saludReproduccion(Map.of()).get("embudo");

        assertThat(animales(embudo.get("vaciasLargas"))).as("vacías con más de 90 días").containsExactly("A-101");
        assertThat(animales(embudo.get("inseminadasSinDiagnostico"))).as("inseminadas hace más de 35 días")
                .containsExactly("A-105");
        assertThat(animales(embudo.get("anestroSinIntervencion"))).containsExactly("A-104");
    }

    @Test
    @DisplayName("H0044-04 - Salud y reproducción - Animales a revisar")
    void H0044_04_animalesARevisar() throws Exception {
        LocalDateTime ahora = ahora();
        insertarAlerta(EST_A, "A-105", "SALUD", "SALUD", "ALTA", "PENDIENTE", ahora.minusHours(30));
        estadoSalud(EST_A, "A-101", "ENFERMO", ahora.minusDays(1));
        estadoSalud(EST_A, "A-104", "EN_OBSERVACION", ahora.minusDays(1));
        estadoSalud(EST_A, "A-106", "ENFERMO", ahora.minusDays(5));
        estadoSalud(EST_A, "A-106", "SANO", ahora.minusHours(1));
        intervencion(EST_A, "A-104", "REVISION_VETERINARIA", "veterinarioA", ahora.minusDays(3));

        JsonNode sr = saludReproduccion(Map.of());

        assertThat(animales(sr.get("alertasSaludSinAtender"))).as("de la más vieja a la más nueva")
                .containsExactly("A-105", "A-103");

        JsonNode enObservacion = sr.get("enObservacion");
        assertThat(animales(enObservacion)).containsExactly("A-101", "A-104");
        assertThat(enObservacion.get(0).get("estadoSalud").asText()).isEqualTo("ENFERMO");
        assertThat(enObservacion.get(0).get("ultimaIntervencionTipo").isNull()).isTrue();
        assertThat(enObservacion.get(0).get("diasSinIntervencion").isNull()).isTrue();
        assertThat(enObservacion.get(1).get("estadoSalud").asText()).isEqualTo("EN_OBSERVACION");
        assertThat(enObservacion.get(1).get("ultimaIntervencionTipo").asText()).isEqualTo("REVISION_VETERINARIA");
        assertThat(enObservacion.get(1).get("diasSinIntervencion").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("H0044-05 - Salud y reproducción - Partos esperados")
    void H0044_05_partosEsperados() throws Exception {
        LocalDateTime ahora = ahora();
        LocalDate hoy = ahora.toLocalDate();
        // Parto a 283 días de la inseminación.
        estadoReproductivo(EST_A, "A-101", "GESTANTE", null, hoy.minusDays(273), ahora); // en 10 días
        estadoReproductivo(EST_A, "A-103", "GESTANTE", null, hoy.minusDays(203), ahora); // en 80 días
        estadoReproductivo(EST_A, "A-105", "GESTANTE", null, hoy.minusDays(193), ahora); // en 90 días
        estadoReproductivo(EST_A, "A-106", "GESTANTE", null, hoy.minusDays(290), ahora); // hace 7 días
        // A-104: la inseminación quedó en el registro anterior; el de gestante no repite la fecha.
        estadoReproductivo(EST_A, "A-104", "INSEMINADA", null, hoy.minusDays(263), ahora.minusDays(2)); // en 20 días
        estadoReproductivo(EST_A, "A-104", "GESTANTE", null, null, ahora);
        altaAnimalSinCaravana(EST_A, "A-190", hoy.minusYears(3));
        estadoReproductivo(EST_A, "A-190", "GESTANTE", null, null, ahora);

        JsonNode partos = saludReproduccion(Map.of()).get("partos");

        JsonNode semanas = partos.get("semanas");
        assertThat(semanas).hasSize(12);
        assertThat(LocalDate.parse(semanas.get(0).get("desde").asText())).isEqualTo(hoy);
        assertThat(animales(semanas.get(1).get("animales"))).as("semana 2").containsExactly("A-101");
        assertThat(animales(semanas.get(11).get("animales"))).as("semana 12").containsExactly("A-103");
        assertThat(partos.get("vencidos").asLong()).as("parto esperado ya pasado").isEqualTo(1);
        assertThat(partos.get("totalGestantes").asLong()).isEqualTo(6);

        // A-104 toma la fecha de su inseminación del historial; solo A-190 no tiene ninguna.
        assertThat(animales(semanas.get(2).get("animales"))).as("semana 3").containsExactly("A-104");
        assertThat(partos.get("gestantesSinFecha").asLong()).isEqualTo(1);
        List<String> enLas12Semanas = new ArrayList<>();
        semanas.forEach(s -> enLas12Semanas.addAll(animales(s.get("animales"))));
        assertThat(enLas12Semanas).containsExactlyInAnyOrder("A-101", "A-104", "A-103");
    }

    @Test
    @DisplayName("H0044-06 - Salud y reproducción - Desempeño reproductivo del período")
    void H0044_06_desempenyoReproductivo() throws Exception {
        LocalDateTime ahora = ahora();
        LocalDate hoy = ahora.toLocalDate();
        insertarCelo(EST_A, "A-101", "Celo Detectado", "CONFIRMADA", ahora.minusDays(10));
        insertarCelo(EST_A, "A-104", "Celo Detectado", "ARCHIVADA", ahora.minusDays(5));
        insertarCelo(EST_A, "A-105", "Celo Detectado", "ARCHIVADA", ahora.minusDays(40));
        insertarAnestro(EST_A, "A-103", "PENDIENTE", ahora.minusDays(5));
        estadoReproductivo(EST_A, "A-103", "INSEMINADA", null, hoy.minusDays(20), ahora);
        estadoReproductivo(EST_A, "A-105", "GESTANTE", null, hoy.minusDays(25), ahora);

        JsonNode treinta = saludReproduccion(Map.of()).get("desempenyo");
        assertThat(treinta.get("reproductivos").asLong()).isEqualTo(6);
        assertThat(treinta.get("celosDetectados").asLong()).isEqualTo(4);
        assertThat(treinta.get("celosAtendidos").asLong()).isEqualTo(1);
        assertThat(treinta.get("ventanasPerdidas").asLong()).isEqualTo(1);
        assertThat(treinta.get("inseminadas").asLong()).isEqualTo(2);
        assertThat(treinta.get("gestantes").asLong()).isEqualTo(1);

        JsonNode siete = saludReproduccion(Map.of("periodoDias", 7));
        assertThat(siete.get("periodoDias").asInt()).isEqualTo(7);
        assertThat(siete.get("desempenyo").get("celosDetectados").asLong()).isEqualTo(3);
        assertThat(siete.get("desempenyo").get("celosAtendidos").asLong()).isZero();
        assertThat(siete.get("desempenyo").get("ventanasPerdidas").asLong()).isEqualTo(1);
        assertThat(siete.get("desempenyo").get("inseminadas").asLong()).isZero();
    }

    @Test
    @DisplayName("H0044-07 - Salud y reproducción - Reincidencia")
    void H0044_07_reincidencia() throws Exception {
        LocalDateTime ahora = ahora();
        insertarAlerta(EST_A, "A-102", "SALUD", "SALUD", "MEDIA", "DESCARTADA", ahora.minusDays(3));
        insertarAlerta(EST_A, "A-102", "Fuera de Perímetro", "FUERA_DE_PERIMETRO", "ALTA", "CONFIRMADA",
                ahora.minusDays(2));
        insertarAlerta(EST_A, "A-102", "SALUD", "SALUD", "BAJA", "CONFIRMADA", ahora.minusDays(1));
        insertarAlerta(EST_A, "A-104", "SALUD", "SALUD", "MEDIA", "CONFIRMADA", ahora.minusDays(2));
        insertarAlerta(EST_A, "A-104", "SALUD", "SALUD", "MEDIA", "CONFIRMADA", ahora.minusDays(1));
        insertarAlerta(EST_A, "A-103", "Fuera de Perímetro", "FUERA_DE_PERIMETRO", "ALTA", "CONFIRMADA",
                ahora.minusDays(1));
        insertarAlerta(EST_A, "A-106", "SALUD", "SALUD", "MEDIA", "CONFIRMADA", ahora.minusDays(40));

        JsonNode reincidencia = saludReproduccion(Map.of()).get("reincidencia");

        List<String> animales = new ArrayList<>();
        reincidencia.get("animales").forEach(a -> animales.add(a.get("animal").get("idCaravanaOficial").asText()
                + " " + a.get("alertas").asLong()));
        assertThat(animales).containsExactly("A-102 4", "A-104 3", "A-103 2");

        List<String> lotes = new ArrayList<>();
        reincidencia.get("lotes").forEach(l -> lotes.add(l.get("nombre").asText() + " " + l.get("salidas").asLong()));
        assertThat(lotes).as("Rodeo A2 tiene una sola salida").containsExactly("Rodeo A1 2");
    }

    @Test
    @DisplayName("H0044-08 - Salud y reproducción - Última intervención")
    void H0044_08_ultimaIntervencion() throws Exception {
        LocalDateTime ahora = ahora();
        intervencion(EST_A, "A-101", "VACUNACION", "veterinarioA", ahora.minusDays(10));
        intervencion(EST_A, "A-102", "TRATAMIENTO", "veterinarioA", ahora.minusDays(2));
        intervencion(EST_A, "A-103", "REVISION_VETERINARIA", "veterinarioA", ahora.minusDays(30));

        JsonNode ultima = saludReproduccion(Map.of()).get("ultimaIntervencion");

        JsonNode masReciente = ultima.get("masReciente");
        assertThat(masReciente.get("animal").get("idCaravanaOficial").asText()).isEqualTo("A-102");
        assertThat(masReciente.get("tipo").asText()).isEqualTo("TRATAMIENTO");
        assertThat(fecha(masReciente.get("fechaHora"))).isEqualTo(ahora.minusDays(2));
        assertThat(masReciente.get("interventor").asText()).startsWith("Veterinario");

        JsonNode ranking = ultima.get("sinIntervencionHaceMas");
        assertThat(ranking).hasSize(5);
        List<String> nunca = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            assertThat(ranking.get(i).get("dias").isNull()).isTrue();
            nunca.add(ranking.get(i).get("animal").get("idCaravanaOficial").asText());
        }
        assertThat(nunca).as("nunca tuvieron").containsExactlyInAnyOrder("A-104", "A-105", "A-106");
        assertThat(ranking.get(3).get("animal").get("idCaravanaOficial").asText()).isEqualTo("A-103");
        assertThat(ranking.get(3).get("dias").asLong()).isEqualTo(30);
        assertThat(ranking.get(4).get("animal").get("idCaravanaOficial").asText()).isEqualTo("A-101");
        assertThat(ranking.get(4).get("dias").asLong()).isEqualTo(10);
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private JsonNode saludReproduccion(Map<String, Object> params) throws Exception {
        return dashboardOk("veterinarioA", EST_A, PESTANIA, params);
    }

    /** Identificadores visibles de una lista de animales o de ítems que traen su {@code animal}. */
    private static List<String> animales(JsonNode lista) {
        List<String> ids = new ArrayList<>();
        lista.forEach(item -> ids.add((item.has("animal") ? item.get("animal") : item).get("idCaravanaOficial")
                .asText()));
        return ids;
    }
}
