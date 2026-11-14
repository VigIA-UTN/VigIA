package dev.vigia.backend.controller;

import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base de los IT de la regresión E2 sobre lo que exponen los jobs de detección del worker: alertas de celo y
 * sanitarias, ventana de inseminación, historial reproductivo, estados e indicadores de salud, cohesión y
 * diagnósticos. Las alertas del escenario son las que arman los detectores (ver {@code fixtures/escenario}); los
 * casos que dependen de la hora crean sus datos relativos a {@link LocalDateTime#now()} de la JVM, no a la hora de la
 * carga, para no depender de la zona horaria de la máquina.
 */
abstract class DeteccionItBase extends GestionRodeoItBase {

    /** La alerta del escenario del animal con ese disparador (cada animal tiene a lo sumo una). */
    protected Long idAlerta(String establecimiento, String identificador, String disparador) {
        return jdbcTemplate.queryForObject("SELECT al.id FROM alertas al WHERE al.animal_id = ? AND al.disparador = ?",
                Long.class, idAnimal(establecimiento, identificador), disparador);
    }

    protected static final String ACCION_CELO = "Planificar inseminación artificial dentro de la ventana óptima.";

    /** Una alerta de celo pendiente generada en {@code ts}, con su ventana de 6 a 24 h, como la arma CeloDetector. */
    protected Long crearAlertaCelo(Long animalId, LocalDateTime ts) {
        return jdbcTemplate.queryForObject("INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, "
                        + "valor_umbral, valor_detectado, estado, ventana_inicio_ins, ventana_fin_ins, confianza_modelo, "
                        + "ventana_optima_horas, accion_recomendada) VALUES (?, 'Celo Detectado', 'ALTA', ?, 'CELO', 0.5, "
                        + "0.9, 'PENDIENTE', ?, ?, 0.9, 18, ?) RETURNING id",
                Long.class, animalId, Timestamp.valueOf(ts), Timestamp.valueOf(ts.plusHours(6)),
                Timestamp.valueOf(ts.plusHours(24)), ACCION_CELO);
    }

    protected void ultimaSenial(String codigoCaravana, LocalDateTime ultima) {
        jdbcTemplate.update("UPDATE caravanas SET ultima_senial = ? WHERE codigo = ?",
                ultima == null ? null : Timestamp.valueOf(ultima), codigoCaravana);
    }

    /** GET de un recurso de alertas, que siempre recibe el establecimiento seleccionado. */
    protected MvcResult getAlerta(String clave, String ruta, String establecimiento) throws Exception {
        return mockMvc.perform(get("/api/v1/alertas" + ruta)
                        .param("establecimientoId", idEstablecimiento(establecimiento).toString())
                        .header("Authorization", bearer(clave)))
                .andReturn();
    }

    protected MvcResult getAnimal(String clave, Long animalId, String ruta) throws Exception {
        return mockMvc.perform(get("/api/v1/animales/" + animalId + ruta)
                        .header("Authorization", bearer(clave)))
                .andReturn();
    }

    protected int estado(MvcResult resultado) {
        return resultado.getResponse().getStatus();
    }

    /** El resultado, después de comprobar que fue 200 (con el cuerpo en el mensaje si no). */
    protected MvcResult ok(MvcResult resultado) throws Exception {
        assertThat(estado(resultado)).as(texto(resultado)).isEqualTo(200);
        return resultado;
    }
}
