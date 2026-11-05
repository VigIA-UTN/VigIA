package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /api/v1/animales/{id}/linea-tiempo-alertas: alertas e intervenciones de un animal en orden cronológico. Se arma
 * sobre A-101, que no tiene alertas en el escenario:
 * <pre>
 *   -10 h  alerta CELO ALTA, confirmada por veterinario.a
 *    -9 h  intervención INSEMINACION sobre esa alerta
 *    -8 h  alerta SALUD BAJA, confirmada por productor.a
 *    -7 h  intervención TRATAMIENTO sobre esa alerta
 *    -6 h  alerta SALUD MEDIA, pendiente
 *    -3 h  alerta FUERA_DE_PERIMETRO ALTA, descartada por campero.a
 * </pre>
 */
@DisplayName("H0024 - Línea de tiempo de alertas por animal — IT")
class LineaTiempoAnimalIT extends CicloAlertasItBase {

    private LocalDateTime base;
    private Long animal;
    private Long celo;
    private Long saludBaja;
    private Long saludMedia;
    private Long perimetro;

    private void armarHistorial() throws Exception {
        base = ahora();
        animal = idAnimal(EST_A, "A-101");
        celo = insertarAlerta(EST_A, "A-101", "Celo Detectado", "CELO", "ALTA", "PENDIENTE", base.minusHours(10));
        saludBaja = insertarAlerta(EST_A, "A-101", "SALUD", "SALUD", "BAJA", "PENDIENTE", base.minusHours(8));
        saludMedia = insertarAlerta(EST_A, "A-101", "SALUD", "SALUD", "MEDIA", "PENDIENTE", base.minusHours(6));
        perimetro = insertarAlerta(EST_A, "A-101", "Fuera de Perímetro", "FUERA_DE_PERIMETRO", "ALTA", "PENDIENTE",
                base.minusHours(3));

        ok(resolver("veterinarioA", celo, "CONFIRMADA", null));
        assertThat(estado(intervenir("veterinarioA", celo, "INSEMINACION", "IA con semen Angus", base.minusHours(9),
                null))).isEqualTo(200);
        ok(resolver("productorA", saludBaja, "CONFIRMADA", null));
        assertThat(estado(intervenir("productorA", saludBaja, "TRATAMIENTO", "Vitaminas", base.minusHours(7), null)))
                .isEqualTo(200);
        ok(resolver("camperoA", perimetro, "DESCARTADA", "Estaba en el potrero vecino"));
    }

    @Test
    @DisplayName("H0024-01 - Línea de tiempo - Alertas e intervenciones en orden cronológico")
    void H0024_01_ordenCronologico() throws Exception {
        armarHistorial();

        JsonNode eventos = leer(ok(lineaTiempo("productorA", animal, null, null, null)));

        assertThat(campo(eventos, "tipoEvento")).containsExactly(
                "ALERTA", "INTERVENCION", "ALERTA", "INTERVENCION", "ALERTA", "ALERTA");
        List<LocalDateTime> fechas = new ArrayList<>();
        eventos.forEach(e -> fechas.add(LocalDateTime.parse(e.get("fecha").asText())));
        assertThat(fechas).isSorted();
        assertThat(fechas.get(0)).isEqualTo(base.minusHours(10));
        assertThat(fechas.get(1)).isEqualTo(base.minusHours(9));
        assertThat(fechas.get(5)).isEqualTo(base.minusHours(3));
        // Las alertas son las del animal, y cada intervención apunta a la suya.
        assertThat(eventos.get(0).get("id").asLong()).isEqualTo(celo);
        assertThat(eventos.get(1).get("alertaAsociadaId").asLong()).isEqualTo(celo);
        assertThat(eventos.get(3).get("alertaAsociadaId").asLong()).isEqualTo(saludBaja);
        assertThat(eventos.get(4).get("id").asLong()).isEqualTo(saludMedia);
        assertThat(eventos.get(5).get("id").asLong()).isEqualTo(perimetro);
    }

    @Test
    @DisplayName("H0024-02 - Línea de tiempo - Datos de cada evento: dato disparador, resolución e intervención")
    void H0024_02_datosDeCadaEvento() throws Exception {
        armarHistorial();

        JsonNode eventos = leer(ok(lineaTiempo("veterinarioA", animal, null, null, null)));

        JsonNode confirmada = eventos.get(0);
        assertThat(confirmada.get("tipo").asText()).isEqualTo("CELO");
        assertThat(confirmada.get("subtipo").asText()).isEqualTo("Celo Detectado");
        assertThat(confirmada.get("severidad").asText()).isEqualTo("ALTA");
        assertThat(confirmada.get("valorDetectado").asDouble()).isEqualTo(0.8);
        assertThat(confirmada.get("valorUmbral").asDouble()).isEqualTo(0.5);
        assertThat(confirmada.get("estadoResolucion").asText()).isEqualTo("CONFIRMADA");
        assertThat(confirmada.get("usuarioResolucion").asText()).isEqualTo("Veterinario A");
        assertThat(confirmada.get("tsResolucion").isNull()).isFalse();

        JsonNode pendiente = eventos.get(4);
        assertThat(pendiente.get("estadoResolucion").asText()).isEqualTo("SIN_ATENDER");
        assertThat(pendiente.get("usuarioResolucion").isNull()).isTrue();
        assertThat(pendiente.get("tsResolucion").isNull()).isTrue();

        JsonNode descartada = eventos.get(5);
        assertThat(descartada.get("estadoResolucion").asText()).isEqualTo("DESCARTADA");
        assertThat(descartada.get("usuarioResolucion").asText()).isEqualTo("Campero A");
        assertThat(descartada.get("tipo").asText()).isEqualTo("FUERA_DE_PERIMETRO");

        JsonNode intervencion = eventos.get(1);
        assertThat(intervencion.get("tipoIntervencion").asText()).isEqualTo("INSEMINACION");
        assertThat(intervencion.get("descripcion").asText()).isEqualTo("IA con semen Angus");
        assertThat(intervencion.get("interventor").asText()).isEqualTo("Veterinario A");
        assertThat(intervencion.get("severidad").isNull()).isTrue();
    }

    @Test
    @DisplayName("H0024-03 - Línea de tiempo - Filtros por tipo y por rango de fechas")
    void H0024_03_filtros() throws Exception {
        armarHistorial();

        // El tipo filtra las alertas; las intervenciones del animal se mantienen.
        JsonNode celoYSusIntervenciones = leer(ok(lineaTiempo("productorA", animal, "celo", null, null)));
        assertThat(campo(celoYSusIntervenciones, "tipoEvento"))
                .containsExactly("ALERTA", "INTERVENCION", "INTERVENCION");
        assertThat(celoYSusIntervenciones.get(0).get("id").asLong()).isEqualTo(celo);
        JsonNode sanitario = leer(ok(lineaTiempo("productorA", animal, "sanitario", null, null)));
        assertThat(campo(sanitario, "tipoEvento")).containsExactly("INTERVENCION", "ALERTA", "INTERVENCION", "ALERTA");
        assertThat(sanitario.get(1).get("id").asLong()).isEqualTo(saludBaja);
        assertThat(sanitario.get(3).get("id").asLong()).isEqualTo(saludMedia);

        // El rango es inclusivo y alcanza a alertas e intervenciones.
        JsonNode rango = leer(ok(lineaTiempo("productorA", animal, null, base.minusHours(9), base.minusHours(6))));
        assertThat(campo(rango, "tipoEvento")).containsExactly("INTERVENCION", "ALERTA", "INTERVENCION", "ALERTA");
        assertThat(rango.get(3).get("id").asLong()).isEqualTo(saludMedia);
        assertThat(campo(leer(ok(lineaTiempo("productorA", animal, "sanitario", base.minusHours(7), null))), "tipoEvento"))
                .containsExactly("INTERVENCION", "ALERTA");
        // Un rango sin eventos devuelve la lista vacía.
        assertThat(leer(ok(lineaTiempo("productorA", animal, null, base.minusHours(2), base.minusHours(1))))).isEmpty();
    }

    @Test
    @DisplayName("H0024-04 - Línea de tiempo - Animal sin eventos, accesos y filtros inválidos")
    void H0024_04_accesoYCasosBorde() throws Exception {
        // A-101 todavía no tiene alertas: lista vacía, para los tres roles del establecimiento.
        for (String clave : new String[] {"productorA", "veterinarioA", "camperoA"}) {
            MvcResult vacia = lineaTiempo(clave, idAnimal(EST_A, "A-101"), null, null, null);
            assertThat(estado(vacia)).as(clave).isEqualTo(200);
            assertThat(leer(vacia)).as(clave).isEmpty();
        }
        // Un animal con una sola alerta del escenario.
        JsonNode unaAlerta = leer(ok(lineaTiempo("productorA", idAnimal(EST_A, "A-105"), null, null, null)));
        assertThat(unaAlerta).hasSize(1);
        assertThat(unaAlerta.get(0).get("tipo").asText()).isEqualTo("FUERA_DE_PERIMETRO");

        Long deA = idAnimal(EST_A, "A-105");
        // Otro establecimiento y el administrador no acceden; el animal inexistente y el tipo desconocido, 400.
        assertThat(estado(lineaTiempo("productorB", deA, null, null, null))).isEqualTo(403);
        assertThat(estado(lineaTiempo("admin", deA, null, null, null))).isEqualTo(403);
        Long inexistente = jdbcTemplate.queryForObject("SELECT coalesce(max(id), 0) + 1000 FROM animales", Long.class);
        assertThat(estado(lineaTiempo("productorA", inexistente, null, null, null))).isEqualTo(400);
        MvcResult tipo = lineaTiempo("productorA", deA, "inexistente", null, null);
        assertThat(estado(tipo)).isEqualTo(400);
        assertThat(texto(tipo)).contains("Tipo de alerta desconocido");
    }
}
