package dev.vigia.backend.security;

import dev.vigia.backend.dto.AuthResponse;
import dev.vigia.backend.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;

    public OAuth2SuccessHandler(@Lazy AuthService authService) {
        this.authService = authService;
    }

    @Value("${app.oauth2.redirect-uri:http://localhost:5173/oauth2/callback}")
    private String redirectUri;

    @SuppressWarnings("null")
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2User oAuth2User = oauthToken.getPrincipal();
            String provider = oauthToken.getAuthorizedClientRegistrationId();

            String email = oAuth2User.getAttribute("email");
            String nombre = oAuth2User.getAttribute("given_name");
            if (nombre == null || nombre.isBlank()) {
                nombre = oAuth2User.getAttribute("name");
            }
            String apellido = oAuth2User.getAttribute("family_name");

            AuthResponse authResponse = authService.processOAuth2User(email, nombre, apellido, provider);

            String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("token", authResponse.getToken())
                    .queryParam("email", authResponse.getEmail())
                    .queryParam("rol", authResponse.getRol())
                    .build().toUriString();

            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        } else {
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
