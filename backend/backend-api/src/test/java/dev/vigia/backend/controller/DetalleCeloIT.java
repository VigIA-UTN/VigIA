package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /api/v1/alertas/{id}: detalle de una alerta de celo de A-102 ("Celo A", Holando), que en el escenario tiene dos
 * estados reproductivos: PARIDA hace 30 días (cargado por veterinario.a) y el POSIBLE_CELO automático de hace 4 h.
 */
@DisplayName("H0009 - Detalle del evento de celo — IT")
class DetalleCeloIT extends DeteccionItBase {

    @Test
    @DisplayName("H0009-01 - Detalle de celo - Datos del animal, ventana, estados, dato origen y acción")
    void H0009_01_detalleCompleto() throws Exception {
        LocalDateTime ts = LocalDateTime.now().minusHours(2).withNano(0);
        Long alerta = crearAlertaCelo(idAnimal(EST_A, "A-102"), ts);

        JsonNode detalle = leer(ok(getAlerta("productorA", "/" + alerta, EST_A)));

        assertThat(detalle.get("animalCaravanaOficial").asText()).isEqualTo("A-102");
        assertThat(detalle.get("animalAlias").asText()).isEqualTo("Celo A");
        assertThat(detalle.get("animalRaza").asText()).isEqualTo("Holando");
        assertThat(LocalDateTime.parse(detalle.get("tsGeneracion").asText())).isEqualTo(ts);
        assertThat(LocalDateTime.parse(detalle.get("ventanaInicioIns").asText())).isEqualTo(ts.plusHours(6));
        assertThat(LocalDateTime.parse(detalle.get("ventanaFinIns").asText())).isEqualTo(ts.plusHours(24));
        assertThat(detalle.get("ventanaOptimaHoras").asText()).isEqualTo("18 horas");
        assertThat(detalle.get("datoDisparador").asText()).isEqualTo("CELO");
        assertThat(detalle.get("estadoActual").asText()).isEqualTo("PENDIENTE");
        assertThat(detalle.get("descripcionDatoOrigen").asText()).contains("signos de celo").contains("90%");
        assertThat(detalle.get("accionRecomendada").asText()).isEqualTo(ACCION_CELO);

        JsonNode estados = detalle.get("ultimosEstadosReproductivos");
        assertThat(estados).hasSize(2);
        assertThat(estados.get(0).get("estado").asText()).isEqualTo("POSIBLE_CELO");
        assertThat(estados.get(0).get("origen").asText()).isEqualTo("AUTOMATICO");
        assertThat(estados.get(1).get("estado").asText()).isEqualTo("PARIDA");
        assertThat(estados.get(1).get("registradoPor").asText()).isEqualTo("Veterinario A");
        assertThat(estados.get(1).get("fechaUltimoParto").asText())
                .isEqualTo(LocalDate.now().minusDays(60).toString());
    }

    @Test
    @DisplayName("H0009-02 - Detalle de celo - Acceso por rol del establecimiento")
    void H0009_02_accesoPorRol() throws Exception {
        Long alerta = idAlerta(EST_A, "A-102", "CELO");

        for (String clave : new String[] {"productorA", "veterinarioA", "camperoA"}) {
            assertThat(estado(getAlerta(clave, "/" + alerta, EST_A))).as(clave).isEqualTo(200);
        }
        // El administrador no opera alertas.
        assertThat(estado(getAlerta("admin", "/" + alerta, EST_A))).isEqualTo(403);
    }

    @Test
    @DisplayName("H0009-03 - Detalle de celo - Alerta de otro establecimiento")
    void H0009_03_otroEstablecimiento() throws Exception {
        Long alerta = idAlerta(EST_A, "A-102", "CELO");

        // Un establecimiento del que no es miembro, o el propio con una alerta que no es de ahí.
        assertThat(estado(getAlerta("productorB", "/" + alerta, EST_A))).isEqualTo(403);
        assertThat(estado(getAlerta("productorB", "/" + alerta, EST_B))).isEqualTo(403);
        // Quien está en los dos la ve solo con el establecimiento de la alerta seleccionado.
        assertThat(estado(getAlerta("productorAmbos", "/" + alerta, EST_A))).isEqualTo(200);
        assertThat(estado(getAlerta("productorAmbos", "/" + alerta, EST_B))).isEqualTo(403);
    }

    @Test
    @DisplayName("H0009-04 - Detalle de celo - Alerta inexistente")
    void H0009_04_alertaInexistente() throws Exception {
        Long inexistente = jdbcTemplate.queryForObject("SELECT coalesce(max(id), 0) + 1000 FROM alertas", Long.class);

        assertThat(estado(getAlerta("productorA", "/" + inexistente, EST_A))).isEqualTo(404);
    }

    @Test
    @DisplayName("H0009-05 - Detalle de celo - Hasta 5 estados reproductivos, del más nuevo al más viejo")
    void H0009_05_ultimosCincoEstados() throws Exception {
        Long animal = idAnimal(EST_A, "A-102");
        // Cinco estados entre el PARIDA y el POSIBLE_CELO del escenario, una hora uno del otro: el último del arreglo
        // queda 1 h antes del POSIBLE_CELO.
        LocalDateTime posibleCelo = jdbcTemplate.queryForObject("SELECT fecha_registro FROM estados_reproductivos "
                + "WHERE animal_id = ? AND estado = 'POSIBLE_CELO'", Timestamp.class, animal).toLocalDateTime();
        String[] estados = {"VACIA", "CELO", "INSEMINADA", "GESTANTE", "VACIA"};
        for (int i = 0; i < estados.length; i++) {
            jdbcTemplate.update("INSERT INTO estados_reproductivos (animal_id, estado, origen, fecha_registro) "
                    + "VALUES (?, ?, 'MANUAL', ?)", animal, estados[i],
                    Timestamp.valueOf(posibleCelo.minusHours(estados.length - i)));
        }

        JsonNode lista = leer(ok(getAlerta("veterinarioA", "/" + idAlerta(EST_A, "A-102", "CELO"), EST_A)))
                .get("ultimosEstadosReproductivos");

        // 7 registros en total: se muestran los 5 últimos, sin el PARIDA ni el primer VACIA.
        assertThat(lista).hasSize(5);
        assertThat(lista.get(0).get("estado").asText()).isEqualTo("POSIBLE_CELO");
        assertThat(lista.get(1).get("estado").asText()).isEqualTo("VACIA");
        assertThat(lista.get(2).get("estado").asText()).isEqualTo("GESTANTE");
        assertThat(lista.get(3).get("estado").asText()).isEqualTo("INSEMINADA");
        assertThat(lista.get(4).get("estado").asText()).isEqualTo("CELO");
        for (int i = 1; i < lista.size(); i++) {
            assertThat(LocalDateTime.parse(lista.get(i).get("fechaRegistro").asText()))
                    .isBefore(LocalDateTime.parse(lista.get(i - 1).get("fechaRegistro").asText()));
        }
    }

    @Test
    @DisplayName("H0009-06 - Detalle de celo - Alerta de anestro sin ventana de inseminación")
    void H0009_06_alertaDeAnestro() throws Exception {
        Long alerta = jdbcTemplate.queryForObject("INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, "
                + "disparador, valor_umbral, valor_detectado, estado, accion_recomendada) VALUES (?, 'Anestro Detectado', "
                + "'MEDIA', ?, 'CELO', 45, 60, 'PENDIENTE', 'Se recomienda revisión veterinaria.') RETURNING id",
                Long.class, idAnimal(EST_A, "A-101"), Timestamp.valueOf(LocalDateTime.now()));

        JsonNode detalle = leer(ok(getAlerta("productorA", "/" + alerta, EST_A)));

        assertThat(detalle.get("descripcionDatoOrigen").asText())
                .isEqualTo("El animal no registra celo hace 60 días (umbral: 45 días).");
        assertThat(detalle.get("ventanaOptimaHoras").isNull()).isTrue();
        assertThat(detalle.get("ventanaInicioIns").isNull()).isTrue();
        assertThat(detalle.get("datoPosiblementeDesactualizado").asBoolean()).isFalse();
    }
}
