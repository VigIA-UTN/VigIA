package dev.vigia.backend.controller;

import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.AuthResponse;
import dev.vigia.backend.dto.RegisterRequest;
import dev.vigia.backend.dto.ValidateCodeRequest;
import dev.vigia.backend.security.CustomUserDetailsService;
import dev.vigia.backend.security.JwtUtil;
import dev.vigia.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final AuthService authService;

    /**
     * POST /api/v1/auth/register
     * <p>
     * Registra un nuevo usuario y dispara el envío del código de validación
     * a su email.
     *
     * @param request datos de registro del usuario
     * @return 201 con el resultado del registro, 400 si hay error de validación
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/auth/validate-code
     * <p>
     * Valida el código enviado por email para activar la cuenta registrada.
     *
     * @param request email y código a validar
     * @return 200 con el resultado de la validación, 400 si el código es
     *         inválido o expiró
     */
    @PostMapping("/validate-code")
    public ResponseEntity<?> validateCode(@RequestBody ValidateCodeRequest request) {
        try {
            return ResponseEntity.ok(authService.validateCode(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/auth/login
     * <p>
     * Autentica al usuario con email y contraseña y devuelve un JWT.
     * Bloquea la cuenta temporalmente tras varios intentos fallidos.
     *
     * @param request email y contraseña
     * @return 200 con el token JWT, 401 si las credenciales son incorrectas,
     *         403 si la cuenta no está activa, 423 si la cuenta está bloqueada
     */
    @PostMapping("/login")
    @SuppressWarnings("null")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        if (authService.isAccountLocked(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body("Cuenta bloqueada por 30 minutos debido a intentos fallidos.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            authService.resetFailedLogin(request.getEmail());

            final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
            final String jwt = jwtUtil.generateToken(userDetails);

            String role = userDetails.getAuthorities().stream()
                    .findFirst()
                    .map(GrantedAuthority::getAuthority)
                    .map(r -> r.replaceFirst("^ROLE_", ""))
                    .orElse("");

            return ResponseEntity.ok(AuthResponse.builder()
                    .token(jwt)
                    .email(userDetails.getUsername())
                    .rol(role)
                    .build());
        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("La cuenta no está activa.");
        } catch (AuthenticationException e) {
            authService.handleFailedLogin(request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email o contraseña incorrectos");
        }
    }

    /**
     * POST /api/v1/auth/oauth2/google
     * <p>
     * Autentica o registra un usuario a partir de datos de Google OAuth2.
     *
     * @param request email, nombre, apellido y proveedor (GOOGLE por defecto)
     * @return 200 con el token JWT, 400 si hay error de validación
     */
    @PostMapping("/oauth2/google")
    public ResponseEntity<?> oauth2Google(@RequestBody dev.vigia.backend.dto.OAuth2Request request) {
        try {
            String provider = request.getProvider() != null ? request.getProvider() : "GOOGLE";
            AuthResponse response = authService.processOAuth2User(request.getEmail(), request.getNombre(), request.getApellido(), provider);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/auth/oauth2
     * <p>
     * Autentica o registra un usuario a partir de datos OAuth2 de un
     * proveedor genérico.
     *
     * @param request email, nombre, apellido y proveedor OAuth2
     * @return 200 con el token JWT, 400 si hay error de validación
     */
    @PostMapping("/oauth2")
    public ResponseEntity<?> oauth2Login(@RequestBody dev.vigia.backend.dto.OAuth2Request request) {
        try {
            AuthResponse response = authService.processOAuth2User(request.getEmail(), request.getNombre(), request.getApellido(), request.getProvider());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
