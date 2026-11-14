package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("H0001 - Registro por invitación (código de productor / token de empleado) — IT")
class RegistroInvitacionIT extends SeguridadItBase {

    @Test
    @DisplayName("H0001-01 - Registro - Productor con código de invitación válido")
    void H0001_01_registroProductorConCodigo() throws Exception {
        String email = emailUnico();
        String token = crearInvitacion("PRODUCTOR", "PRODUCTOR", email, EST_A, "PENDIENTE",
                LocalDateTime.now().plusDays(7));

        MvcResult resultado = registrar(token, email, PASSWORD_VALIDA);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
        JsonNode cuerpo = leer(resultado);
        assertThat(cuerpo.get("token").asText()).isNotBlank();
        assertThat(cuerpo.get("rol").asText()).isEqualTo("PRODUCTOR");
        assertThat(cuerpo.get("email").asText()).isEqualTo(email);
        assertThat(esEmpleadoActivo(email, EST_A)).isTrue();
        assertThat(estadoInvitacion(token)).isEqualTo("USADA");
        assertThat(login(email, PASSWORD_VALIDA).getResponse().getStatus()).isEqualTo(200);
    }

    @ParameterizedTest(name = "H0001-02 - Registro - Empleado {0} con token")
    @ValueSource(strings = { "VETERINARIO", "CAMPERO" })
    @DisplayName("H0001-02 - Registro - Empleado (Veterinario/Campero) con token de invitación")
    void H0001_02_registroEmpleadoConToken(String rol) throws Exception {
        String email = emailUnico();
        String token = crearInvitacion("EMPLEADO", rol, email, EST_A, "PENDIENTE", LocalDateTime.now().plusDays(7));

        // El empleado no manda email: el registro toma el de la invitación.
        MvcResult resultado = registrar(token, null, PASSWORD_VALIDA);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
        assertThat(leer(resultado).get("rol").asText()).isEqualTo(rol);
        assertThat(esEmpleadoActivo(email, EST_A)).isTrue();
        assertThat(estadoInvitacion(token)).isEqualTo("USADA");
    }

    @Test
    @DisplayName("H0001-03 - Registro - Token vencido")
    void H0001_03_tokenVencido() throws Exception {
        String email = emailUnico();
        String token = crearInvitacion("EMPLEADO", "CAMPERO", email, EST_A, "PENDIENTE",
                LocalDateTime.now().minusHours(1));

        MvcResult resultado = registrar(token, null, PASSWORD_VALIDA);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(resultado.getResponse().getContentAsString()).contains("expirado");
        assertThat(estadoInvitacion(token)).isEqualTo("EXPIRADA");
        assertThat(contarUsuarios(email)).isZero();
    }

    @Test
    @DisplayName("H0001-04 - Registro - Token ya usado")
    void H0001_04_tokenUsado() throws Exception {
        String email = emailUnico();
        String token = crearInvitacion("EMPLEADO", "VETERINARIO", email, EST_A, "PENDIENTE",
                LocalDateTime.now().plusDays(7));
        assertThat(registrar(token, null, PASSWORD_VALIDA).getResponse().getStatus()).isEqualTo(201);

        MvcResult segundo = registrar(token, null, PASSWORD_VALIDA);

        assertThat(segundo.getResponse().getStatus()).isEqualTo(400);
        assertThat(segundo.getResponse().getContentAsString()).contains("utilizada");
        assertThat(contarUsuarios(email)).isEqualTo(1);
    }

    @Test
    @DisplayName("H0001-05 - Registro - Email ya registrado")
    void H0001_05_emailDuplicado() throws Exception {
        // La invitación apunta a un usuario que ya existe (el campero de A).
        String emailExistente = email("camperoA");
        String token = crearInvitacion("EMPLEADO", "CAMPERO", emailExistente, EST_B, "PENDIENTE",
                LocalDateTime.now().plusDays(7));

        MvcResult resultado = registrar(token, null, PASSWORD_VALIDA);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(resultado.getResponse().getContentAsString()).contains("ya se encuentra registrado");
        assertThat(contarUsuarios(emailExistente)).isEqualTo(1);
        assertThat(estadoInvitacion(token)).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("H0001-06 - Registro - Contraseña débil y código inexistente")
    void H0001_06_passwordDebilYCodigoInexistente() throws Exception {
        String email = emailUnico();
        String token = crearInvitacion("EMPLEADO", "CAMPERO", email, EST_A, "PENDIENTE",
                LocalDateTime.now().plusDays(7));

        MvcResult debil = registrar(token, null, "corta");
        assertThat(debil.getResponse().getStatus()).isEqualTo(400);
        assertThat(contarUsuarios(email)).isZero();
        assertThat(estadoInvitacion(token)).isEqualTo("PENDIENTE");

        MvcResult inexistente = mockMvc.perform(post("/api/v1/auth/validate-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "no-existe-" + System.nanoTime()))))
                .andReturn();
        assertThat(inexistente.getResponse().getStatus()).isEqualTo(400);
        assertThat(inexistente.getResponse().getContentAsString()).contains("inválido");
    }

    private MvcResult registrar(String token, String email, String password) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", "Nuevo");
        cuerpo.put("apellido", "Usuario");
        cuerpo.put("email", email);
        cuerpo.put("password", password);
        cuerpo.put("token", token);
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    private int contarUsuarios(String email) {
        Integer cantidad = jdbcTemplate.queryForObject("SELECT count(*) FROM usuarios WHERE email = ?",
                Integer.class, email);
        return cantidad == null ? 0 : cantidad;
    }
}
