package dev.vigia.backend.controller;

import dev.vigia.backend.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@DisplayName("H0002 - Login, bloqueo por intentos fallidos y expiración de sesión — IT")
class LoginSesionIT extends SeguridadItBase {

    @Autowired
    private AuthService authService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @ParameterizedTest(name = "H0002-01 - Login - Rol {0}")
    @ValueSource(strings = { "admin", "productorA", "veterinarioA", "camperoA" })
    @DisplayName("H0002-01 - Login - Credenciales válidas por rol")
    void H0002_01_loginPorRol(String clave) throws Exception {
        MvcResult resultado = login(email(clave), credenciales.password());

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(leer(resultado).get("token").asText()).isNotBlank();
        assertThat(leer(resultado).get("rol").asText()).isEqualTo(credenciales.usuario(clave).rol());
    }

    @Test
    @DisplayName("H0002-02 - Login - Credenciales inválidas y usuario inactivo")
    void H0002_02_credencialesInvalidasYUsuarioInactivo() throws Exception {
        MvcResult incorrecta = login(email("productorA"), "OtraClave999");
        assertThat(incorrecta.getResponse().getStatus()).isEqualTo(401);
        assertThat(incorrecta.getResponse().getContentAsString()).doesNotContain("token");

        MvcResult inexistente = login(emailUnico(), credenciales.password());
        assertThat(inexistente.getResponse().getStatus()).isEqualTo(401);

        jdbcTemplate.update("UPDATE usuarios SET activo = 0 WHERE email = ?", email("camperoA"));
        MvcResult inactivo = login(email("camperoA"), credenciales.password());
        assertThat(inactivo.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("H0002-03 - Login - Bloqueo tras 5 intentos fallidos")
    void H0002_03_bloqueoTrasCincoIntentos() throws Exception {
        String email = email("veterinarioA");
        for (int intento = 1; intento <= 5; intento++) {
            assertThat(login(email, "Incorrecta" + intento).getResponse().getStatus())
                    .as("intento fallido %d", intento).isEqualTo(401);
        }

        // Con la cuenta bloqueada, ni la contraseña correcta entra.
        MvcResult bloqueado = login(email, credenciales.password());
        assertThat(bloqueado.getResponse().getStatus()).isEqualTo(423);
        assertThat(bloqueado.getResponse().getContentAsString()).contains("bloqueada");

        // El bloqueo es por cuenta: otro usuario sigue entrando.
        assertThat(login(email("camperoA"), credenciales.password()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("H0002-04 - Login - Desbloqueo al expirar el bloqueo")
    void H0002_04_desbloqueoAlExpirar() throws Exception {
        String email = email("veterinarioA");
        for (int intento = 1; intento <= 5; intento++) {
            login(email, "Incorrecta" + intento);
        }
        assertThat(login(email, credenciales.password()).getResponse().getStatus()).isEqualTo(423);

        // Los 30 minutos del bloqueo están fijos en AuthServiceImpl: en vez de esperarlos se adelanta el vencimiento
        // en su caché, que es lo mismo que ve el servicio cuando pasa el tiempo.
        Map<String, LocalDateTime> bloqueos = cacheDeBloqueos();
        assertThat(bloqueos).containsKey(email);
        bloqueos.put(email, LocalDateTime.now().minusSeconds(1));

        MvcResult resultado = login(email, credenciales.password());
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(bloqueos).doesNotContainKey(email);
    }

    @Test
    @DisplayName("H0002-05 - Sesión - JWT vencido, manipulado o ausente")
    void H0002_05_expiracionDelJwt() throws Exception {
        String valido = token("productorA");
        assertThat(pedirPerfil("Bearer " + valido)).isEqualTo(200);

        String vencido = Jwts.builder()
                .setClaims(Map.of("rol", "PRODUCTOR"))
                .setSubject(email("productorA"))
                .setIssuedAt(new Date(System.currentTimeMillis() - 2 * 86_400_000L))
                .setExpiration(new Date(System.currentTimeMillis() - 60_000L))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
        assertThat(pedirPerfil("Bearer " + vencido)).isEqualTo(401);

        // Se cambia un carácter de la firma. No el último: en base64url sus bits bajos son relleno y el cambio podría
        // decodificar a la misma firma.
        int posicion = valido.length() - 10;
        char original = valido.charAt(posicion);
        String manipulado = valido.substring(0, posicion) + (original == 'x' ? 'y' : 'x')
                + valido.substring(posicion + 1);
        assertThat(pedirPerfil("Bearer " + manipulado)).isEqualTo(401);

        assertThat(pedirPerfil(null)).isEqualTo(401);
    }

    private int pedirPerfil(String authorization) throws Exception {
        var request = get("/api/v1/usuarios/me");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    @SuppressWarnings("unchecked")
    private Map<String, LocalDateTime> cacheDeBloqueos() {
        // El bean puede venir envuelto en un proxy de @Transactional: los campos están en el objeto real.
        Object servicio = AopTestUtils.getTargetObject(authService);
        return (ConcurrentHashMap<String, LocalDateTime>) ReflectionTestUtils.getField(servicio, "lockoutCache");
    }
}
