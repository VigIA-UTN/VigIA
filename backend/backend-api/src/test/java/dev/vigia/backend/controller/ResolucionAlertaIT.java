package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PUT /api/v1/alertas/estado/{id}: confirmar o descartar una alerta pendiente, con el bloqueo pesimista de la fila para
 * que dos usuarios no la resuelvan a la vez y el trigger de V16 que congela la resolución en la base.
 */
@DisplayName("H0010 - Confirmar/descartar alerta (concurrencia de 2 usuarios) — IT")
class ResolucionAlertaIT extends CicloAlertasItBase {

    @Test
    @DisplayName("H0010-01 - Confirmar alerta - Persiste estado, usuario y hora")
    void H0010_01_confirmarPersisteResolucion() throws Exception {
        Long alerta = idAlerta(EST_A, "A-102", "CELO");
        Long animal = idAnimal(EST_A, "A-102");
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);

        MvcResult resultado = resolver("veterinarioA", alerta, "CONFIRMADA", null);

        assertThat(estado(resultado)).as(texto(resultado)).isEqualTo(200);
        JsonNode respuesta = leer(resultado);
        assertThat(respuesta.get("id").asLong()).isEqualTo(alerta);
        assertThat(respuesta.get("estado").asText()).isEqualTo("CONFIRMADA");
        assertThat(respuesta.get("tsConfirmacion").isNull()).isFalse();

        Map<String, Object> fila = jdbcTemplate.queryForMap("SELECT estado, usuario_confirmacion_id, ts_confirmacion "
                + "FROM alertas WHERE id = ?", alerta);
        assertThat(fila.get("estado")).isEqualTo("CONFIRMADA");
        assertThat(((Number) fila.get("usuario_confirmacion_id")).longValue())
                .isEqualTo(idUsuario(email("veterinarioA")));
        assertThat(((Timestamp) fila.get("ts_confirmacion")).toLocalDateTime())
                .isBetween(antes, LocalDateTime.now().plusSeconds(1));

        // Confirmar un celo deja el estado CELO del animal, cargado por quien confirmó.
        Map<String, Object> celo = jdbcTemplate.queryForMap("SELECT usuario_registro_id, origen FROM "
                + "estados_reproductivos WHERE animal_id = ? AND estado = 'CELO'", animal);
        assertThat(((Number) celo.get("usuario_registro_id")).longValue())
                .isEqualTo(idUsuario(email("veterinarioA")));
        assertThat(celo.get("origen")).isEqualTo("MANUAL");

        // El detalle muestra quién la resolvió.
        JsonNode detalle = detalle("productorA", EST_A, alerta);
        assertThat(detalle.get("estadoActual").asText()).isEqualTo("CONFIRMADA");
        assertThat(detalle.get("usuarioAccion").asText()).isEqualTo("Veterinario A");
    }

    @Test
    @DisplayName("H0010-02 - Descartar alerta - Motivo opcional y sin efecto en el estado reproductivo")
    void H0010_02_descartarConYSinMotivo() throws Exception {
        // a) Con motivo: se guarda sin los espacios de los bordes.
        Long celo = idAlerta(EST_A, "A-106", "CELO");
        MvcResult descartada = resolver("camperoA", celo, "DESCARTADA", "  Falsa alarma  ");

        assertThat(estado(descartada)).as(texto(descartada)).isEqualTo(200);
        assertThat(leer(descartada).get("estado").asText()).isEqualTo("DESCARTADA");
        Map<String, Object> fila = jdbcTemplate.queryForMap("SELECT estado, motivo_descarte, usuario_confirmacion_id, "
                + "ts_confirmacion FROM alertas WHERE id = ?", celo);
        assertThat(fila.get("estado")).isEqualTo("DESCARTADA");
        assertThat(fila.get("motivo_descarte")).isEqualTo("Falsa alarma");
        assertThat(((Number) fila.get("usuario_confirmacion_id")).longValue())
                .isEqualTo(idUsuario(email("camperoA")));
        assertThat(fila.get("ts_confirmacion")).isNotNull();
        // Descartar no es confirmar un celo: no se registra ningún estado CELO.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM estados_reproductivos WHERE animal_id = ? "
                + "AND estado = 'CELO'", Integer.class, idAnimal(EST_A, "A-106"))).isZero();

        // b) Sin motivo: queda null.
        Long salud = idAlerta(EST_A, "A-103", "SALUD");
        assertThat(estado(resolver("veterinarioA", salud, "DESCARTADA", null))).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("SELECT motivo_descarte FROM alertas WHERE id = ?", String.class,
                salud)).isNull();
        assertThat(estadoEnBase(salud)).isEqualTo("DESCARTADA");
    }

    @Test
    @DisplayName("H0010-03 - Resolución concurrente - Dos usuarios a la vez, solo uno gana y el otro recibe 409")
    void H0010_03_dosUsuariosEnSimultaneo() throws Exception {
        Long alerta = idAlerta(EST_A, "A-102", "CELO");
        Long animal = idAnimal(EST_A, "A-102");
        // Las dos sesiones se inician antes de largar: el login no tiene que quedar dentro de la carrera.
        String sesionProductor = bearer("productorA");
        String sesionVeterinario = bearer("veterinarioA");

        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> confirma = () -> {
                listos.countDown();
                largada.await();
                return mockMvc.perform(pedidoResolver(sesionProductor, alerta, "CONFIRMADA", null)).andReturn();
            };
            Callable<MvcResult> descarta = () -> {
                listos.countDown();
                largada.await();
                return mockMvc.perform(pedidoResolver(sesionVeterinario, alerta, "DESCARTADA", "Otro criterio"))
                        .andReturn();
            };
            Future<MvcResult> delProductor = pool.submit(confirma);
            Future<MvcResult> delVeterinario = pool.submit(descarta);
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
            largada.countDown();

            MvcResult productor = delProductor.get(30, TimeUnit.SECONDS);
            MvcResult veterinario = delVeterinario.get(30, TimeUnit.SECONDS);

            List<Integer> estados = new ArrayList<>(List.of(estado(productor), estado(veterinario)));
            assertThat(estados).as("productor: %s | veterinario: %s", texto(productor), texto(veterinario))
                    .containsExactlyInAnyOrder(200, 409);

            boolean ganaElProductor = estado(productor) == 200;
            MvcResult perdedor = ganaElProductor ? veterinario : productor;
            String ganador = ganaElProductor ? "Productor A" : "Veterinario A";
            assertThat(texto(perdedor)).contains("Esta alerta ya fue").contains("por " + ganador);

            // La base refleja una sola resolución, la del ganador, sin mezclar campos de los dos.
            Map<String, Object> fila = jdbcTemplate.queryForMap("SELECT estado, usuario_confirmacion_id, "
                    + "motivo_descarte FROM alertas WHERE id = ?", alerta);
            assertThat(fila.get("estado")).isEqualTo(ganaElProductor ? "CONFIRMADA" : "DESCARTADA");
            assertThat(((Number) fila.get("usuario_confirmacion_id")).longValue())
                    .isEqualTo(idUsuario(email(ganaElProductor ? "productorA" : "veterinarioA")));
            assertThat(fila.get("motivo_descarte")).isEqualTo(ganaElProductor ? null : "Otro criterio");
            // Y los efectos secundarios se aplicaron una sola vez: el CELO solo existe si ganó la confirmación.
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM estados_reproductivos WHERE animal_id = ? "
                    + "AND estado = 'CELO'", Integer.class, animal)).isEqualTo(ganaElProductor ? 1 : 0);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("H0010-04 - Resolución inmutable - El trigger impide cambiar estado, usuario y hora")
    void H0010_04_triggerImpideCambiarLaResolucion() throws Exception {
        Long alerta = confirmada("veterinarioA", idAlerta(EST_A, "A-102", "CELO"));
        Map<String, Object> original = jdbcTemplate.queryForMap("SELECT estado, usuario_confirmacion_id, "
                + "ts_confirmacion FROM alertas WHERE id = ?", alerta);
        Long otroUsuario = idUsuario(email("productorA"));

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE alertas SET estado = 'DESCARTADA' WHERE id = ?", alerta))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Acción prohibida");
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE alertas SET estado = 'PENDIENTE' WHERE id = ?", alerta))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Acción prohibida");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE alertas SET usuario_confirmacion_id = ? WHERE id = ?", otroUsuario, alerta))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Acción prohibida");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE alertas SET ts_confirmacion = now() + interval '1 day' WHERE id = ?", alerta))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Acción prohibida");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE alertas SET usuario_confirmacion_id = NULL, ts_confirmacion = NULL WHERE id = ?", alerta))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Acción prohibida");

        assertThat(jdbcTemplate.queryForMap("SELECT estado, usuario_confirmacion_id, ts_confirmacion FROM alertas "
                + "WHERE id = ?", alerta)).isEqualTo(original);

        // El trigger solo congela la resolución: lo demás de la alerta sigue editable.
        assertThat(jdbcTemplate.update("UPDATE alertas SET accion_recomendada = 'Nueva acción' WHERE id = ?", alerta))
                .isEqualTo(1);

        // También vale para el descarte: el motivo no se puede reescribir.
        Long descartada = idAlerta(EST_A, "A-103", "SALUD");
        ok(resolver("veterinarioA", descartada, "DESCARTADA", "Error de lectura"));
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE alertas SET motivo_descarte = 'Otro motivo' WHERE id = ?", descartada))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Acción prohibida");
    }

    @Test
    @DisplayName("H0010-05 - Resolver alerta - Permisos y alertas que no se pueden resolver")
    void H0010_05_permisosYAlertaInexistente() throws Exception {
        Long celo = idAlerta(EST_A, "A-102", "CELO");
        Long salud = idAlerta(EST_A, "A-103", "SALUD");
        Long inexistente = jdbcTemplate.queryForObject("SELECT coalesce(max(id), 0) + 1000 FROM alertas", Long.class);

        // El campero no actúa sobre alertas sanitarias; sí sobre las demás.
        MvcResult campero = resolver("camperoA", salud, "CONFIRMADA", null);
        assertThat(estado(campero)).as(texto(campero)).isEqualTo(403);
        assertThat(texto(campero)).contains("campero no puede actuar");
        // El administrador no opera alertas, y otro establecimiento no ve las de A.
        assertThat(estado(resolver("admin", celo, "CONFIRMADA", null))).isEqualTo(403);
        assertThat(estado(resolver("productorB", celo, "CONFIRMADA", null))).isEqualTo(403);
        assertThat(estado(resolver("veterinarioB", salud, "DESCARTADA", null))).isEqualTo(403);
        // Una alerta que no existe es un error de la solicitud.
        assertThat(estado(resolver("productorA", inexistente, "CONFIRMADA", null))).isEqualTo(400);

        // Ninguno de los rechazos tocó las alertas.
        assertThat(estadoEnBase(celo)).isEqualTo("PENDIENTE");
        assertThat(estadoEnBase(salud)).isEqualTo("PENDIENTE");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM alertas WHERE id IN (?, ?) "
                + "AND (usuario_confirmacion_id IS NOT NULL OR ts_confirmacion IS NOT NULL)", Integer.class, celo,
                salud)).isZero();

        // El campero sí resuelve las que no son sanitarias.
        assertThat(estado(resolver("camperoA", celo, "CONFIRMADA", null))).isEqualTo(200);
    }

    @Test
    @DisplayName("H0010-06 - Resolver alerta - Estados inválidos y alerta ya resuelta")
    void H0010_06_estadosInvalidosYYaResuelta() throws Exception {
        Long perimetro = idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO");

        // Desde PENDIENTE solo se confirma o se descarta; el resto de los estados no se asigna a mano.
        for (String invalido : new String[] {"PENDIENTE", "ARCHIVADA", "VENCIDA"}) {
            MvcResult resultado = resolver("productorA", perimetro, invalido, null);
            assertThat(estado(resultado)).as("%s: %s", invalido, texto(resultado)).isEqualTo(400);
        }
        assertThat(estado(resolver("productorA", perimetro, "RESUELTA", null))).isEqualTo(400);
        assertThat(estadoEnBase(perimetro)).isEqualTo("PENDIENTE");

        // Ya confirmada: otra resolución, aun de quien la confirmó, es 409 con quién y cuándo, y no cambia nada.
        ok(resolver("productorA", perimetro, "CONFIRMADA", null));
        for (String intento : new String[] {"DESCARTADA", "CONFIRMADA"}) {
            MvcResult repetido = resolver("productorA", perimetro, intento, null);
            assertThat(estado(repetido)).as(intento).isEqualTo(409);
            assertThat(texto(repetido)).contains("Esta alerta ya fue confirmada por Productor A el ");
        }
        MvcResult otroUsuario = resolver("veterinarioA", perimetro, "DESCARTADA", "Distinto criterio");
        assertThat(estado(otroUsuario)).isEqualTo(409);
        assertThat(estadoEnBase(perimetro)).isEqualTo("CONFIRMADA");
        assertThat(jdbcTemplate.queryForObject("SELECT motivo_descarte FROM alertas WHERE id = ?", String.class,
                perimetro)).isNull();

        // Vencida (la deja así el worker): no admite cambios.
        Long vencida = insertarAlerta(EST_A, "A-101", "Fuera de Perímetro", "FUERA_DE_PERIMETRO", "ALTA", "VENCIDA",
                ahora().minusDays(2));
        MvcResult resultado = resolver("productorA", vencida, "CONFIRMADA", null);
        assertThat(estado(resultado)).isEqualTo(409);
        assertThat(texto(resultado)).contains("ya venci");
    }
}
