package dev.vigia.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String jwt = null;
        final String userEmail;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        } else if (request.getParameter("token") != null) {
            jwt = request.getParameter("token");
        }

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            userEmail = jwtUtil.extractUsername(jwt);
            if (userEmail != null) {
                UserDetails userDetails = this.customUserDetailsService.loadUserByUsername(userEmail);
                // isEnabled() refleja usuarios.activo: un usuario desactivado pierde el acceso en el momento, no
                // cuando vence su JWT (hasta 24 h después).
                if (jwtUtil.validateToken(jwt, userDetails) && userDetails.isEnabled()) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    // Un contexto nuevo por request: el de getContext() puede ser el que guarda la sesión HTTP y
                    // comparten todos los requests con esa cookie; escribir ahí mezclaba usuarios simultáneos.
                    SecurityContext contexto = SecurityContextHolder.createEmptyContext();
                    contexto.setAuthentication(authToken);
                    SecurityContextHolder.setContext(contexto);
                }
            }
        } catch (Exception e) {
            log.debug("Error de autenticación con JWT: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
