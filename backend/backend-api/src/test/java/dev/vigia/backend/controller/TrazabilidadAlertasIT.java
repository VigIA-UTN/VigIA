package dev.vigia.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RNF07 (trazabilidad): toda alerta generada guarda el dato que la disparó, cuándo se generó, el umbral que superó y
 * su estado. Se verifica sobre la base, que es donde se audita, después de recorrer el ciclo de vida por la API.
 */
@DisplayName("RNF07 - Trazabilidad de las alertas generadas — IT")
class TrazabilidadAlertasIT extends CicloAlertasItBase {

    private static final String ALERTAS_DE_LOS_FIXTURES = "FROM alertas al JOIN animales a ON a.id = al.animal_id "
            + "JOIN establecimientos e ON e.id = a.establecimiento_id WHERE e.nombre LIKE 'Fixture %'";

    @Test
    @DisplayName("RNF07-01 - Trazabilidad - Todas las alertas del escenario y de las pruebas tienen dato disparador, hora, umbral y estado")
    void RNF07_01_todasLasAlertasTienenSuDatoDeOrigen() throws Exception {
        // Alertas de cada tipo y estado, como las dejan el worker y las pruebas de este ciclo.
        LocalDateTime ahora = ahora();
        insertarAlerta(EST_A, "A-101", "Celo Detectado", "CELO", "MEDIA", "CONFIRMADA", ahora.minusHours(5));
        insertarAlerta(EST_A, "A-101", "Abigeato", "ABIGEATO", "BAJA", "VENCIDA", ahora.minusHours(6));
        insertarAlerta(EST_B, "B-101", "SALUD", "SALUD", "BAJA", "ARCHIVADA", ahora.minusHours(7));
        ok(resolver("veterinarioA", idAlerta(EST_A, "A-102", "CELO"), "CONFIRMADA", null));
        ok(resolver("productorA", idAlerta(EST_A, "A-103", "SALUD"), "DESCARTADA", "Falsa alarma"));

        int total = jdbcTemplate.queryForObject("SELECT count(*) " + ALERTAS_DE_LOS_FIXTURES, Integer.class);
        assertThat(total).isEqualTo(5 * 2 + 3);

        // Ninguna sin disparador, hora, umbral ni estado, ni con un estado fuera del ciclo de vida.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) " + ALERTAS_DE_LOS_FIXTURES
                + " AND (al.disparador IS NULL OR al.ts_generacion IS NULL OR al.valor_umbral IS NULL "
                + "OR al.estado IS NULL OR al.valor_detectado IS NULL)", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT al.estado " + ALERTAS_DE_LOS_FIXTURES, String.class))
                .isSubsetOf("PENDIENTE", "CONFIRMADA", "DESCARTADA", "VENCIDA", "ARCHIVADA");
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT al.disparador " + ALERTAS_DE_LOS_FIXTURES, String.class))
                .isSubsetOf("CELO", "SALUD", "ABIGEATO", "FUERA_DE_PERIMETRO", "AISLAMIENTO_SOCIAL");
        // Sin horas de generación futuras y, las que se resolvieron en la prueba, con quién y cuándo.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) " + ALERTAS_DE_LOS_FIXTURES
                + " AND al.ts_generacion > now() + interval '1 minute'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM alertas WHERE id IN (?, ?) AND (estado = 'PENDIENTE' "
                + "OR usuario_confirmacion_id IS NULL OR ts_confirmacion IS NULL)", Integer.class,
                idAlerta(EST_A, "A-102", "CELO"), idAlerta(EST_A, "A-103", "SALUD"))).isZero();
    }

    @Test
    @DisplayName("RNF07-02 - Trazabilidad - La base rechaza una alerta sin disparador, hora, umbral o estado")
    void RNF07_02_laBaseRechazaAlertasIncompletas() {
        Long animal = idAnimal(EST_A, "A-101");
        String completa = "INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, valor_umbral, "
                + "valor_detectado, estado) VALUES (?, 'SALUD', 'BAJA', %s, %s, %s, 1.0, %s)";

        // Control: con todo cargado, entra.
        assertThat(jdbcTemplate.update(completa.formatted("now()", "'SALUD'", "0.5", "'PENDIENTE'"), animal))
                .isEqualTo(1);

        List<String> incompletas = List.of(
                completa.formatted("now()", "NULL", "0.5", "'PENDIENTE'"),
                completa.formatted("NULL", "'SALUD'", "0.5", "'PENDIENTE'"),
                completa.formatted("now()", "'SALUD'", "NULL", "'PENDIENTE'"),
                completa.formatted("now()", "'SALUD'", "0.5", "NULL"));
        for (String sql : incompletas) {
            assertThatThrownBy(() -> jdbcTemplate.update(sql, animal)).as(sql)
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("null value in column");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM alertas WHERE animal_id = ?", Integer.class,
                animal)).isEqualTo(1);
    }

    @Test
    @DisplayName("RNF07-03 - Trazabilidad - El dato de origen no cambia al resolver y el trigger protege la resolución")
    void RNF07_03_elDatoDeOrigenSobreviveALaResolucion() throws Exception {
        Long alerta = idAlerta(EST_A, "A-103", "SALUD");
        Map<String, Object> origen = datoDeOrigen(alerta);

        ok(resolver("veterinarioA", alerta, "CONFIRMADA", null));

        // El dato de origen sigue igual y ahora suma quién y cuándo resolvió.
        assertThat(datoDeOrigen(alerta)).isEqualTo(origen);
        assertThat(jdbcTemplate.queryForObject("SELECT usuario_confirmacion_id IS NOT NULL AND ts_confirmacion IS NOT "
                + "NULL FROM alertas WHERE id = ?", Boolean.class, alerta)).isTrue();
        // La confianza del modelo que respalda el diagnóstico tampoco se reescribe una vez resuelta.
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE alertas SET confianza_modelo = 0.1 WHERE id = ?", alerta))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Acción prohibida");
        assertThat(datoDeOrigen(alerta)).isEqualTo(origen);
    }

    /**
     * Disparador, hora, umbral, valor y confianza. La confianza es un float en la entidad: el escenario carga 0.72 como
     * double y, al guardar la alerta, la columna queda en su valor float; se compara en esa precisión.
     */
    private Map<String, Object> datoDeOrigen(Long alerta) {
        Map<String, Object> fila = new java.util.HashMap<>(jdbcTemplate.queryForMap("SELECT disparador, ts_generacion, "
                + "valor_umbral, valor_detectado, confianza_modelo FROM alertas WHERE id = ?", alerta));
        fila.put("confianza_modelo", ((Number) fila.get("confianza_modelo")).floatValue());
        return fila;
    }
}
