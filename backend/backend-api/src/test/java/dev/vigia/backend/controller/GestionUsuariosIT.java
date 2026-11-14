package dev.vigia.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@DisplayName("H0036 - Gestión de usuarios por el administrador — IT")
class GestionUsuariosIT extends SeguridadItBase {

    @Test
    @DisplayName("H0036-01 - Gestión de usuarios - Listado del administrador")
    void H0036_01_listadoDelAdministrador() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", bearer("admin")))
                .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        String cuerpo = resultado.getResponse().getContentAsString();
        assertThat(cuerpo).contains(email("productorA"), email("veterinarioB"), email("camperoA"));
        // El listado es de usuarios no administradores.
        assertThat(cuerpo).doesNotContain(email("admin"));
    }

    @Test
    @DisplayName("H0036-02 - Gestión de usuarios - Desactivar y reactivar")
    void H0036_02_desactivarYReactivar() throws Exception {
        String email = email("camperoA");

        assertThat(cambiarEstado("admin", idUsuario(email))).isEqualTo(200);
        assertThat(activo(email)).isFalse();
        assertThat(login(email, credenciales.password()).getResponse().getStatus()).isEqualTo(403);

        assertThat(cambiarEstado("admin", idUsuario(email))).isEqualTo(200);
        assertThat(activo(email)).isTrue();
        assertThat(login(email, credenciales.password()).getResponse().getStatus()).isEqualTo(200);
    }

    @ParameterizedTest(name = "H0036-03 - Gestión de usuarios - Rol {0} sin permiso")
    @ValueSource(strings = { "productorA", "veterinarioA", "camperoA" })
    @DisplayName("H0036-03 - Gestión de usuarios - Cambio de estado por un rol no administrador")
    void H0036_03_noAdminNoCambiaEstado(String clave) throws Exception {
        String objetivo = email("camperoB");

        assertThat(cambiarEstado(clave, idUsuario(objetivo))).isEqualTo(403);
        assertThat(activo(objetivo)).isTrue();
    }

    @Test
    @DisplayName("H0036-04 - Gestión de usuarios - Sesión abierta de un usuario desactivado")
    void H0036_04_sesionDeUsuarioDesactivado() throws Exception {
        String tokenPrevio = bearer("camperoA");
        assertThat(cambiarEstado("admin", idUsuario(email("camperoA")))).isEqualTo(200);

        int estado = mockMvc.perform(get("/api/v1/animales")
                        .param("establecimientoId", idEstablecimiento(EST_A).toString())
                        .header("Authorization", tokenPrevio))
                .andReturn().getResponse().getStatus();

        assertThat(estado).as("un usuario desactivado no debe seguir operando con su JWT").isIn(401, 403);
    }

    private int cambiarEstado(String clave, Long usuarioId) throws Exception {
        return mockMvc.perform(patch("/api/v1/usuarios/" + usuarioId + "/estado")
                        .header("Authorization", bearer(clave)))
                .andReturn().getResponse().getStatus();
    }

    private boolean activo(String email) {
        Integer activo = jdbcTemplate.queryForObject("SELECT activo FROM usuarios WHERE email = ?", Integer.class,
                email);
        return activo != null && activo == 1;
    }
}
