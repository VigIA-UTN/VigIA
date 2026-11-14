package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("H0033 - Gestión de empleados (invitar Campero/Veterinario) — IT")
class InvitacionEmpleadoIT extends SeguridadItBase {

    @Test
    @DisplayName("H0033-01 - Invitar empleado - Campero")
    void H0033_01_invitarCampero() throws Exception {
        verificarInvitacionCreada("CAMPERO");
    }

    @Test
    @DisplayName("H0033-02 - Invitar empleado - Veterinario")
    void H0033_02_invitarVeterinario() throws Exception {
        verificarInvitacionCreada("VETERINARIO");
    }

    @ParameterizedTest(name = "H0033-03 - Invitar empleado - Rol {0} no permitido")
    @ValueSource(strings = { "PRODUCTOR", "ADMINISTRADOR", "PENDIENTE" })
    @DisplayName("H0033-03 - Invitar empleado - Rol de invitación no permitido")
    void H0033_03_rolNoPermitido(String rol) throws Exception {
        String email = emailUnico();

        MvcResult resultado = invitar("productorA", email, idEstablecimiento(EST_A), rol);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(contarInvitaciones(email)).isZero();
    }

    @Test
    @DisplayName("H0033-04 - Invitar empleado - Ya activo o invitación pendiente duplicada")
    void H0033_04_yaActivoOPendienteDuplicada() throws Exception {
        Long establecimiento = idEstablecimiento(EST_A);

        MvcResult yaActivo = invitar("productorA", email("camperoA"), establecimiento, "CAMPERO");
        assertThat(yaActivo.getResponse().getStatus()).isEqualTo(400);

        String email = emailUnico();
        assertThat(invitar("productorA", email, establecimiento, "CAMPERO").getResponse().getStatus())
                .isEqualTo(201);
        MvcResult duplicada = invitar("productorA", email, establecimiento, "CAMPERO");
        assertThat(duplicada.getResponse().getStatus()).isEqualTo(400);
        assertThat(contarInvitaciones(email)).isEqualTo(1);
    }

    @Test
    @DisplayName("H0033-05 - Invitar empleado - Productor de otro establecimiento")
    void H0033_05_productorDeOtroEstablecimiento() throws Exception {
        String email = emailUnico();

        // productorB no pertenece a Fixture A: es un recurso de otro establecimiento.
        MvcResult resultado = invitar("productorB", email, idEstablecimiento(EST_A), "CAMPERO");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(contarInvitaciones(email)).isZero();
    }

    @ParameterizedTest(name = "H0033-06 - Invitar empleado - Rol {0} sin permiso")
    @ValueSource(strings = { "veterinarioA", "camperoA", "admin" })
    @DisplayName("H0033-06 - Invitar empleado - Rol que no es productor")
    void H0033_06_noProductorNoPuedeInvitar(String clave) throws Exception {
        String email = emailUnico();

        assertThat(invitar(clave, email, idEstablecimiento(EST_A), "CAMPERO").getResponse().getStatus())
                .isEqualTo(403);
        assertThat(contarInvitaciones(email)).isZero();
    }

    private void verificarInvitacionCreada(String rol) throws Exception {
        String email = emailUnico();

        MvcResult resultado = invitar("productorA", email, idEstablecimiento(EST_A), rol);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
        JsonNode invitacion = leer(resultado);
        assertThat(invitacion.get("rol").asText()).isEqualTo(rol);
        assertThat(invitacion.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(jdbcTemplate.queryForObject("SELECT e.nombre FROM invitaciones i JOIN establecimientos e "
                + "ON e.id = i.establecimiento_id WHERE i.email_destino = ?", String.class, email)).isEqualTo(EST_A);
    }

    private MvcResult invitar(String clave, String email, Long establecimientoId, String rol) throws Exception {
        return mockMvc.perform(post("/api/v1/invitaciones/empleado")
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "establecimientoId", establecimientoId, "rol", rol))))
                .andReturn();
    }

    private int contarInvitaciones(String email) {
        Integer cantidad = jdbcTemplate.queryForObject("SELECT count(*) FROM invitaciones WHERE email_destino = ?",
                Integer.class, email);
        return cantidad == null ? 0 : cantidad;
    }
}
