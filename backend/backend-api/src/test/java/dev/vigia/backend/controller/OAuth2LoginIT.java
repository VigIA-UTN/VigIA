package dev.vigia.backend.controller;

import dev.vigia.backend.security.OAuth2SuccessHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * El proveedor OAuth2 se simula entregándole al {@link OAuth2SuccessHandler} real la autenticación que armaría Spring
 * Security después del callback de Google: un {@link OAuth2AuthenticationToken} con los atributos del usuario. Así se
 * prueba el tramo propio de VigIA (alta por invitación, JWT y redirect al frontend) sin salir a Internet.
 */
@DisplayName("H0014 - Login mediante SSO/OAuth 2.0 — IT")
class OAuth2LoginIT extends SeguridadItBase {

    @Autowired
    private OAuth2SuccessHandler successHandler;

    @Test
    @DisplayName("H0014-01 - SSO - Usuario existente")
    void H0014_01_usuarioExistente() throws Exception {
        UriComponents redirect = loginConProveedor(email("productorA"));

        assertThat(redirect.getQueryParams().getFirst("error")).isNull();
        assertThat(redirect.getQueryParams().getFirst("token")).isNotBlank();
        assertThat(redirect.getQueryParams().getFirst("rol")).isEqualTo("PRODUCTOR");
        assertThat(jdbcTemplate.queryForObject("SELECT proveedor_auth FROM usuarios WHERE email = ?", String.class,
                email("productorA"))).isEqualTo("GOOGLE");
    }

    @Test
    @DisplayName("H0014-02 - SSO - Usuario nuevo con invitación pendiente")
    void H0014_02_usuarioNuevoConInvitacion() throws Exception {
        String email = emailUnico();
        String token = crearInvitacion("EMPLEADO", "VETERINARIO", email, EST_A, "PENDIENTE",
                LocalDateTime.now().plusDays(7));

        UriComponents redirect = loginConProveedor(email);

        assertThat(redirect.getQueryParams().getFirst("error")).isNull();
        assertThat(redirect.getQueryParams().getFirst("rol")).isEqualTo("VETERINARIO");
        assertThat(estadoInvitacion(token)).isEqualTo("USADA");
        assertThat(esEmpleadoActivo(email, EST_A)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT proveedor_auth FROM usuarios WHERE email = ?", String.class,
                email)).isEqualTo("GOOGLE");
    }

    @Test
    @DisplayName("H0014-03 - SSO - Usuario sin invitación")
    void H0014_03_usuarioSinInvitacion() throws Exception {
        String email = emailUnico();

        UriComponents redirect = loginConProveedor(email);

        assertThat(redirect.getQueryParams().getFirst("token")).isNull();
        assertThat(redirect.getQueryParams().getFirst("error")).isNotBlank();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM usuarios WHERE email = ?", Integer.class,
                email)).isZero();
    }

    @Test
    @DisplayName("H0014-04 - SSO - Invitación vencida")
    void H0014_04_invitacionVencida() throws Exception {
        String email = emailUnico();
        crearInvitacion("EMPLEADO", "CAMPERO", email, EST_A, "PENDIENTE", LocalDateTime.now().minusDays(1));

        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "nombre", "Nuevo", "apellido", "SSO"))))
                .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM usuarios WHERE email = ?", Integer.class,
                email)).isZero();
    }

    private UriComponents loginConProveedor(String email) throws Exception {
        var usuario = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("email", email, "given_name", "Nombre", "family_name", "Apellido"), "email");
        var autenticacion = new OAuth2AuthenticationToken(usuario, usuario.getAuthorities(), "google");
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacion);

        assertThat(response.getRedirectedUrl()).isNotNull();
        return UriComponentsBuilder.fromUriString(response.getRedirectedUrl()).build();
    }
}
