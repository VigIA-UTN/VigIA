package dev.vigia.backend.deteccion;

import dev.vigia.backend.service.AlertaArchivadorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Archivador de alertas de celo con la ventana de inseminación vencida (corre cada hora en el worker). */
@DisplayName("H0016 - Countdown ventana óptima de inseminación: archivado al vencer — IT")
class ArchivadoVentanaIT extends DeteccionItBase {

    @Autowired
    private AlertaArchivadorService archivador;

    @Test
    @DisplayName("H0016-03 - Ventana de inseminación - Al vencer, la alerta se archiva como oportunidad no aprovechada")
    void H0016_03_archivadoAlVencer() {
        LocalDateTime ahora = LocalDateTime.now();
        Long vencida = crearAlertaCelo(idAnimal(EST_A, "A-101"), ahora.minusHours(25), "PENDIENTE");
        Long activa = crearAlertaCelo(idAnimal(EST_A, "A-103"), ahora.minusHours(12), "PENDIENTE");
        // Una ya resuelta no cambia aunque su ventana haya pasado.
        Long confirmada = crearAlertaCelo(idAnimal(EST_A, "A-104"), ahora.minusHours(30), "CONFIRMADA");

        archivador.archivarAlertasVencidas();

        Map<String, Object> archivada = alerta(vencida);
        assertThat(archivada.get("estado")).isEqualTo("ARCHIVADA");
        assertThat(archivada.get("motivo_descarte")).isEqualTo("oportunidad_no_aprovechada");
        assertThat(((Timestamp) archivada.get("ts_confirmacion")).toLocalDateTime())
                .isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES));
        assertThat(alerta(activa).get("estado")).isEqualTo("PENDIENTE");
        assertThat(alerta(confirmada).get("estado")).isEqualTo("CONFIRMADA");
        // Las alertas de celo del escenario (A-102 y A-106) están dentro de su ventana.
        assertThat(alerta(jdbcTemplate.queryForObject("SELECT id FROM alertas WHERE animal_id = ? AND disparador = "
                + "'CELO'", Long.class, idAnimal(EST_A, "A-102"))).get("estado")).isEqualTo("PENDIENTE");

        // Una segunda corrida no vuelve a tocar la archivada.
        archivador.archivarAlertasVencidas();
        assertThat(alerta(vencida).get("ts_confirmacion")).isEqualTo(archivada.get("ts_confirmacion"));
    }

    private Long crearAlertaCelo(Long animalId, LocalDateTime ts, String estado) {
        return jdbcTemplate.queryForObject("INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, "
                        + "valor_umbral, valor_detectado, estado, ventana_inicio_ins, ventana_fin_ins, confianza_modelo, "
                        + "ts_confirmacion) VALUES (?, 'Celo Detectado', 'ALTA', ?, 'CELO', 0.5, 0.9, ?, ?, ?, 0.9, ?) "
                        + "RETURNING id",
                Long.class, animalId, Timestamp.valueOf(ts), estado, Timestamp.valueOf(ts.plusHours(6)),
                Timestamp.valueOf(ts.plusHours(24)),
                "PENDIENTE".equals(estado) ? null : Timestamp.valueOf(ts.plusHours(8)));
    }

    private Map<String, Object> alerta(Long id) {
        return jdbcTemplate.queryForMap("SELECT * FROM alertas WHERE id = ?", id);
    }
}
