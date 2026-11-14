package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@DisplayName("H0037 - Gestión del perfil y cambio de contraseña — IT")
class PerfilUsuarioIT extends SeguridadItBase {

    @Test
    @DisplayName("H0037-01 - Perfil - Consultar y editar nombre y apellido")
    void H0037_01_editarPerfil() throws Exception {
        String bearer = bearer("veterinarioA");

        MvcResult actual = mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", bearer)).andReturn();
        assertThat(actual.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(actual).get("email").asText()).isEqualTo(email("veterinarioA"));

        MvcResult editado = mockMvc.perform(put("/api/v1/usuarios/me")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombre", "Valeria", "apellido", "Gómez"))))
                .andReturn();

        assertThat(editado.getResponse().getStatus()).isEqualTo(200);
        JsonNode perfil = leer(editado);
        assertThat(perfil.get("nombre").asText()).isEqualTo("Valeria");
        assertThat(perfil.get("apellido").asText()).isEqualTo("Gómez");
        assertThat(jdbcTemplate.queryForMap("SELECT nombre, apellido FROM usuarios WHERE email = ?",
                email("veterinarioA"))).containsEntry("nombre", "Valeria").containsEntry("apellido", "Gómez");
    }

    @Test
    @DisplayName("H0037-02 - Cambio de contraseña - Datos válidos")
    void H0037_02_cambioDePasswordValido() throws Exception {
        String email = email("camperoA");

        assertThat(cambiarPassword("camperoA", credenciales.password(), PASSWORD_VALIDA)).isEqualTo(200);

        assertThat(login(email, PASSWORD_VALIDA).getResponse().getStatus()).isEqualTo(200);
        assertThat(login(email, credenciales.password()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("H0037-03 - Cambio de contraseña - Actual incorrecta o nueva débil")
    void H0037_03_cambioDePasswordInvalido() throws Exception {
        assertThat(cambiarPassword("camperoA", "NoEsLaActual1", PASSWORD_VALIDA)).isEqualTo(400);
        assertThat(cambiarPassword("camperoA", credenciales.password(), "debil")).isEqualTo(400);
        assertThat(cambiarPassword("camperoA", credenciales.password(), "sinmayusculas123")).isEqualTo(400);

        // La contraseña no cambió.
        assertThat(login(email("camperoA"), credenciales.password()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("H0037-04 - Cambio de contraseña - Usuario registrado por SSO")
    void H0037_04_usuarioSso() throws Exception {
        String email = emailUnico();
        crearUsuario(email, "PRODUCTOR", "GOOGLE");
        String bearer = "Bearer " + tokenDe(email, credenciales.password());

        int estado = mockMvc.perform(put("/api/v1/usuarios/me/password")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("passwordActual", credenciales.password(),
                                "nuevaPassword", PASSWORD_VALIDA))))
                .andReturn().getResponse().getStatus();

        assertThat(estado).isEqualTo(400);
        assertThat(login(email, PASSWORD_VALIDA).getResponse().getStatus()).isEqualTo(401);
    }

    private int cambiarPassword(String clave, String actual, String nueva) throws Exception {
        return mockMvc.perform(put("/api/v1/usuarios/me/password")
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("passwordActual", actual, "nuevaPassword", nueva))))
                .andReturn().getResponse().getStatus();
    }
}
