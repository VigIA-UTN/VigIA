package dev.vigia.backend.controller;

import dev.vigia.backend.dto.AuthRequest;
import dev.vigia.backend.dto.AuthResponse;
import dev.vigia.backend.security.CustomUserDetailsService;
import dev.vigia.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

        private final AuthenticationManager authenticationManager;
        private final CustomUserDetailsService userDetailsService;
        private final JwtUtil jwtUtil;

        @PostMapping("/login")
        @SuppressWarnings("null")
        public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

                final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
                final String jwt = jwtUtil.generateToken(userDetails);

                String role = userDetails.getAuthorities().stream()
                                .findFirst()
                                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                                .map(r -> r.replaceFirst("^ROLE_", ""))
                                .orElse("");

                return ResponseEntity.ok(AuthResponse.builder()
                                .token(jwt)
                                .email(userDetails.getUsername())
                                .rol(role)
                                .build());
        }

        @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
        public ResponseEntity<String> handleAuthenticationException(
                        org.springframework.security.core.AuthenticationException e) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email o contraseña incorrectos");
        }
}
