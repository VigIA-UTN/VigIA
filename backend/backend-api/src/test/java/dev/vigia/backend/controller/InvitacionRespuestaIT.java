package dev.vigia.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("H0034 - Aceptar o rechazar invitaciones a otros establecimientos — IT")
class InvitacionRespuestaIT extends SeguridadItBase {

    @Test
    @DisplayName("H0034-01 - Invitación a otro establecimiento - Aceptar")
    void H0034_01_aceptar() throws Exception {
        String token = invitarAProductorAEnB(LocalDateTime.now().plusDays(7));

        MvcResult misInvitaciones = mockMvc.perform(get("/api/v1/invitaciones/mis-invitaciones")
                        .header("Authorization", bearer("productorA")))
                .andReturn();
        assertThat(misInvitaciones.getResponse().getContentAsString()).contains(EST_B);

        assertThat(responder("productorA", token, "aceptar")).isEqualTo(200);

        assertThat(estadoInvitacion(token)).isEqualTo("USADA");
        assertThat(esEmpleadoActivo(email("productorA"), EST_B)).isTrue();
        MvcResult establecimientos = mockMvc.perform(get("/api/v1/establecimientos")
                        .header("Authorization", bearer("productorA")))
                .andReturn();
        assertThat(establecimientos.getResponse().getContentAsString()).contains(EST_A, EST_B);
    }

    @Test
    @DisplayName("H0034-02 - Invitación a otro establecimiento - Rechazar")
    void H0034_02_rechazar() throws Exception {
        String token = invitarAProductorAEnB(LocalDateTime.now().plusDays(7));

        assertThat(responder("productorA", token, "rechazar")).isEqualTo(200);

        assertThat(estadoInvitacion(token)).isEqualTo("RECHAZADA");
        assertThat(esEmpleadoActivo(email("productorA"), EST_B)).isFalse();
        MvcResult animalesDeB = mockMvc.perform(get("/api/v1/animales")
                        .param("establecimientoId", idEstablecimiento(EST_B).toString())
                        .header("Authorization", bearer("productorA")))
                .andReturn();
        assertThat(animalesDeB.getResponse().getStatus()).isEqualTo(403);
        // Una invitación rechazada ya no se puede aceptar.
        assertThat(responder("productorA", token, "aceptar")).isEqualTo(400);
    }

    @Test
    @DisplayName("H0034-03 - Invitación a otro establecimiento - Dirigida a otro usuario")
    void H0034_03_invitacionDeOtroUsuario() throws Exception {
        String token = invitarAProductorAEnB(LocalDateTime.now().plusDays(7));

        int estado = responder("camperoA", token, "aceptar");

        assertThat(estado).isEqualTo(403);
        assertThat(estadoInvitacion(token)).isEqualTo("PENDIENTE");
        assertThat(esEmpleadoActivo(email("camperoA"), EST_B)).isFalse();
        assertThat(responder("camperoA", token, "rechazar")).isEqualTo(403);
    }

    @Test
    @DisplayName("H0034-04 - Invitación a otro establecimiento - Vencida")
    void H0034_04_invitacionVencida() throws Exception {
        String token = invitarAProductorAEnB(LocalDateTime.now().minusDays(1));

        int estado = responder("productorA", token, "aceptar");

        assertThat(estado).isIn(400, 410);
        assertThat(esEmpleadoActivo(email("productorA"), EST_B)).isFalse();
    }

    private String invitarAProductorAEnB(LocalDateTime vence) {
        return crearInvitacion("PRODUCTOR", "PRODUCTOR", email("productorA"), EST_B, "PENDIENTE", vence);
    }

    private int responder(String clave, String token, String accion) throws Exception {
        return mockMvc.perform(post("/api/v1/invitaciones/" + idInvitacion(token) + "/" + accion)
                        .header("Authorization", bearer(clave)))
                .andReturn().getResponse().getStatus();
    }
}
