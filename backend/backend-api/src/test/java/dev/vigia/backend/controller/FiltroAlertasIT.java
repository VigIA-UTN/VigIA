package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * GET /api/v1/alertas con los filtros tipo, severidad y estado. Sobre las cinco alertas pendientes de Fixture A se
 * suman cuatro, así cada tipo y cada severidad tiene más de una combinación posible:
 * <pre>
 *   CELO   ALTA  A-102 y A-106 (escenario)     SALUD  MEDIA A-103 (escenario)
 *   CELO   MEDIA A-101 (nueva)                 SALUD  BAJA  A-101 (nueva)
 *   FUERA_DE_PERIMETRO ALTA A-105 (escenario)  SALUD  ALTA  A-101 (nueva, CONFIRMADA)
 *   AISLAMIENTO_SOCIAL ALTA A-104 (escenario)  ABIGEATO BAJA A-101 (nueva)
 * </pre>
 */
@DisplayName("H0011 - Filtrar alertas por tipo/severidad — IT")
class FiltroAlertasIT extends CicloAlertasItBase {

    private Long celoAlta1;
    private Long celoAlta2;
    private Long celoMedia;
    private Long saludMedia;
    private Long saludBaja;
    private Long saludAltaConfirmada;
    private Long fuera;
    private Long aislamiento;
    private Long abigeato;

    private void armarConjunto() {
        celoAlta1 = idAlerta(EST_A, "A-102", "CELO");
        celoAlta2 = idAlerta(EST_A, "A-106", "CELO");
        saludMedia = idAlerta(EST_A, "A-103", "SALUD");
        fuera = idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO");
        aislamiento = idAlerta(EST_A, "A-104", "AISLAMIENTO_SOCIAL");
        LocalDateTime ahora = ahora();
        celoMedia = insertarAlerta(EST_A, "A-101", "Celo Detectado", "CELO", "MEDIA", "PENDIENTE", ahora.minusHours(5));
        saludBaja = insertarAlerta(EST_A, "A-101", "SALUD", "SALUD", "BAJA", "PENDIENTE", ahora.minusHours(6));
        saludAltaConfirmada = insertarAlerta(EST_A, "A-101", "SALUD", "SALUD", "ALTA", "CONFIRMADA",
                ahora.minusHours(7));
        abigeato = insertarAlerta(EST_A, "A-101", "Abigeato", "ABIGEATO", "BAJA", "PENDIENTE", ahora.minusHours(8));
    }

    @Test
    @DisplayName("H0011-01 - Filtro por tipo - Celo, sanitario, geoespacial y sus subtipos")
    void H0011_01_filtroPorTipo() throws Exception {
        armarConjunto();

        JsonNode celo = listadoOk("productorA", EST_A, "celo", null, null);
        assertThat(ids(celo)).containsExactlyInAnyOrder(celoAlta1, celoAlta2, celoMedia);
        assertThat(campo(celo, "tipo")).containsOnly("CELO");
        // "reproductivo" es el nombre del contador del mapa; no distingue mayúsculas.
        assertThat(ids(listadoOk("productorA", EST_A, "Reproductivo", null, null)))
                .containsExactlyInAnyOrder(celoAlta1, celoAlta2, celoMedia);

        JsonNode salud = listadoOk("productorA", EST_A, "sanitario", null, null);
        assertThat(ids(salud)).containsExactlyInAnyOrder(saludMedia, saludBaja, saludAltaConfirmada);
        assertThat(campo(salud, "tipo")).containsOnly("SALUD");
        assertThat(ids(listadoOk("productorA", EST_A, "SALUD", null, null)))
                .containsExactlyInAnyOrder(saludMedia, saludBaja, saludAltaConfirmada);

        // Geoespacial agrupa abigeato, fuera de perímetro y aislamiento; cada uno también se pide por separado.
        assertThat(ids(listadoOk("productorA", EST_A, "geoespacial", null, null)))
                .containsExactlyInAnyOrder(fuera, aislamiento, abigeato);
        assertThat(ids(listadoOk("productorA", EST_A, "perimetro", null, null))).containsExactly(fuera);
        assertThat(ids(listadoOk("productorA", EST_A, "fuera_de_perimetro", null, null))).containsExactly(fuera);
        assertThat(ids(listadoOk("productorA", EST_A, "aislamiento_social", null, null))).containsExactly(aislamiento);
        assertThat(ids(listadoOk("productorA", EST_A, "abigeato", null, null))).containsExactly(abigeato);
    }

    @Test
    @DisplayName("H0011-02 - Filtro por severidad - Alta, media y baja")
    void H0011_02_filtroPorSeveridad() throws Exception {
        armarConjunto();

        JsonNode alta = listadoOk("productorA", EST_A, null, "ALTA", null);
        assertThat(ids(alta)).containsExactlyInAnyOrder(celoAlta1, celoAlta2, saludAltaConfirmada, fuera, aislamiento);
        assertThat(campo(alta, "severidad")).containsOnly("ALTA");

        JsonNode media = listadoOk("productorA", EST_A, null, "MEDIA", null);
        assertThat(ids(media)).containsExactlyInAnyOrder(celoMedia, saludMedia);
        assertThat(campo(media, "severidad")).containsOnly("MEDIA");

        JsonNode baja = listadoOk("productorA", EST_A, null, "BAJA", null);
        assertThat(ids(baja)).containsExactlyInAnyOrder(saludBaja, abigeato);
        assertThat(campo(baja, "severidad")).containsOnly("BAJA");

        // No distingue mayúsculas ni espacios en los bordes.
        assertThat(ids(listadoOk("productorA", EST_A, null, " alta ", null)))
                .containsExactlyInAnyOrder(celoAlta1, celoAlta2, saludAltaConfirmada, fuera, aislamiento);
    }

    @Test
    @DisplayName("H0011-03 - Filtros combinados - Tipo y severidad juntos")
    void H0011_03_tipoYSeveridad() throws Exception {
        armarConjunto();

        JsonNode celoAlta = listadoOk("productorA", EST_A, "celo", "ALTA", null);
        assertThat(ids(celoAlta)).containsExactlyInAnyOrder(celoAlta1, celoAlta2);
        assertThat(ids(listadoOk("productorA", EST_A, "celo", "MEDIA", null))).containsExactly(celoMedia);
        assertThat(ids(listadoOk("productorA", EST_A, "sanitario", "MEDIA", null))).containsExactly(saludMedia);
        assertThat(ids(listadoOk("productorA", EST_A, "sanitario", "BAJA", null))).containsExactly(saludBaja);
        assertThat(ids(listadoOk("productorA", EST_A, "geoespacial", "ALTA", null)))
                .containsExactlyInAnyOrder(fuera, aislamiento);
        assertThat(ids(listadoOk("productorA", EST_A, "geoespacial", "BAJA", null))).containsExactly(abigeato);

        // Una combinación sin alertas es una lista vacía, no un error.
        MvcResult vacio = listar("productorA", EST_A, "abigeato", "ALTA", null);
        assertThat(estado(vacio)).isEqualTo(200);
        assertThat(leer(vacio)).isEmpty();
        assertThat(leer(ok(listar("productorA", EST_A, "perimetro", "BAJA", null)))).isEmpty();
    }

    @Test
    @DisplayName("H0011-04 - Filtros combinados - Con el estado y después de resolver una alerta")
    void H0011_04_conEstado() throws Exception {
        armarConjunto();

        assertThat(ids(listadoOk("productorA", EST_A, "sanitario", "ALTA", "CONFIRMADA")))
                .containsExactly(saludAltaConfirmada);
        assertThat(leer(ok(listar("productorA", EST_A, "sanitario", "ALTA", "PENDIENTE")))).isEmpty();
        // Sin tipo ni severidad, el estado solo deja las 8 pendientes.
        assertThat(listadoOk("productorA", EST_A, null, null, "PENDIENTE")).hasSize(8);

        // Al confirmar una de las dos de celo ALTA, cada filtro la ubica en su estado.
        ok(resolver("veterinarioA", celoAlta1, "CONFIRMADA", null));
        assertThat(ids(listadoOk("productorA", EST_A, "celo", "ALTA", "PENDIENTE"))).containsExactly(celoAlta2);
        JsonNode confirmadas = listadoOk("productorA", EST_A, "celo", "ALTA", "CONFIRMADA");
        assertThat(ids(confirmadas)).containsExactly(celoAlta1);
        assertThat(confirmadas.get(0).get("tsConfirmacion").isNull()).isFalse();
        assertThat(listadoOk("productorA", EST_A, null, null, "PENDIENTE")).hasSize(7);
    }

    @Test
    @DisplayName("H0011-05 - Filtros inválidos - Tipo, severidad o estado desconocidos y falta del establecimiento")
    void H0011_05_filtrosInvalidos() throws Exception {
        armarConjunto();

        // Un tipo desconocido no se interpreta como "sin filtro": devolvería todo sin avisar.
        MvcResult tipo = listar("productorA", EST_A, "inexistente", null, null);
        assertThat(estado(tipo)).as(texto(tipo)).isEqualTo(400);
        assertThat(texto(tipo)).contains("Tipo de alerta desconocido: inexistente");
        assertThat(estado(listar("productorA", EST_A, null, null, "RESUELTA"))).isEqualTo(400);
        // Una severidad desconocida tampoco se ignora: devolvería todas las alertas sin avisar.
        MvcResult severidad = listar("productorA", EST_A, null, "URGENTE", null);
        assertThat(estado(severidad)).as(texto(severidad)).isEqualTo(400);
        assertThat(texto(severidad)).contains("Severidad de alerta desconocida: URGENTE");
        // El establecimiento es obligatorio.
        assertThat(estado(mockMvc.perform(get("/api/v1/alertas").header("Authorization", bearer("productorA")))
                .andReturn())).isEqualTo(400);
    }

    @Test
    @DisplayName("H0011-06 - Filtros de alertas - Sin filtros, por rol y aislados por establecimiento")
    void H0011_06_sinFiltrosYAislamiento() throws Exception {
        armarConjunto();

        // Sin filtros: todas las del establecimiento y solo de sus animales.
        JsonNode todas = listadoOk("productorA", EST_A, null, null, null);
        assertThat(ids(todas)).hasSize(9).containsExactlyInAnyOrderElementsOf(idsAlertas(EST_A));
        for (String clave : new String[] {"veterinarioA", "camperoA"}) {
            assertThat(listadoOk(clave, EST_A, null, null, null)).as(clave).hasSize(9);
        }

        // Fixture B conserva sus cinco alertas del escenario aunque A tenga más, con y sin filtros.
        assertThat(ids(listadoOk("productorB", EST_B, null, null, null)))
                .containsExactlyInAnyOrderElementsOf(idsAlertas(EST_B)).hasSize(5);
        assertThat(ids(listadoOk("productorB", EST_B, "celo", "ALTA", null)))
                .containsExactlyInAnyOrder(idAlerta(EST_B, "B-102", "CELO"), idAlerta(EST_B, "B-106", "CELO"));

        // Quien está en los dos ve cada uno por separado; el administrador no opera alertas.
        assertThat(listadoOk("productorAmbos", EST_A, null, null, null)).hasSize(9);
        assertThat(listadoOk("productorAmbos", EST_B, null, null, null)).hasSize(5);
        assertThat(estado(listar("productorB", EST_A, "celo", null, null))).isEqualTo(403);
        assertThat(estado(listar("admin", EST_A, null, null, null))).isEqualTo(403);
    }
}
