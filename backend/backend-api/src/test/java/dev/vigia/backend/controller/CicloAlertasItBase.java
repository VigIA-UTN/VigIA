package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base de los IT de la regresión E4 (ciclo de vida de las alertas): resolver, filtrar, intervenir, trazabilidad,
 * línea de tiempo y panel. Parte del escenario de {@code fixtures/escenario}, que en cada establecimiento trae cinco
 * alertas PENDIENTE: celo ALTA (A-102), celo ALTA con dato desactualizado (A-106), sanitaria MEDIA (A-103), fuera de
 * perímetro ALTA (A-105) y aislamiento ALTA (A-104).
 * <p>
 * Las alertas las genera el worker, no la API, así que los casos que necesitan otras combinaciones de tipo, severidad o
 * estado las insertan por JDBC con los mismos campos obligatorios que arman los detectores.
 */
abstract class CicloAlertasItBase extends DeteccionItBase {

    protected static LocalDateTime ahora() {
        return LocalDateTime.now().withNano(0);
    }

    // ── Alertas armadas por JDBC ─────────────────────────────────────────────

    /** Una alerta con valores de disparador conocidos (detectado 0.8 sobre umbral 0.5) y la acción recomendada dada. */
    protected Long insertarAlerta(String establecimiento, String identificador, String tipo, String disparador,
            String severidad, String estado, LocalDateTime tsGeneracion, String accionRecomendada) {
        return jdbcTemplate.queryForObject("INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, "
                        + "valor_umbral, valor_detectado, estado, accion_recomendada) "
                        + "VALUES (?, ?, ?, ?, ?, 0.5, 0.8, ?, ?) RETURNING id",
                Long.class, idAnimal(establecimiento, identificador), tipo, severidad,
                Timestamp.valueOf(tsGeneracion), disparador, estado, accionRecomendada);
    }

    protected Long insertarAlerta(String establecimiento, String identificador, String tipo, String disparador,
            String severidad, String estado, LocalDateTime tsGeneracion) {
        return insertarAlerta(establecimiento, identificador, tipo, disparador, severidad, estado, tsGeneracion, null);
    }

    protected String estadoEnBase(Long alertaId) {
        return jdbcTemplate.queryForObject("SELECT estado FROM alertas WHERE id = ?", String.class, alertaId);
    }

    // ── Resolver e intervenir ────────────────────────────────────────────────

    /** PUT /api/v1/alertas/estado/{id}: confirmar o descartar. */
    protected MvcResult resolver(String clave, Long alertaId, String nuevoEstado, String notas) throws Exception {
        return mockMvc.perform(pedidoResolver(bearer(clave), alertaId, nuevoEstado, notas)).andReturn();
    }

    protected MockHttpServletRequestBuilder pedidoResolver(String authorization, Long alertaId, String nuevoEstado,
            String notas) {
        MockHttpServletRequestBuilder pedido = put("/api/v1/alertas/estado/" + alertaId)
                .param("estado", nuevoEstado)
                .header("Authorization", authorization);
        return notas == null ? pedido : pedido.param("notas", notas);
    }

    protected Long confirmada(String clave, Long alertaId) throws Exception {
        ok(resolver(clave, alertaId, "CONFIRMADA", null));
        return alertaId;
    }

    /** POST /api/v1/alertas/intervenir/{id}; los campos en null no se mandan. */
    protected MvcResult intervenir(String clave, Long alertaId, String tipo, String descripcion,
            LocalDateTime fechaHora, String estadoSalud) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        if (tipo != null) {
            cuerpo.put("tipo", tipo);
        }
        if (descripcion != null) {
            cuerpo.put("descripcion", descripcion);
        }
        if (fechaHora != null) {
            cuerpo.put("fechaHora", fechaHora.toString());
        }
        if (estadoSalud != null) {
            cuerpo.put("estadoSalud", estadoSalud);
        }
        return mockMvc.perform(post("/api/v1/alertas/intervenir/" + alertaId)
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    // ── Listados ─────────────────────────────────────────────────────────────

    /** GET /api/v1/alertas con el establecimiento y los filtros opcionales (tipo, severidad, estado). */
    protected MvcResult listar(String clave, String establecimiento, String tipo, String severidad, String estado)
            throws Exception {
        MockHttpServletRequestBuilder pedido = get("/api/v1/alertas")
                .param("establecimientoId", idEstablecimiento(establecimiento).toString())
                .header("Authorization", bearer(clave));
        if (tipo != null) {
            pedido = pedido.param("tipo", tipo);
        }
        if (severidad != null) {
            pedido = pedido.param("severidad", severidad);
        }
        if (estado != null) {
            pedido = pedido.param("estado", estado);
        }
        return mockMvc.perform(pedido).andReturn();
    }

    protected JsonNode listadoOk(String clave, String establecimiento, String tipo, String severidad, String estado)
            throws Exception {
        return leer(ok(listar(clave, establecimiento, tipo, severidad, estado)));
    }

    protected static List<Long> ids(JsonNode alertas) {
        List<Long> ids = new ArrayList<>();
        alertas.forEach(a -> ids.add(a.get("id").asLong()));
        return ids;
    }

    protected static List<String> campo(JsonNode alertas, String nombre) {
        List<String> valores = new ArrayList<>();
        alertas.forEach(a -> valores.add(a.get(nombre).asText()));
        return valores;
    }

    // ── Detalle y línea de tiempo ────────────────────────────────────────────

    protected JsonNode detalle(String clave, String establecimiento, Long alertaId) throws Exception {
        return leer(ok(getAlerta(clave, "/" + alertaId, establecimiento)));
    }

    /** GET /api/v1/animales/{id}/linea-tiempo-alertas con los filtros opcionales. */
    protected MvcResult lineaTiempo(String clave, Long animalId, String tipo, LocalDateTime desde,
            LocalDateTime hasta) throws Exception {
        MockHttpServletRequestBuilder pedido = get("/api/v1/animales/" + animalId + "/linea-tiempo-alertas")
                .header("Authorization", bearer(clave));
        if (tipo != null) {
            pedido = pedido.param("tipo", tipo);
        }
        if (desde != null) {
            pedido = pedido.param("desde", desde.toString());
        }
        if (hasta != null) {
            pedido = pedido.param("hasta", hasta.toString());
        }
        return mockMvc.perform(pedido).andReturn();
    }
}
