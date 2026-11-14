package dev.vigia.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * POST /api/v1/alertas/intervenir/{id}: la intervención queda asociada a una alerta ya confirmada (una sola por
 * alerta), con el animal, el interventor y el tipo. La tabla es inmutable (V12).
 */
@DisplayName("H0021 - Registrar intervención asociada a alerta — IT")
class IntervencionAlertaIT extends CicloAlertasItBase {

    @Test
    @DisplayName("H0021-01 - Intervención - Se asocia a la alerta confirmada con animal, interventor y tipo")
    void H0021_01_registroAsociadoALaAlerta() throws Exception {
        Long salud = confirmada("veterinarioA", idAlerta(EST_A, "A-103", "SALUD"));
        LocalDateTime fecha = ahora().minusMinutes(20);

        MvcResult resultado = intervenir("veterinarioA", salud, "TRATAMIENTO", "  Antibiótico por 5 días  ", fecha, null);

        assertThat(estado(resultado)).as(texto(resultado)).isEqualTo(200);
        assertThat(leer(resultado).get("id").asLong()).isEqualTo(salud);
        Map<String, Object> fila = jdbcTemplate.queryForMap("SELECT * FROM intervenciones WHERE alerta_id = ?", salud);
        assertThat(((Number) fila.get("animal_id")).longValue()).isEqualTo(idAnimal(EST_A, "A-103"));
        assertThat(((Number) fila.get("interventor_id")).longValue()).isEqualTo(idUsuario(email("veterinarioA")));
        assertThat(fila.get("tipo")).isEqualTo("TRATAMIENTO");
        assertThat(fila.get("descripcion")).isEqualTo("Antibiótico por 5 días");
        assertThat(((Timestamp) fila.get("fecha_hora")).toLocalDateTime()).isEqualTo(fecha);
        // Registrar la intervención no cambia la resolución de la alerta.
        assertThat(estadoEnBase(salud)).isEqualTo("CONFIRMADA");

        // Sin fecha, la intervención queda en el momento del registro; el campero la registra sobre un celo.
        Long celo = confirmada("camperoA", idAlerta(EST_A, "A-102", "CELO"));
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);
        assertThat(estado(intervenir("camperoA", celo, "OTRO", "Se separó del lote", null, null))).isEqualTo(200);
        Map<String, Object> sinFecha = jdbcTemplate.queryForMap("SELECT interventor_id, fecha_hora FROM "
                + "intervenciones WHERE alerta_id = ?", celo);
        assertThat(((Number) sinFecha.get("interventor_id")).longValue()).isEqualTo(idUsuario(email("camperoA")));
        assertThat(((Timestamp) sinFecha.get("fecha_hora")).toLocalDateTime())
                .isBetween(antes, LocalDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("H0021-02 - Intervención - Validaciones: alerta sin confirmar, datos faltantes y duplicada")
    void H0021_02_validaciones() throws Exception {
        Long celo = idAlerta(EST_A, "A-102", "CELO");

        // a) La alerta tiene que estar confirmada: pendiente y descartada no admiten intervención.
        MvcResult pendiente = intervenir("productorA", celo, "OTRO", "Revisión", null, null);
        assertThat(estado(pendiente)).as(texto(pendiente)).isEqualTo(400);
        assertThat(texto(pendiente)).contains("debe estar confirmada");
        Long descartada = idAlerta(EST_A, "A-106", "CELO");
        ok(resolver("productorA", descartada, "DESCARTADA", null));
        assertThat(estado(intervenir("productorA", descartada, "OTRO", "Revisión", null, null))).isEqualTo(400);

        // b) Descripción y tipo son obligatorios.
        confirmada("productorA", celo);
        for (String vacia : new String[] {null, "", "   "}) {
            MvcResult sinDescripcion = intervenir("productorA", celo, "OTRO", vacia, null, null);
            assertThat(estado(sinDescripcion)).as("descripción [%s]", vacia).isEqualTo(400);
            assertThat(texto(sinDescripcion)).contains("descripci").contains("obligatoria");
        }
        MvcResult sinTipo = intervenir("productorA", celo, null, "Revisión", null, null);
        assertThat(estado(sinTipo)).isEqualTo(400);
        assertThat(texto(sinTipo)).contains("tipo de intervenci").contains("obligatorio");
        assertThat(cantidadIntervenciones(celo)).isZero();

        // c) Una sola intervención por alerta.
        assertThat(estado(intervenir("productorA", celo, "OTRO", "Primera", null, null))).isEqualTo(200);
        MvcResult segunda = intervenir("veterinarioA", celo, "TRATAMIENTO", "Segunda", null, null);
        assertThat(estado(segunda)).isEqualTo(400);
        assertThat(texto(segunda)).contains("Ya existe una intervenci");
        assertThat(cantidadIntervenciones(celo)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT descripcion FROM intervenciones WHERE alerta_id = ?",
                String.class, celo)).isEqualTo("Primera");
    }

    @Test
    @DisplayName("H0021-03 - Intervención - Permisos por rol y establecimiento")
    void H0021_03_permisos() throws Exception {
        Long salud = confirmada("veterinarioA", idAlerta(EST_A, "A-103", "SALUD"));
        Long celo = confirmada("veterinarioA", idAlerta(EST_A, "A-102", "CELO"));
        Long inexistente = jdbcTemplate.queryForObject("SELECT coalesce(max(id), 0) + 1000 FROM alertas", Long.class);

        // El campero no actúa sobre alertas sanitarias; el administrador y otro establecimiento, sobre ninguna.
        MvcResult campero = intervenir("camperoA", salud, "TRATAMIENTO", "Tratamiento", null, null);
        assertThat(estado(campero)).as(texto(campero)).isEqualTo(403);
        assertThat(texto(campero)).contains("campero no puede actuar");
        assertThat(estado(intervenir("admin", celo, "OTRO", "Revisión", null, null))).isEqualTo(403);
        assertThat(estado(intervenir("productorB", celo, "OTRO", "Revisión", null, null))).isEqualTo(403);
        assertThat(estado(intervenir("veterinarioB", salud, "TRATAMIENTO", "Tratamiento", null, null))).isEqualTo(403);
        assertThat(estado(intervenir("productorA", inexistente, "OTRO", "Revisión", null, null))).isEqualTo(404);
        assertThat(cantidadIntervenciones(salud) + cantidadIntervenciones(celo)).isZero();

        // Los tres roles del establecimiento sí, cada uno donde corresponde.
        assertThat(estado(intervenir("productorA", celo, "OTRO", "Revisión", null, null))).isEqualTo(200);
        assertThat(estado(intervenir("veterinarioA", salud, "TRATAMIENTO", "Tratamiento", null, null))).isEqualTo(200);
        Long perimetro = confirmada("camperoA", idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO"));
        assertThat(estado(intervenir("camperoA", perimetro, "OTRO", "Se lo llevó de vuelta", null, null)))
                .isEqualTo(200);
    }

    @Test
    @DisplayName("H0021-04 - Intervención - Efectos según el tipo y registro inmutable")
    void H0021_04_efectosPorTipoEInmutabilidad() throws Exception {
        // INSEMINACION: deja al animal INSEMINADA con la fecha de la intervención.
        Long celo = confirmada("veterinarioA", idAlerta(EST_A, "A-102", "CELO"));
        LocalDateTime fecha = ahora().minusHours(1);
        assertThat(estado(intervenir("veterinarioA", celo, "INSEMINACION", "IA con semen Angus", fecha, null)))
                .isEqualTo(200);
        Map<String, Object> inseminada = jdbcTemplate.queryForMap("SELECT estado, fecha_ultima_inseminacion, origen, "
                + "usuario_registro_id FROM estados_reproductivos WHERE animal_id = ? ORDER BY id DESC LIMIT 1",
                idAnimal(EST_A, "A-102"));
        assertThat(inseminada.get("estado")).isEqualTo("INSEMINADA");
        assertThat(inseminada.get("fecha_ultima_inseminacion")).hasToString(fecha.toLocalDate().toString());
        assertThat(inseminada.get("origen")).isEqualTo("MANUAL");
        assertThat(((Number) inseminada.get("usuario_registro_id")).longValue())
                .isEqualTo(idUsuario(email("veterinarioA")));

        // REVISION_VETERINARIA con el estado de salud resultante: queda registrado con las notas de la intervención.
        Long salud = confirmada("veterinarioA", idAlerta(EST_A, "A-103", "SALUD"));
        assertThat(estado(intervenir("veterinarioA", salud, "REVISION_VETERINARIA", "Cuadro respiratorio", null,
                "EN_OBSERVACION"))).isEqualTo(200);
        Map<String, Object> revision = jdbcTemplate.queryForMap("SELECT estado, diagnostico_notas, fecha_revision, "
                + "origen FROM estados_salud WHERE animal_id = ? ORDER BY estado_salud_id DESC LIMIT 1",
                idAnimal(EST_A, "A-103"));
        assertThat(revision.get("estado")).isEqualTo("EN_OBSERVACION");
        assertThat(revision.get("diagnostico_notas")).isEqualTo("Cuadro respiratorio");
        assertThat(revision.get("fecha_revision")).hasToString(LocalDate.now().toString());
        assertThat(revision.get("origen")).isEqualTo("MANUAL");

        // Otro tipo no toca los estados del animal.
        Long perimetro = confirmada("productorA", idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO"));
        int estadosAntes = jdbcTemplate.queryForObject("SELECT count(*) FROM estados_reproductivos "
                + "WHERE animal_id = ?", Integer.class, idAnimal(EST_A, "A-105"));
        assertThat(estado(intervenir("productorA", perimetro, "VACUNACION", "Antiaftosa", null, null))).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM estados_reproductivos WHERE animal_id = ?",
                Integer.class, idAnimal(EST_A, "A-105"))).isEqualTo(estadosAntes);

        // La intervención registrada no se edita ni se borra en la base.
        long id = jdbcTemplate.queryForObject("SELECT id FROM intervenciones WHERE alerta_id = ?", Long.class, celo);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE intervenciones SET descripcion = 'Alterada' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Acción prohibida");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM intervenciones WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Acción prohibida");
        assertThat(jdbcTemplate.queryForObject("SELECT descripcion FROM intervenciones WHERE id = ?", String.class,
                id)).isEqualTo("IA con semen Angus");
    }

    private int cantidadIntervenciones(Long alertaId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM intervenciones WHERE alerta_id = ?", Integer.class,
                alertaId);
    }

}
