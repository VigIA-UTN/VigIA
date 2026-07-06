package dev.vigia.backend.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil — generación y validación de tokens")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil();

        // Inyectamos los campos con reflexión ya que no levantamos el contexto de
        // Spring
        var secretField = JwtUtil.class.getDeclaredField("secret");
        secretField.setAccessible(true);
        secretField.set(jwtUtil, "testSecretKeyForVigiaApplicationWhichNeedsToBeAtLeast256BitsLong123");

        var expirationField = JwtUtil.class.getDeclaredField("jwtExpirationInMs");
        expirationField.setAccessible(true);
        expirationField.set(jwtUtil, 86400000L);
    }

    private UserDetails buildUser(String email, String role) {
        return new User(email, "hashed_password",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    // ─── Generación ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateToken() devuelve un token no nulo y no vacío")
    void generateToken_returnsNonBlankToken() {
        UserDetails user = buildUser("productor@vigia.dev", "PRODUCTOR");

        String token = jwtUtil.generateToken(user);

        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("El token contiene el email como subject")
    void generateToken_subjectIsEmail() {
        UserDetails user = buildUser("campero@vigia.dev", "CAMPERO");

        String token = jwtUtil.generateToken(user);
        String extractedEmail = jwtUtil.extractUsername(token);

        assertThat(extractedEmail).isEqualTo("campero@vigia.dev");
    }

    @Test
    @DisplayName("El token incluye el claim 'rol' sin el prefijo ROLE_")
    void generateToken_containsRoleClaim() {
        UserDetails user = buildUser("admin@vigia.dev", "ADMINISTRADOR");

        String token = jwtUtil.generateToken(user);
        String rol = jwtUtil.extractClaim(token, claims -> claims.get("rol", String.class));

        assertThat(rol).isEqualTo("ADMINISTRADOR");
    }

    // ─── Validación ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("validateToken() retorna true para un token válido")
    void validateToken_validTokenReturnsTrue() {
        UserDetails user = buildUser("vet@vigia.dev", "VETERINARIO");

        String token = jwtUtil.generateToken(user);

        assertThat(jwtUtil.validateToken(token, user)).isTrue();
    }

    @Test
    @DisplayName("validateToken() retorna false si el email no coincide")
    void validateToken_wrongUserReturnsFalse() {
        UserDetails owner = buildUser("owner@vigia.dev", "PRODUCTOR");
        UserDetails other = buildUser("otro@vigia.dev", "CAMPERO");

        String token = jwtUtil.generateToken(owner);

        assertThat(jwtUtil.validateToken(token, other)).isFalse();
    }

    @Test
    @DisplayName("extractUsername() lanza excepción con token manipulado")
    void extractUsername_tamperedTokenThrows() {
        UserDetails user = buildUser("legit@vigia.dev", "PRODUCTOR");
        String token = jwtUtil.generateToken(user);

        // Modificamos el payload para simular un token manipulado
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        Assertions.assertThrows(
                Exception.class,
                () -> jwtUtil.extractUsername(tampered));
    }
}
