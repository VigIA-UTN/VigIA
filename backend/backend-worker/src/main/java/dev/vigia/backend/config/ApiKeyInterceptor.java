package dev.vigia.backend.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Slf4j
public class ApiKeyInterceptor implements HandlerInterceptor {

    @Value("${vigia.ttn.webhook.api-key:}")
    private String configuredApiKey;

    @PostConstruct
    void validarConfiguracion() {
        if (!StringUtils.hasText(configuredApiKey)) {
            throw new IllegalStateException(
                    "vigia.ttn.webhook.api-key (TTN_WEBHOOK_API_KEY) es obligatoria para proteger el webhook de TTN");
        }
    }

    @Override
    @SuppressWarnings("null")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (MessageDigest.isEqual(
                    configuredApiKey.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }

        log.warn("Intento de acceso no autorizado al webhook de TTN desde {}", request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}
