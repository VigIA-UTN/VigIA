package dev.vigia.backend.security;

import dev.vigia.backend.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OAuth2 — el mensaje de error vuelve al frontend codificado")
class OAuth2RedireccionErrorTest {

    private static final String REDIRECT_URI = "http://localhost:5173/oauth2/callback";
    private static final String SIN_INVITACION = "No se puede registrar mediante SSO sin una invitación previa.";

    /** Valor del parámetro "error" tal como lo lee el navegador. */
    private static String errorDecodificado(String url) {
        String crudo = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("error");
        return URLDecoder.decode(crudo, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Ingreso con Google sin invitación: la redirección lleva el mensaje en UTF-8 codificado")
    void successHandler_errorConAcentos_codificado() throws Exception {
        AuthService authService = mock(AuthService.class);
        when(authService.processOAuth2User(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException(SIN_INVITACION));
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(authService);
        ReflectionTestUtils.setField(handler, "redirectUri", REDIRECT_URI);

        var usuario = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", "sin.invitacion@fixture.vigia.test", "given_name", "Ana", "family_name", "Pérez"), "email");
        var token = new OAuth2AuthenticationToken(usuario, usuario.getAuthorities(), "google");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, token);

        String url = response.getRedirectedUrl();
        assertThat(url).startsWith(REDIRECT_URI + "?error=").contains("invitaci%C3%B3n").doesNotContain("ó");
        assertThat(errorDecodificado(url)).isEqualTo(SIN_INVITACION);
    }

    @Test
    @DisplayName("Falla de autenticación de Google: la redirección lleva el mensaje codificado")
    void failureHandler_errorConAcentos_codificado() throws Exception {
        OAuth2FailureHandler handler = new OAuth2FailureHandler();
        ReflectionTestUtils.setField(handler, "redirectUri", REDIRECT_URI);
        String mensaje = "La autenticación con Google falló [invalid_request] {detalle}";
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, new BadCredentialsException(mensaje));

        String url = response.getRedirectedUrl();
        assertThat(url).startsWith(REDIRECT_URI + "?error=").doesNotContain("ó").doesNotContain(" ");
        assertThat(errorDecodificado(url)).isEqualTo(mensaje);
    }
}
