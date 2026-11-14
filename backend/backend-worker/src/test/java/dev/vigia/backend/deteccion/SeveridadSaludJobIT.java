package dev.vigia.backend.deteccion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** A-101 ya calibrada (con perfil) y con su última lectura pendiente: cada caso cambia solo lo que responde ml-salud. */
@DisplayName("H0018 - Detalle de alerta sanitaria con causa probable (job de salud) — IT")
class SeveridadSaludJobIT extends SaludItBase {

    private Long animal;
    private LocalDateTime perfilCalculado;

    @BeforeEach
    void animalCalibrado() {
        animal = idAnimal(EST_A, "A-101");
        perfilCalculado = cargarPerfil(animal);
        marcarUltimaLecturaPendiente(animal, "salud");
    }

    @ParameterizedTest(name = "H0018-01 - Severidad {0} de ml-salud se guarda como {1}")
    @CsvSource({"1, BAJA", "2, MEDIA", "3, ALTA"})
    @DisplayName("H0018-01 - Severidad - Se persiste la severidad que devuelve ml-salud")
    void H0018_01_severidadPersistida(int severidad, String esperada) throws Exception {
        ML.responder(MlSimulado.SALUD_PREDICT, desvio(severidad, "Disminución de actividad", 0.77));

        correr(saludJob);

        Map<String, Object> alerta = unicaAlerta();
        assertThat(alerta.get("severidad")).isEqualTo(esperada);
        assertThat(alerta.get("estado")).isEqualTo("PENDIENTE");
        assertThat(((Number) alerta.get("confianza_modelo")).doubleValue()).isCloseTo(0.77, within(0.001));
        assertThat(((Number) alerta.get("valor_detectado")).doubleValue()).isCloseTo(0.77, within(0.001));
        assertThat(((Number) alerta.get("desvio_detectado")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("H0018-02 - Causa probable - Se persiste la causa y la acción recomendada según la severidad")
    void H0018_02_causaYAccionRecomendada() throws Exception {
        ML.responder(MlSimulado.SALUD_PREDICT, desvio(3, "Posible cojera", 0.93));

        correr(saludJob);

        Map<String, Object> alerta = unicaAlerta();
        assertThat(alerta.get("posible_causa")).isEqualTo("Posible cojera");
        assertThat(alerta.get("accion_recomendada")).isEqualTo("Atención veterinaria urgente y aislamiento en "
                + "enfermería. Causa sospechada: Posible cojera (riesgo alto).");
    }

    @Test
    @DisplayName("H0018-03 - Causa probable - Respuesta sin causa o sin severidad")
    void H0018_03_sinCausaOSinSeveridad() throws Exception {
        // Desvío con severidad 0 es una respuesta inconsistente: no se alerta.
        ML.responder(MlSimulado.SALUD_PREDICT, desvio(0, "Disminución de actividad", 0.6));
        correr(saludJob);
        assertThat(alertas(animal, "SALUD")).isEmpty();

        // Sin causa: la alerta se guarda igual y la acción no muestra "null".
        ML.responder(MlSimulado.SALUD_PREDICT, desvio(1, null, 0.55));
        insertarLecturaNueva(animal);
        correr(saludJob);

        Map<String, Object> alerta = unicaAlerta();
        assertThat(alerta.get("severidad")).isEqualTo("BAJA");
        assertThat(alerta.get("posible_causa")).isNull();
        assertThat((String) alerta.get("accion_recomendada"))
                .contains("Causa sospechada: no determinada (riesgo bajo)")
                .doesNotContain("null");
    }

    @Test
    @DisplayName("H0018-04 - Serie comparativa - Se guarda la serie actual contra el perfil de referencia")
    void H0018_04_serieComparativa() throws Exception {
        ML.responder(MlSimulado.SALUD_PREDICT, desvio(2, "Caída de actividad", 0.8));

        correr(saludJob);

        JsonNode serie = json((String) unicaAlerta().get("serie_comparativa_json"));
        assertThat(serie.get("perfilCalculadoEn").isNull()).isFalse();
        assertThat(LocalDateTime.parse(serie.get("perfilCalculadoEn").asText())).isEqualTo(perfilCalculado);
        assertThat(LocalDateTime.parse(serie.get("ventanaDesde").asText()))
                .isBefore(LocalDateTime.parse(serie.get("ventanaHasta").asText()));
        assertThat(serie.get("descripcionDesvio").asText())
                .contains("Severidad media")
                .contains("Posible causa: Caída de actividad");

        // Cuatro bloques de 6 h, cada uno con la actividad real y la referencia del perfil.
        JsonNode actividad = serie.get("serieActividad");
        assertThat(actividad).hasSize(4);
        double[] referencia = {3.0, 3.5, 3.2, 2.8};
        for (int b = 0; b < 4; b++) {
            assertThat(actividad.get(b).get("actual").isNumber()).as("bloque %s", b).isTrue();
            assertThat(actividad.get(b).get("referenciaMedia").asDouble()).isEqualTo(referencia[b]);
        }
        assertThat(serie.get("serieDesplazamiento")).hasSize(4);
    }

    private Map<String, Object> unicaAlerta() {
        List<Map<String, Object>> alertas = alertas(animal, "SALUD");
        assertThat(alertas).hasSize(1);
        return alertas.get(0);
    }
}
