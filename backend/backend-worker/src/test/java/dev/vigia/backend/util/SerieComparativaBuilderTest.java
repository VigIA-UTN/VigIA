package dev.vigia.backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.vigia.backend.dto.BloqueComparativoResponse;
import dev.vigia.backend.dto.SerieComparativaSnapshot;
import dev.vigia.backend.model.Telemetria;

class SerieComparativaBuilderTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 15, 0, 0);

    /** 288 puntos (1 cada 5 min = 24hs); primer bloque de 6hs con actividad baja, resto alta. */
    private List<Telemetria> ventana288() {
        List<Telemetria> lista = new ArrayList<>();
        for (int i = 0; i < 288; i++) {
            Telemetria t = new Telemetria();
            t.setFechaHora(BASE.plusMinutes(5L * i));
            t.setMoveState(i < 72 ? 1 : 5);
            t.setLatitud(-34.0 + 0.0001 * i);
            t.setLongitud(-60.0);
            lista.add(t);
        }
        return lista;
    }

    private Map<String, Object> perfil() {
        return Map.of(
                "quarter_moveState_mean", List.of(3.0, 2.0, 4.0, 3.5),
                "quarter_moveState_std", List.of(0.5, 0.4, 0.6, 0.5),
                "quarter_distancia_mean", List.of(10.0, 8.0, 12.0, 9.0),
                "quarter_distancia_std", List.of(2.0, 1.5, 2.5, 2.0));
    }

    @Test
    void build_conPerfilYVentana_produce4BloquesConActualYReferencia() {
        LocalDateTime perfilCalc = BASE.minusDays(3);

        SerieComparativaSnapshot snap = SerieComparativaBuilder.build(
                ventana288(), perfil(), perfilCalc, "MEDIA", "Enfermedad moderada / Cojera clínica");

        assertEquals(BASE, snap.getVentanaDesde());
        assertEquals(BASE.plusMinutes(5L * 287), snap.getVentanaHasta());
        assertEquals(perfilCalc, snap.getPerfilCalculadoEn());

        List<BloqueComparativoResponse> act = snap.getSerieActividad();
        assertEquals(4, act.size());
        assertEquals("0-6h", act.get(0).getBloque());
        assertEquals("18-24h", act.get(3).getBloque());
        assertEquals(1.0, act.get(0).getActual(), 1e-6);
        assertEquals(5.0, act.get(1).getActual(), 1e-6);
        assertEquals(3.0, act.get(0).getReferenciaMedia(), 1e-6);
        assertEquals(0.5, act.get(0).getReferenciaDesvio(), 1e-6);

        List<BloqueComparativoResponse> desp = snap.getSerieDesplazamiento();
        assertEquals(4, desp.size());
        // distancia ~ sqrt((0.0001)^2) * 100000 = 10.0 por paso (bloques 1..3 completos)
        assertEquals(10.0, desp.get(1).getActual(), 1e-6);
        assertEquals(8.0, desp.get(1).getReferenciaMedia(), 1e-6);

        // mayor desvío relativo de actividad: bloque "6-12h" (5.0 vs 2.0 => +150%)
        assertTrue(snap.getDescripcionDesvio().contains("6-12h"), snap.getDescripcionDesvio());
        assertTrue(snap.getDescripcionDesvio().contains("Enfermedad moderada / Cojera clínica"));
    }

    @Test
    void build_sinPerfil_referenciaEnNull() {
        SerieComparativaSnapshot snap = SerieComparativaBuilder.build(
                ventana288(), null, null, "BAJA", "Enfermedad leve / Inicio");

        List<BloqueComparativoResponse> act = snap.getSerieActividad();
        assertEquals(4, act.size());
        assertNotNull(act.get(0).getActual());
        assertNull(act.get(0).getReferenciaMedia());
        assertNull(act.get(0).getReferenciaDesvio());
        assertNull(snap.getPerfilCalculadoEn());
        assertNotNull(snap.getDescripcionDesvio());
    }

    @Test
    void build_perfilConArrayIncompleto_referenciaEnNull() {
        Map<String, Object> perfilRoto = Map.of(
                "quarter_moveState_mean", List.of(3.0, 2.0, 4.0), // solo 3
                "quarter_distancia_mean", List.of(10.0, 8.0, 12.0, 9.0));

        SerieComparativaSnapshot snap = SerieComparativaBuilder.build(
                ventana288(), perfilRoto, BASE, "MEDIA", "x");

        assertNull(snap.getSerieActividad().get(0).getReferenciaMedia());
        assertEquals(9.0, snap.getSerieDesplazamiento().get(3).getReferenciaMedia(), 1e-6);
    }

    @Test
    void build_ventanaVacia_seriesNull() {
        SerieComparativaSnapshot snap = SerieComparativaBuilder.build(
                Collections.emptyList(), perfil(), BASE, "ALTA", "Problema severo");

        assertNull(snap.getSerieActividad());
        assertNull(snap.getSerieDesplazamiento());
        assertNull(snap.getVentanaDesde());
        assertNull(snap.getVentanaHasta());
        assertNotNull(snap.getDescripcionDesvio());
        assertTrue(snap.getDescripcionDesvio().toLowerCase().contains("severidad alta"));
    }
}
