package dev.vigia.backend.deteccion;

import dev.vigia.backend.service.AnestroDetectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Detector de anestro sobre el escenario: todas las vacas tienen estado reproductivo (gestantes o paridas con un
 * POSIBLE_CELO reciente), así que sin cambios el detector no alerta a nadie. Cada caso deja a A-101 VACIA: su último
 * signo de ciclo es el GESTANTE registrado hace 30 días, y con un umbral de 25 días califica como anestro.
 */
@DisplayName("H0030 - Detección automática de anestro/ausencia de ciclo — IT")
class AnestroJobIT extends DeteccionItBase {

    private static final String TIPO_ANESTRO = "Anestro Detectado";

    @Autowired
    private AnestroDetectorService anestroDetectorService;

    private Long animal;

    @BeforeEach
    void dejarA101SinCiclo() {
        jdbcTemplate.update("UPDATE establecimientos SET dias_deteccion_anestro = 25, severidad_anestro = 'ALTA' "
                + "WHERE nombre = ?", EST_A);
        animal = idAnimal(EST_A, "A-101");
        registrarEstado(animal, "VACIA", LocalDateTime.now().minusDays(1));
    }

    @Test
    @DisplayName("H0030-01 - Anestro - Detecta la ausencia de ciclo")
    void H0030_01_detectaAusenciaDeCiclo() {
        anestroDetectorService.detectarAnestro();

        List<Map<String, Object>> alertas = alertasAnestro(animal);
        assertThat(alertas).hasSize(1);
        Map<String, Object> alerta = alertas.get(0);
        assertThat(alerta.get("estado")).isEqualTo("PENDIENTE");
        assertThat(alerta.get("severidad")).isEqualTo("ALTA");
        assertThat(alerta.get("disparador")).isEqualTo("CELO");
        assertThat(((Number) alerta.get("valor_umbral")).intValue()).isEqualTo(25);
        assertThat(((Number) alerta.get("valor_detectado")).intValue()).isEqualTo(30);
        assertThat((String) alerta.get("accion_recomendada")).contains("30 días sin ciclo");
        assertThat((String) alerta.get("posible_causa")).isEqualTo("Anestro prolongado (>25 días)");

        // Las demás de A ciclan (POSIBLE_CELO reciente) o están gestantes, y B usa el umbral por defecto (45 días).
        assertThat(cantidadAlertasAnestro(EST_A)).isEqualTo(1);
        assertThat(cantidadAlertasAnestro(EST_B)).isZero();
    }

    @Test
    @DisplayName("H0030-02 - Anestro - No duplica la alerta en corridas sucesivas")
    void H0030_02_noDuplicaAlertas() {
        anestroDetectorService.detectarAnestro();
        anestroDetectorService.detectarAnestro();

        // Con el anestro pendiente, la segunda corrida no abre otro.
        List<Map<String, Object>> alertas = alertasAnestro(animal);
        assertThat(alertas).hasSize(1);

        // Descartado hace un momento: queda silenciado (21 días) aunque el animal siga sin ciclo.
        jdbcTemplate.update("UPDATE alertas SET estado = 'DESCARTADA', ts_confirmacion = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), alertas.get(0).get("id"));
        anestroDetectorService.detectarAnestro();
        assertThat(alertasAnestro(animal)).hasSize(1);
    }

    @Test
    @DisplayName("H0030-03 - Anestro - Cierre al registrar un ciclo y animales que no se evalúan")
    void H0030_03_cierreYExclusiones() {
        // Fuera de la evaluación: A-103 tiene menos de un año, y B está inactivo; los dos, VACIA y sin ciclo.
        Long joven = idAnimal(EST_A, "A-103");
        jdbcTemplate.update("UPDATE animales SET fecha_nacimiento = current_date - 200 WHERE id = ?", joven);
        registrarEstado(joven, "VACIA", LocalDateTime.now().minusDays(1));
        jdbcTemplate.update("UPDATE establecimientos SET dias_deteccion_anestro = 25, activo = 0 WHERE nombre = ?",
                EST_B);
        registrarEstado(idAnimal(EST_B, "B-101"), "VACIA", LocalDateTime.now().minusDays(1));

        anestroDetectorService.detectarAnestro();
        assertThat(alertasAnestro(animal)).hasSize(1);
        assertThat(alertasAnestro(joven)).isEmpty();
        assertThat(cantidadAlertasAnestro(EST_B)).isZero();

        // Registran un celo: el anestro pendiente se archiva y no se abre otro.
        registrarEstado(animal, "CELO", LocalDateTime.now());
        anestroDetectorService.detectarAnestro();

        List<Map<String, Object>> alertas = alertasAnestro(animal);
        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).get("estado")).isEqualTo("ARCHIVADA");
        assertThat(alertas.get(0).get("motivo_descarte")).isEqualTo("ciclo_registrado");
    }

    private void registrarEstado(Long animalId, String estado, LocalDateTime fecha) {
        jdbcTemplate.update("INSERT INTO estados_reproductivos (animal_id, estado, origen, fecha_registro) "
                + "VALUES (?, ?, 'MANUAL', ?)", animalId, estado, Timestamp.valueOf(fecha));
    }

    private List<Map<String, Object>> alertasAnestro(Long animalId) {
        return jdbcTemplate.queryForList("SELECT * FROM alertas WHERE animal_id = ? AND tipo = ? ORDER BY id",
                animalId, TIPO_ANESTRO);
    }

    private int cantidadAlertasAnestro(String establecimiento) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM alertas al JOIN animales a ON a.id = al.animal_id "
                + "JOIN establecimientos e ON e.id = a.establecimiento_id WHERE e.nombre = ? AND al.tipo = ?",
                Integer.class, establecimiento, TIPO_ANESTRO);
    }
}
