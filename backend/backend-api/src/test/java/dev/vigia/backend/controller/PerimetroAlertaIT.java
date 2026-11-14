package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.service.EmailService;
import dev.vigia.backend.service.SseAlertaPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Perímetro del establecimiento y las alertas que genera al salir un animal. El perímetro de hoy es geográfico
 * (área, tolerancia y zona estricta); no tiene un horario nocturno, así que estos casos cubren su configuración y la
 * notificación de la alerta de salida, y no la distinción dentro y fuera de horario del nombre de la HU.
 * <p>
 * La alerta la genera el worker al detectar la salida; la API la lista, la muestra y, cuando llega el evento de alerta
 * nueva, avisa por mail a los empleados del establecimiento. El envío real se reemplaza por un doble.
 */
@DisplayName("H0027 - Notificaciones por perímetro nocturno configurable — IT")
class PerimetroAlertaIT extends CicloAlertasItBase {

    @MockBean
    private EmailService emailService;

    @Autowired
    private SseAlertaPublisher sseAlertaPublisher;

    @Test
    @DisplayName("H0027-01 - Perímetro configurable - Tolerancia y zona estricta, validaciones, activar/desactivar y permisos")
    void H0027_01_configuracionDelPerimetro() throws Exception {
        Long perimetro = idPerimetro(EST_A);
        JsonNode original = leer(ok(consultar("productorA", perimetro)));
        assertThat(original.get("distanciaToleranciaMetros").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(original.get("zonaEstricta").asBoolean()).isFalse();

        // Se configura una tolerancia de 25,5 m y zona estricta.
        MvcResult actualizado = actualizar("productorA", perimetro, cuerpo(original, "25.5", true));
        assertThat(estado(actualizado)).as(texto(actualizado)).isEqualTo(200);
        assertThat(leer(actualizado).get("distanciaToleranciaMetros").decimalValue())
                .isEqualByComparingTo(new BigDecimal("25.5"));
        assertThat(leer(actualizado).get("zonaEstricta").asBoolean()).isTrue();
        Map<String, Object> fila = jdbcTemplate.queryForMap("SELECT distancia_tolerancia_metros, zona_estricta, "
                + "activo FROM perimetro WHERE id = ?", perimetro);
        assertThat((BigDecimal) fila.get("distancia_tolerancia_metros")).isEqualByComparingTo(new BigDecimal("25.5"));
        assertThat(fila.get("zona_estricta")).isEqualTo(true);
        // Y lo ven todos los roles del establecimiento.
        for (String clave : new String[] {"veterinarioA", "camperoA"}) {
            JsonNode visto = leer(ok(consultar(clave, perimetro)));
            assertThat(visto.get("zonaEstricta").asBoolean()).as(clave).isTrue();
            assertThat(visto.get("distanciaToleranciaMetros").decimalValue()).as(clave)
                    .isEqualByComparingTo(new BigDecimal("25.5"));
        }

        // Si la actualización no los manda, conserva lo configurado.
        JsonNode sinCampos = cuerpo(original, null, null);
        assertThat(estado(actualizar("productorA", perimetro, sinCampos))).isEqualTo(200);
        assertThat(leer(consultar("productorA", perimetro)).get("zonaEstricta").asBoolean()).isTrue();

        // Validaciones: tolerancia negativa, menos de 3 vértices y nombre vacío no se guardan.
        assertThat(estado(actualizar("productorA", perimetro, cuerpo(original, "-1", false)))).isEqualTo(400);
        Map<String, Object> pocosVertices = mapa(original, "10", false);
        pocosVertices.put("coordenadas", List.of(Map.of("latitud", -36.5, "longitud", -61.5),
                Map.of("latitud", -36.51, "longitud", -61.5)));
        assertThat(estado(actualizar("productorA", perimetro, pocosVertices))).isEqualTo(400);
        Map<String, Object> sinNombre = mapa(original, "10", false);
        sinNombre.put("nombre", " ");
        assertThat(estado(actualizar("productorA", perimetro, sinNombre))).isEqualTo(400);
        assertThat(jdbcTemplate.queryForObject("SELECT distancia_tolerancia_metros FROM perimetro WHERE id = ?",
                BigDecimal.class, perimetro)).isEqualByComparingTo(new BigDecimal("25.5"));

        activarDesactivarYPermisos(perimetro, original);
    }

    @Test
    @DisplayName("H0027-02 - Alerta de salida del perímetro - Listado, detalle y tiempo fuera")
    void H0027_02_alertaDeSalida() throws Exception {
        Long alerta = idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO");

        // El filtro "perímetro" ubica la alerta del animal que salió, con el lote y el tiempo que lleva afuera.
        JsonNode listado = listadoOk("camperoA", EST_A, "perimetro", null, "PENDIENTE");
        assertThat(ids(listado)).containsExactly(alerta);
        assertThat(listado.get(0).get("severidad").asText()).isEqualTo("ALTA");
        JsonNode pendiente = detalle("camperoA", EST_A, alerta).get("detalleGeoespacial");
        assertThat(pendiente.get("nombreLote").asText()).isEqualTo("Rodeo A1");
        assertThat(pendiente.get("segundosFuera").asLong()).isBetween(1790L, 3600L);

        // Al resolverla, el tiempo fuera queda fijo entre la salida y la resolución, y ya no sigue corriendo.
        ok(resolver("camperoA", alerta, "CONFIRMADA", null));
        Map<String, Object> fila = jdbcTemplate.queryForMap("SELECT ts_generacion, ts_confirmacion FROM alertas "
                + "WHERE id = ?", alerta);
        long esperado = Duration.between(((Timestamp) fila.get("ts_generacion")).toLocalDateTime(),
                ((Timestamp) fila.get("ts_confirmacion")).toLocalDateTime()).getSeconds();
        JsonNode primera = detalle("camperoA", EST_A, alerta).get("detalleGeoespacial");
        JsonNode segunda = detalle("camperoA", EST_A, alerta).get("detalleGeoespacial");
        assertThat(primera.get("segundosFuera").asLong()).isEqualTo(esperado);
        assertThat(segunda.get("segundosFuera").asLong()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("H0027-03 - Notificación por mail - La alerta nueva llega a los empleados del establecimiento y solo a ellos")
    void H0027_03_notificaMailsDeAlertaNueva() throws Exception {
        Long alerta = idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO");
        AlertaEventDTO nueva = eventoDeSalida(alerta, "A-105", EST_A, true);

        sseAlertaPublisher.handleAlertaEvent(nueva);

        // Los cuatro empleados activos de Fixture A, con el nombre del establecimiento; ninguno de Fixture B.
        ArgumentCaptor<String> destinatarios = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(4)).enviarAlerta(destinatarios.capture(), eq(EST_A), any(Alerta.class));
        assertThat(destinatarios.getAllValues()).containsExactlyInAnyOrder(email("productorA"), email("veterinarioA"),
                email("camperoA"), email("productorAmbos"));

        // La alerta de B avisa a los de B, también al productor que está en los dos, y no a los de A.
        org.mockito.Mockito.clearInvocations(emailService);
        sseAlertaPublisher.handleAlertaEvent(
                eventoDeSalida(idAlerta(EST_B, "B-105", "FUERA_DE_PERIMETRO"), "B-105", EST_B, true));
        ArgumentCaptor<String> destinatariosB = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(4)).enviarAlerta(destinatariosB.capture(), eq(EST_B), any(Alerta.class));
        assertThat(destinatariosB.getAllValues()).containsExactlyInAnyOrder(email("productorB"),
                email("veterinarioB"), email("camperoB"), email("productorAmbos"));
    }

    @Test
    @DisplayName("H0027-04 - Notificación por mail - El cambio de estado, un empleado dado de baja o sin animal no notifican")
    void H0027_04_casosQueNoNotifican() throws Exception {
        Long alerta = idAlerta(EST_A, "A-105", "FUERA_DE_PERIMETRO");

        // Confirmar o descartar publica la alerta completa, pero no es una alerta nueva: no manda mail.
        sseAlertaPublisher.handleAlertaEvent(eventoDeSalida(alerta, "A-105", EST_A, false));
        verifyNoInteractions(emailService);

        // Un empleado dado de baja deja de recibir las alertas nuevas.
        jdbcTemplate.update("UPDATE empleado SET activo = 0 WHERE usuario_id = ? AND establecimiento_id = ?",
                idUsuario(email("camperoA")), idEstablecimiento(EST_A));
        sseAlertaPublisher.handleAlertaEvent(eventoDeSalida(alerta, "A-105", EST_A, true));
        ArgumentCaptor<String> destinatarios = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(3)).enviarAlerta(destinatarios.capture(), eq(EST_A), any(Alerta.class));
        assertThat(destinatarios.getAllValues()).doesNotContain(email("camperoA"));

        // Un evento sin animal no tiene a quién avisar.
        org.mockito.Mockito.clearInvocations(emailService);
        sseAlertaPublisher.handleAlertaEvent(AlertaEventDTO.builder().id(alerta).estado(EstadoAlerta.PENDIENTE)
                .disparador(DisparadorAlerta.FUERA_DE_PERIMETRO).nueva(true).build());
        verifyNoInteractions(emailService);
    }

    /** Activar y desactivar, y quién puede configurar el perímetro; no cambia su configuración. */
    private void activarDesactivarYPermisos(Long perimetro, JsonNode original) throws Exception {
        Map<String, Object> antes = jdbcTemplate.queryForMap("SELECT distancia_tolerancia_metros, zona_estricta, "
                + "activo FROM perimetro WHERE id = ?", perimetro);

        assertThat(estado(cambiarActivo("productorA", perimetro, "desactivar"))).isEqualTo(200);
        assertThat(leer(consultar("camperoA", perimetro)).get("activo").asBoolean()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT activo FROM perimetro WHERE id = ?", Boolean.class, perimetro))
                .isFalse();
        assertThat(estado(cambiarActivo("productorA", perimetro, "activar"))).isEqualTo(200);
        assertThat(leer(consultar("camperoA", perimetro)).get("activo").asBoolean()).isTrue();

        // Solo el productor configura: veterinario y campero lo leen pero no lo cambian.
        for (String clave : new String[] {"veterinarioA", "camperoA"}) {
            assertThat(estado(actualizar(clave, perimetro, cuerpo(original, "50", true)))).as(clave).isEqualTo(403);
            assertThat(estado(cambiarActivo(clave, perimetro, "desactivar"))).as(clave).isEqualTo(403);
        }
        // Otro establecimiento no lo lee ni lo cambia.
        assertThat(estado(consultar("productorB", perimetro))).isEqualTo(403);
        assertThat(estado(actualizar("productorB", perimetro, cuerpo(original, "50", true)))).isEqualTo(403);
        assertThat(estado(cambiarActivo("productorB", perimetro, "desactivar"))).isEqualTo(403);

        assertThat(jdbcTemplate.queryForMap("SELECT distancia_tolerancia_metros, zona_estricta, activo FROM perimetro "
                + "WHERE id = ?", perimetro)).isEqualTo(antes);
    }

    // ── Pedidos ──────────────────────────────────────────────────────────────

    private MvcResult consultar(String clave, Long perimetro) throws Exception {
        return mockMvc.perform(get("/api/v1/perimetros/" + perimetro).header("Authorization", bearer(clave)))
                .andReturn();
    }

    private MvcResult cambiarActivo(String clave, Long perimetro, String accion) throws Exception {
        return mockMvc.perform(patch("/api/v1/perimetros/" + perimetro + "/" + accion)
                .header("Authorization", bearer(clave))).andReturn();
    }

    private MvcResult actualizar(String clave, Long perimetro, Object cuerpo) throws Exception {
        return mockMvc.perform(put("/api/v1/perimetros/" + perimetro)
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    /** El cuerpo de la actualización con los datos actuales del perímetro; tolerancia y zona en null no se mandan. */
    private Map<String, Object> mapa(JsonNode perimetro, String tolerancia, Boolean zonaEstricta) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", perimetro.get("nombre").asText());
        cuerpo.put("establecimientoId", perimetro.get("establecimientoId").asLong());
        cuerpo.put("loteIds", objectMapper.convertValue(perimetro.get("loteIds"), List.class));
        cuerpo.put("coordenadas", objectMapper.convertValue(perimetro.get("coordenadas"), List.class));
        if (tolerancia != null) {
            cuerpo.put("distanciaToleranciaMetros", new BigDecimal(tolerancia));
        }
        if (zonaEstricta != null) {
            cuerpo.put("zonaEstricta", zonaEstricta);
        }
        return cuerpo;
    }

    private JsonNode cuerpo(JsonNode perimetro, String tolerancia, Boolean zonaEstricta) throws Exception {
        return objectMapper.valueToTree(mapa(perimetro, tolerancia, zonaEstricta));
    }

    private AlertaEventDTO eventoDeSalida(Long alertaId, String animal, String establecimiento, boolean nueva) {
        return AlertaEventDTO.builder()
                .id(alertaId)
                .animalId(idAnimal(establecimiento, animal))
                .tipo("Fuera de Perímetro")
                .disparador(DisparadorAlerta.FUERA_DE_PERIMETRO)
                .severidad(SeveridadAlerta.ALTA)
                .estado(EstadoAlerta.PENDIENTE)
                .valorUmbral(1.0)
                .valorDetectado(0.0)
                .tsGeneracion(LocalDateTime.now())
                .nueva(nueva)
                .build();
    }
}
