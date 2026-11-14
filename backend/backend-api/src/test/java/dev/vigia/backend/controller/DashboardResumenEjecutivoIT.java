package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sobre el escenario: Fixture A tiene el Rodeo A1 (A-101, A-102, A-104, A-105 y A-106) y el Rodeo A2 (solo A-103),
 * con los 6 animales sin perfil de salud (en calibración) y 5 alertas pendientes generadas en las últimas horas: celo
 * en A-102 y A-106, sanitaria en A-103, aislamiento en A-104 y perímetro en A-105.
 * <p>
 * La pestaña es de todo el establecimiento (no se filtra por lote). Las secciones "Actividad del rodeo", "Dónde
 * ocurren" y "Carga animal" quedan pendientes en la HU, y cómo se destacan los rodeos que se apartan del promedio es
 * del frontend (N/A en la API).
 */
@DisplayName("H0045 - Dashboard: Resumen ejecutivo — IT")
class DashboardResumenEjecutivoIT extends MapaDashboardItBase {

    private static final String PESTANIA = "resumen-ejecutivo";

    @Test
    @DisplayName("H0045-01 - Resumen ejecutivo - Tabla por rodeo y promedio")
    void H0045_01_tablaPorRodeo() throws Exception {
        LocalDateTime ahora = ahora();
        for (String animal : List.of("A-101", "A-102", "A-103", "A-104", "A-105")) {
            perfilSalud(EST_A, animal);
        }
        estadoReproductivo(EST_A, "A-101", "VACIA", ahora.toLocalDate().minusDays(100), null, ahora);
        estadoReproductivo(EST_A, "A-104", "VACIA", ahora.toLocalDate().minusDays(80), null, ahora);
        insertarCelo(EST_A, "A-104", "Celo Detectado", "ARCHIVADA", ahora.minusDays(10));
        insertarCelo(EST_A, "A-105", "Celo Detectado", "VENCIDA", ahora.minusDays(20));
        insertarCelo(EST_A, "A-101", "Celo Detectado", "ARCHIVADA", ahora.minusDays(40));
        insertarAnestro(EST_A, "A-102", "ARCHIVADA", ahora.minusDays(5));
        Long vacio = crearRodeo(EST_A, "Rodeo A3", true);
        crearRodeo(EST_A, "Rodeo A4", false);

        JsonNode re = resumen(Map.of());

        assertThat(re.get("periodoDias").asInt()).isEqualTo(30);
        JsonNode rodeos = re.get("rodeos");
        List<String> nombres = new ArrayList<>();
        rodeos.forEach(r -> nombres.add(r.get("nombre").asText()));
        assertThat(nombres).as("solo los rodeos activos").containsExactly("Rodeo A1", "Rodeo A2", "Rodeo A3");

        JsonNode a1 = rodeos.get(0);
        assertThat(a1.get("animales").asLong()).isEqualTo(5);
        assertThat(a1.get("coberturaPorcentaje").asInt()).as("A-106 sigue en calibración").isEqualTo(80);
        assertThat(a1.get("alertasPendientes").asLong()).isEqualTo(4);
        assertThat(a1.get("alertasCada100").asInt()).isEqualTo(80);
        assertThat(a1.get("ventanasPerdidas").asLong()).as("la de 40 días y el anestro no cuentan").isEqualTo(2);
        assertThat(a1.get("vaciasLargasPorcentaje").asInt()).as("A-101, 1 de 5").isEqualTo(20);

        JsonNode a2 = rodeos.get(1);
        assertThat(a2.get("animales").asLong()).isEqualTo(1);
        assertThat(a2.get("coberturaPorcentaje").asInt()).isEqualTo(100);
        assertThat(a2.get("alertasPendientes").asLong()).isEqualTo(1);
        assertThat(a2.get("alertasCada100").asInt()).isEqualTo(100);
        assertThat(a2.get("ventanasPerdidas").asLong()).isZero();
        assertThat(a2.get("vaciasLargasPorcentaje").asInt()).isZero();

        JsonNode a3 = rodeos.get(2);
        assertThat(a3.get("id").asLong()).isEqualTo(vacio);
        assertThat(a3.get("animales").asLong()).isZero();
        assertThat(a3.get("alertasPendientes").asLong()).isZero();

        // Promedio de los rodeos con animales: el vacío no entra.
        JsonNode promedio = re.get("promedio");
        assertThat(promedio.get("coberturaPorcentaje").asInt()).isEqualTo(90);
        assertThat(promedio.get("alertasCada100").asInt()).isEqualTo(90);
        assertThat(promedio.get("ventanasPerdidas").asInt()).isEqualTo(1);
        assertThat(promedio.get("vaciasLargasPorcentaje").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("H0045-02 - Resumen ejecutivo - Período")
    void H0045_02_periodo() throws Exception {
        LocalDateTime ahora = ahora();
        insertarCelo(EST_A, "A-104", "Celo Detectado", "ARCHIVADA", ahora.minusDays(10));
        insertarCelo(EST_A, "A-105", "Celo Detectado", "VENCIDA", ahora.minusDays(20));

        assertThat(resumen(Map.of()).get("rodeos").get(0).get("ventanasPerdidas").asLong()).isEqualTo(2);

        JsonNode quince = resumen(Map.of("periodoDias", 15));
        assertThat(quince.get("periodoDias").asInt()).isEqualTo(15);
        assertThat(quince.get("rodeos").get(0).get("ventanasPerdidas").asLong()).isEqualTo(1);

        for (int invalido : new int[] {0, 366}) {
            int estado = mockMvc.perform(get("/api/v1/establecimientos/{id}/dashboard/" + PESTANIA,
                            idEstablecimiento(EST_A))
                            .param("periodoDias", String.valueOf(invalido))
                            .header("Authorization", bearer("productorA")))
                    .andReturn().getResponse().getStatus();
            assertThat(estado).as("período de %d días", invalido).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("H0045-03 - Resumen ejecutivo - Tendencia de 8 semanas por tipo")
    void H0045_03_tendencia() throws Exception {
        LocalDateTime ahora = ahora();
        insertarCelo(EST_A, "A-101", "Celo Detectado", "CONFIRMADA", ahora.minusDays(7));
        insertarAlerta(EST_A, "A-102", "SALUD", "SALUD", "MEDIA", "DESCARTADA", ahora.minusDays(14));
        insertarAlerta(EST_A, "A-103", "Fuera de Perímetro", "FUERA_DE_PERIMETRO", "ALTA", "CONFIRMADA",
                ahora.minusDays(49));
        insertarCelo(EST_A, "A-104", "Celo Detectado", "ARCHIVADA", ahora.minusDays(63)); // hace 9 semanas
        insertarAnestro(EST_A, "A-105", "PENDIENTE", ahora);

        JsonNode tendencia = resumen(Map.of()).get("tendencia");

        List<String> semanas = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            semanas.add(semanaIso(ahora.minusWeeks(i)));
        }
        List<String> obtenidas = new ArrayList<>();
        tendencia.get("semanas").forEach(s -> obtenidas.add(s.asText()));
        assertThat(obtenidas).as("las 8 últimas, la actual al final").isEqualTo(semanas);
        List<String> tipos = new ArrayList<>();
        tendencia.get("tipos").forEach(t -> tipos.add(t.asText()));
        assertThat(tipos).containsExactly("CELO", "SALUD", "FUERA_DE_PERIMETRO");

        // Lo que se espera en cada semana: las alertas del escenario (de las últimas horas) y las insertadas. El
        // anestro, el aislamiento y el celo de hace 9 semanas no cuentan.
        Map<String, List<LocalDateTime>> generadas = new LinkedHashMap<>();
        generadas.put("CELO", List.of(ahora.minusHours(1), ahora.minusHours(4), ahora.minusDays(7)));
        generadas.put("SALUD", List.of(ahora.minusHours(2), ahora.minusDays(14)));
        generadas.put("FUERA_DE_PERIMETRO", List.of(ahora.minusMinutes(30), ahora.minusDays(49)));
        for (Map.Entry<String, List<LocalDateTime>> tipo : generadas.entrySet()) {
            List<Long> esperadas = new ArrayList<>();
            for (String semana : semanas) {
                esperadas.add(tipo.getValue().stream().filter(ts -> semana.equals(semanaIso(ts))).count());
            }
            List<Long> datos = new ArrayList<>();
            tendencia.get("datos").get(tipo.getKey()).forEach(n -> datos.add(n.asLong()));
            assertThat(datos).as(tipo.getKey()).isEqualTo(esperadas);
        }
    }

    @Test
    @DisplayName("H0045-04 - Resumen ejecutivo - ¿Está actuando el equipo?")
    void H0045_04_actuacionDelEquipo() throws Exception {
        LocalDateTime ahora = ahora();
        LocalDateTime g1 = ahora.minusDays(10);
        LocalDateTime g2 = ahora.minusDays(8);
        LocalDateTime g3 = ahora.minusDays(6);
        LocalDateTime g4 = ahora.minusDays(20);
        insertarAlertaResuelta(EST_A, "A-101", "SALUD", "SALUD", "CONFIRMADA", g1, g1.plusHours(2));
        insertarAlertaResuelta(EST_A, "A-102", "SALUD", "SALUD", "DESCARTADA", g2, g2.plusHours(6)); // borde: 6-24 h
        insertarAlertaResuelta(EST_A, "A-103", "Fuera de Perímetro", "FUERA_DE_PERIMETRO", "CONFIRMADA", g3,
                g3.plusHours(30));
        insertarAlertaResuelta(EST_A, "A-104", "Celo Detectado", "CELO", "CONFIRMADA", g4, g4.plusDays(4));
        insertarCelo(EST_A, "A-105", "Celo Detectado", "ARCHIVADA", ahora.minusDays(12)); // ventana perdida
        insertarAnestro(EST_A, "A-106", "ARCHIVADA", ahora.minusDays(5)); // se cerró sola: no es del equipo
        LocalDateTime vieja = ahora.minusDays(40);
        insertarAlertaResuelta(EST_A, "A-101", "SALUD", "SALUD", "CONFIRMADA", vieja, vieja.plusHours(1));

        JsonNode equipo = resumen(Map.of()).get("equipo");

        assertThat(equipo.get("enCola").asLong()).as("las 5 pendientes del escenario").isEqualTo(5);
        // 10 generadas en el período (5 pendientes, 4 resueltas y el celo perdido); 4 atendidas, 1 descartada.
        assertThat(equipo.get("porcentajeAtendidas").asInt()).isEqualTo(40);
        assertThat(equipo.get("porcentajeDescartadas").asInt()).isEqualTo(25);
        assertThat(equipo.get("totalAtendidas").asLong()).isEqualTo(4);

        Map<String, Long> rangos = new LinkedHashMap<>();
        equipo.get("tiempoAtencion").forEach(r -> rangos.put(r.get("rango").asText(), r.get("alertas").asLong()));
        assertThat(rangos).containsExactly(Map.entry("<6h", 1L), Map.entry("6-24h", 1L), Map.entry("1-3d", 1L),
                Map.entry(">3d", 1L));
    }

    @Test
    @DisplayName("H0045-05 - Resumen ejecutivo - Intervenciones del establecimiento")
    void H0045_05_intervencionesDelEstablecimiento() throws Exception {
        LocalDateTime ahora = ahora();
        intervencion(EST_A, "A-101", "VACUNACION", "veterinarioA", ahora.minusDays(10));
        intervencion(EST_A, "A-102", "TRATAMIENTO", "veterinarioA", ahora.minusDays(2));
        intervencion(EST_B, "B-101", "TRATAMIENTO", "veterinarioB", ahora.minusDays(1));

        var resultado = mockMvc.perform(get("/api/v1/establecimientos/{id}/intervenciones", idEstablecimiento(EST_A))
                        .header("Authorization", bearer("productorA")))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        JsonNode intervenciones = leer(resultado);

        assertThat(intervenciones).as("solo las de Fixture A").hasSize(2);
        JsonNode primera = intervenciones.get(0);
        assertThat(primera.get("animalId").asLong()).as("la más reciente primero").isEqualTo(idAnimal(EST_A, "A-102"));
        assertThat(primera.get("tipo").asText()).isEqualTo("TRATAMIENTO");
        assertThat(fecha(primera.get("fechaHora"))).isEqualTo(ahora.minusDays(2));
        assertThat(primera.get("interventor").asText()).startsWith("Veterinario");
        assertThat(intervenciones.get(1).get("animalId").asLong()).isEqualTo(idAnimal(EST_A, "A-101"));
        assertThat(intervenciones.get(1).get("tipo").asText()).isEqualTo("VACUNACION");
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private JsonNode resumen(Map<String, Object> params) throws Exception {
        return dashboardOk("productorA", EST_A, PESTANIA, params);
    }

    /** Semana ISO 8601 ({@code 2026-W45}), igual que la tendencia. */
    private static String semanaIso(LocalDateTime momento) {
        return String.format("%d-W%02d", momento.get(IsoFields.WEEK_BASED_YEAR),
                momento.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
