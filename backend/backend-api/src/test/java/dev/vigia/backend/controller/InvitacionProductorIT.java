package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("H0032 - Alta de productores e invitación — IT")
class InvitacionProductorIT extends SeguridadItBase {

    @Test
    @DisplayName("H0032-01 - Invitar productor - Email nuevo")
    void H0032_01_invitarEmailNuevo() throws Exception {
        String email = emailUnico();

        MvcResult resultado = invitar("admin", email, idEstablecimiento(EST_A));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
        JsonNode invitacion = leer(resultado);
        assertThat(invitacion.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(invitacion.get("rol").asText()).isEqualTo("PRODUCTOR");
        assertThat(invitacion.get("token").asText()).isNotBlank();
        LocalDateTime creada = LocalDateTime.parse(invitacion.get("fechaCreacion").asText());
        LocalDateTime vence = LocalDateTime.parse(invitacion.get("fechaExpiracion").asText());
        assertThat(Duration.between(creada, vence).toDays()).isEqualTo(7);
        assertThat(estadoInvitacion(invitacion.get("token").asText())).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("H0032-02 - Invitar productor - Email ya registrado se vincula directo")
    void H0032_02_emailRegistradoSeVinculaDirecto() throws Exception {
        // productorA existe pero no es empleado de B.
        MvcResult resultado = invitar("admin", email("productorA"), idEstablecimiento(EST_B));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
        assertThat(leer(resultado).get("estado").asText()).isEqualTo("USADA");
        assertThat(esEmpleadoActivo(email("productorA"), EST_B)).isTrue();
    }

    @Test
    @DisplayName("H0032-03 - Invitar productor - Ya activo en el establecimiento")
    void H0032_03_productorYaActivo() throws Exception {
        MvcResult resultado = invitar("admin", email("productorA"), idEstablecimiento(EST_A));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(resultado.getResponse().getContentAsString()).contains("ya pertenece");
    }

    @Test
    @DisplayName("H0032-04 - Invitar productor - Invitación pendiente vigente duplicada")
    void H0032_04_invitacionPendienteDuplicada() throws Exception {
        String email = emailUnico();
        Long establecimiento = idEstablecimiento(EST_A);
        assertThat(invitar("admin", email, establecimiento).getResponse().getStatus()).isEqualTo(201);

        MvcResult segunda = invitar("admin", email, establecimiento);

        assertThat(segunda.getResponse().getStatus()).isEqualTo(400);
        assertThat(segunda.getResponse().getContentAsString()).contains("pendiente vigente");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM invitaciones WHERE email_destino = ?",
                Integer.class, email)).isEqualTo(1);
    }

    @ParameterizedTest(name = "H0032-05 - Invitar productor - Rol {0} sin permiso")
    @ValueSource(strings = { "productorA", "veterinarioA", "camperoA" })
    @DisplayName("H0032-05 - Invitar productor - Rol no administrador")
    void H0032_05_noAdminNoPuedeInvitar(String clave) throws Exception {
        String email = emailUnico();

        assertThat(invitar(clave, email, idEstablecimiento(EST_A)).getResponse().getStatus()).isEqualTo(403);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM invitaciones WHERE email_destino = ?",
                Integer.class, email)).isZero();
    }

    @Test
    @DisplayName("H0032-06 - Invitaciones de productor - Listado y cancelación por el administrador")
    void H0032_06_listarYCancelar() throws Exception {
        String email = emailUnico();
        MvcResult creada = invitar("admin", email, idEstablecimiento(EST_A));
        String token = leer(creada).get("token").asText();
        long id = leer(creada).get("id").asLong();

        MvcResult listado = mockMvc.perform(get("/api/v1/invitaciones/productor")
                        .header("Authorization", bearer("admin")))
                .andReturn();
        assertThat(listado.getResponse().getStatus()).isEqualTo(200);
        assertThat(listado.getResponse().getContentAsString()).contains(email);

        MvcResult cancelada = mockMvc.perform(delete("/api/v1/invitaciones/" + id)
                        .header("Authorization", bearer("admin")))
                .andReturn();
        assertThat(cancelada.getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM invitaciones WHERE token = ?",
                Integer.class, token)).isZero();
    }

    private MvcResult invitar(String clave, String email, Long establecimientoId) throws Exception {
        return mockMvc.perform(post("/api/v1/invitaciones/productor")
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "establecimientoId", establecimientoId))))
                .andReturn();
    }
}
