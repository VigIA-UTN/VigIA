package dev.vigia.backend.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.vigia.backend.dto.BloqueComparativoResponse;
import dev.vigia.backend.dto.SerieComparativaSnapshot;
import dev.vigia.backend.model.Telemetria;

/**
 * Arma el {@link SerieComparativaSnapshot} de una alerta sanitaria a partir de
 * la ventana de telemetría que evaluó ml-salud y del perfil de referencia
 * individual del animal (perfil YA deserializado a {@code Map}).
 *
 * <p>Se ejecuta UNA vez, en el worker, al crear la alerta; el resultado se
 * persiste como JSON en {@code alertas.serie_comparativa_json} y el endpoint de
 * detalle solo lo deserializa. La ventana evaluada es un hecho del pasado
 * (inmutable) y el perfil se congela tal como estaba en ese momento, así que no
 * hay nada que recalcular después.
 *
 * <p>La agregación replica el criterio de ml-salud (features_salud.py): la
 * ventana de 24hs se parte en 4 bloques de 6hs ("quarters") y se promedia
 * {@code moveState} (actividad) y la distancia GPS escalada (desplazamiento)
 * por bloque, contra la media/desvío del perfil para ese mismo bloque.
 */
public final class SerieComparativaBuilder {

    private static final String[] BLOQUES_LABELS = { "0-6h", "6-12h", "12-18h", "18-24h" };
    private static final int N_BLOQUES = 4;
    private static final long MINUTOS_POR_BLOQUE = 6 * 60;
    /** Mismo factor de escala que features_salud.py::distancias_desde_coords. */
    private static final double DIST_SCALE_FACTOR = 100000.0;

    private SerieComparativaBuilder() {
    }

    /**
     * @param ventana           puntos de telemetría evaluados (orden ascendente por fechaHora); puede venir vacía
     * @param perfil            perfil de referencia ya deserializado (arrays {@code quarter_*}); puede ser null
     * @param perfilCalculadoEn fecha de cálculo del perfil; puede ser null
     * @param severidad         severidad de la alerta (para la descripción)
     * @param posibleCausa      causa probable de ml-salud (para la descripción)
     */
    public static SerieComparativaSnapshot build(List<Telemetria> ventana, Map<String, Object> perfil,
            LocalDateTime perfilCalculadoEn, String severidad, String posibleCausa) {

        List<BloqueComparativoResponse> serieActividad = null;
        List<BloqueComparativoResponse> serieDesplazamiento = null;
        LocalDateTime ventanaDesde = null;
        LocalDateTime ventanaHasta = null;

        if (ventana != null && !ventana.isEmpty()) {
            ventanaDesde = ventana.get(0).getFechaHora();
            ventanaHasta = ventana.get(ventana.size() - 1).getFechaHora();
            serieActividad = construirSerie(ventana, ventanaDesde, perfil,
                    valoresMoveState(ventana), "quarter_moveState_mean", "quarter_moveState_std");
            serieDesplazamiento = construirSerie(ventana, ventanaDesde, perfil,
                    valoresDistancia(ventana), "quarter_distancia_mean", "quarter_distancia_std");
        }

        return SerieComparativaSnapshot.builder()
                .descripcionDesvio(generarDescripcion(severidad, posibleCausa, serieActividad))
                .ventanaDesde(ventanaDesde)
                .ventanaHasta(ventanaHasta)
                .perfilCalculadoEn(perfilCalculadoEn)
                .serieActividad(serieActividad)
                .serieDesplazamiento(serieDesplazamiento)
                .build();
    }

    /** moveState por punto (null -> NaN, se ignora en el promedio). */
    private static double[] valoresMoveState(List<Telemetria> puntos) {
        double[] v = new double[puntos.size()];
        for (int i = 0; i < puntos.size(); i++) {
            Integer ms = puntos.get(i).getMoveState();
            v[i] = ms != null ? ms.doubleValue() : Double.NaN;
        }
        return v;
    }

    /**
     * Distancia escalada entre puntos GPS consecutivos, mismo criterio que
     * features_salud.py::distancias_desde_coords (proxy relativo, no geodésico).
     * El primer punto vale 0.
     */
    private static double[] valoresDistancia(List<Telemetria> puntos) {
        double[] v = new double[puntos.size()];
        for (int i = 1; i < puntos.size(); i++) {
            Double lat0 = puntos.get(i - 1).getLatitud();
            Double lon0 = puntos.get(i - 1).getLongitud();
            Double lat1 = puntos.get(i).getLatitud();
            Double lon1 = puntos.get(i).getLongitud();
            if (lat0 == null || lon0 == null || lat1 == null || lon1 == null) {
                v[i] = Double.NaN;
                continue;
            }
            double dLat = lat1 - lat0;
            double dLon = lon1 - lon0;
            v[i] = Math.sqrt(dLat * dLat + dLon * dLon) * DIST_SCALE_FACTOR;
        }
        return v;
    }

    /**
     * Promedia {@code valores} por bloque de 6hs (bucket por timestamp) y le
     * adosa la media/desvío del perfil para ese bloque. Si {@code perfil} es
     * null o no trae el array esperado, la referencia queda en null.
     */
    private static List<BloqueComparativoResponse> construirSerie(List<Telemetria> puntos, LocalDateTime desde,
            Map<String, Object> perfil, double[] valores, String keyMedia, String keyDesvio) {
        double[] suma = new double[N_BLOQUES];
        int[] conteo = new int[N_BLOQUES];
        for (int i = 0; i < puntos.size(); i++) {
            if (Double.isNaN(valores[i])) {
                continue;
            }
            long minutos = Duration.between(desde, puntos.get(i).getFechaHora()).toMinutes();
            int b = (int) (minutos / MINUTOS_POR_BLOQUE);
            if (b < 0) {
                b = 0;
            }
            if (b >= N_BLOQUES) {
                b = N_BLOQUES - 1;
            }
            suma[b] += valores[i];
            conteo[b]++;
        }

        double[] refMedia = quarterArray(perfil, keyMedia);
        double[] refDesvio = quarterArray(perfil, keyDesvio);

        List<BloqueComparativoResponse> serie = new ArrayList<>(N_BLOQUES);
        for (int b = 0; b < N_BLOQUES; b++) {
            serie.add(BloqueComparativoResponse.builder()
                    .bloque(BLOQUES_LABELS[b])
                    .actual(conteo[b] > 0 ? suma[b] / conteo[b] : null)
                    .referenciaMedia(refMedia != null ? refMedia[b] : null)
                    .referenciaDesvio(refDesvio != null ? refDesvio[b] : null)
                    .build());
        }
        return serie;
    }

    /** Lee un array de 4 números del perfil JSON de ml-salud; null si no está o no es válido. */
    private static double[] quarterArray(Map<String, Object> perfil, String key) {
        if (perfil == null) {
            return null;
        }
        Object raw = perfil.get(key);
        if (!(raw instanceof List<?>)) {
            return null;
        }
        List<?> lista = (List<?>) raw;
        if (lista.size() < N_BLOQUES) {
            return null;
        }
        double[] out = new double[N_BLOQUES];
        for (int i = 0; i < N_BLOQUES; i++) {
            if (!(lista.get(i) instanceof Number)) {
                return null;
            }
            out[i] = ((Number) lista.get(i)).doubleValue();
        }
        return out;
    }

    /**
     * Descripción del desvío: si hay serie con referencia, resalta el bloque de
     * 6hs con mayor desvío relativo de actividad; si no, un texto genérico con
     * severidad y causa.
     */
    private static String generarDescripcion(String severidad, String posibleCausa,
            List<BloqueComparativoResponse> serieActividad) {
        String sev = severidad != null ? severidad.toLowerCase() : "desconocida";
        String causa = (posibleCausa != null && !posibleCausa.isBlank()) ? posibleCausa : "no determinada";

        if (serieActividad != null) {
            BloqueComparativoResponse peor = null;
            double peorPct = 0.0;
            for (BloqueComparativoResponse b : serieActividad) {
                if (b.getActual() == null || b.getReferenciaMedia() == null || b.getReferenciaMedia() == 0.0) {
                    continue;
                }
                double pct = (b.getActual() - b.getReferenciaMedia()) / Math.abs(b.getReferenciaMedia()) * 100.0;
                if (Math.abs(pct) > Math.abs(peorPct)) {
                    peorPct = pct;
                    peor = b;
                }
            }
            if (peor != null) {
                String dir = peorPct < 0 ? "por debajo" : "por encima";
                return String.format(
                        "Actividad promedio %.0f%% %s del perfil de referencia del animal en el bloque %s. Severidad %s. Posible causa: %s.",
                        Math.abs(peorPct), dir, peor.getBloque(), sev, causa);
            }
        }
        return String.format(
                "Desvío sanitario de severidad %s detectado respecto del perfil de referencia del animal. Posible causa: %s.",
                sev, causa);
    }
}
