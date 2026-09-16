package dev.vigia.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class ApiKeyInterceptor implements HandlerInterceptor {

    @Value("${vigia.ttn.webhook.api-key:}")
    private String configuredApiKey;

    @Override
    @SuppressWarnings("null")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // En un entorno productivo, se exigiría siempre.
        if (!StringUtils.hasText(configuredApiKey)) {
            log.warn("TTN Webhook API Key no configurada. Se permiten solicitudes sin autenticación.");
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (configuredApiKey.equals(token)) {
                return true;
            }
        }

        log.warn("Intento de acceso no autorizado al webhook de TTN desde {}", request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}
