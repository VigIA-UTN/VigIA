package dev.vigia.backend.service;

import dev.vigia.backend.dto.ValidateCodeRequest;
import dev.vigia.backend.dto.ValidateCodeResponse;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.security.CustomUserDetailsService;
import dev.vigia.backend.security.JwtUtil;
import dev.vigia.backend.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private InvitacionRepository invitacionRepository;
    @Mock
    private EstablecimientoRepository establecimientoRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private CustomUserDetailsService userDetailsService;

    @InjectMocks
    private AuthServiceImpl authService;

    private Invitacion invitacionValida;
    private ValidateCodeRequest request;

    @BeforeEach
    void setUp() {
        request = new ValidateCodeRequest();
        request.setToken("valid-token");
        request.setEmail("empleado@test.com");

        Establecimiento est = new Establecimiento();
        est.setNombre("Estancia La Paz");

        invitacionValida = new Invitacion();
        invitacionValida.setToken("valid-token");
        invitacionValida.setEmailDestino("empleado@test.com");
        invitacionValida.setRol(Rol.CAMPERO);
        invitacionValida.setTipoInvitacion(TipoInvitacion.EMPLEADO);
        invitacionValida.setEstadoInvitacion(EstadoInvitacion.PENDIENTE);
        invitacionValida.setFechaExpiracion(LocalDateTime.now().plusDays(1));
        invitacionValida.setEstablecimiento(est);
    }

    @Test
    @DisplayName("Debe validar código de empleado con éxito")
    void validateCode_Success() {
        when(invitacionRepository.findByToken("valid-token")).thenReturn(Optional.of(invitacionValida));

        ValidateCodeResponse response = authService.validateCode(request);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("empleado@test.com");
        assertThat(response.getRol()).isEqualTo(Rol.CAMPERO.name());
        assertThat(response.getEstablecimiento()).isEqualTo("Estancia La Paz");
    }

    @Test
    @DisplayName("Debe rechazar token inválido")
    void validateCode_ThrowsWhenTokenInvalido() {
        when(invitacionRepository.findByToken("invalid-token")).thenReturn(Optional.empty());

        ValidateCodeRequest req = new ValidateCodeRequest();
        req.setToken("invalid-token");

        assertThatThrownBy(() -> authService.validateCode(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El código de invitación es inválido");
    }

    @Test
    @DisplayName("Debe rechazar token reutilizado o cancelado")
    void validateCode_ThrowsWhenTokenUsado() {
        invitacionValida.setEstadoInvitacion(EstadoInvitacion.USADA);
        when(invitacionRepository.findByToken("valid-token")).thenReturn(Optional.of(invitacionValida));

        assertThatThrownBy(() -> authService.validateCode(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La invitación ya ha sido utilizada o cancelada");
    }

    @Test
    @DisplayName("Debe rechazar token expirado y actualizar estado a EXPIRADA")
    @SuppressWarnings("null")
    void validateCode_ThrowsWhenTokenExpirado() {
        invitacionValida.setFechaExpiracion(LocalDateTime.now().minusDays(1));
        when(invitacionRepository.findByToken("valid-token")).thenReturn(Optional.of(invitacionValida));

        assertThatThrownBy(() -> authService.validateCode(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El código de invitación ha expirado");

        verify(invitacionRepository, times(1)).save(invitacionValida);
        assertThat(invitacionValida.getEstadoInvitacion()).isEqualTo(EstadoInvitacion.EXPIRADA);
    }

    @Test
    @DisplayName("Debe validar código de productor con éxito")
    void validateCode_Productor_Success() {
        Invitacion invitacionProductor = new Invitacion();
        invitacionProductor.setToken("valid-token-prod");
        invitacionProductor.setEmailDestino("productor@test.com");
        invitacionProductor.setRol(Rol.PRODUCTOR);
        invitacionProductor.setTipoInvitacion(TipoInvitacion.PRODUCTOR);
        invitacionProductor.setEstadoInvitacion(EstadoInvitacion.PENDIENTE);
        invitacionProductor.setFechaExpiracion(LocalDateTime.now().plusDays(1));
        
        when(invitacionRepository.findByToken("valid-token-prod")).thenReturn(Optional.of(invitacionProductor));

        ValidateCodeRequest req = new ValidateCodeRequest();
        req.setToken("valid-token-prod");
        req.setEmail("productor@test.com");

        ValidateCodeResponse response = authService.validateCode(req);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("productor@test.com");
        assertThat(response.getRol()).isEqualTo(Rol.PRODUCTOR.name());
    }

    @Test
    @DisplayName("Debe rechazar token de productor si el email no coincide")
    void validateCode_Productor_ThrowsWhenEmailMismatch() {
        Invitacion invitacionProductor = new Invitacion();
        invitacionProductor.setToken("valid-token-prod");
        invitacionProductor.setEmailDestino("productor@test.com");
        invitacionProductor.setRol(Rol.PRODUCTOR);
        invitacionProductor.setTipoInvitacion(TipoInvitacion.PRODUCTOR);
        invitacionProductor.setEstadoInvitacion(EstadoInvitacion.PENDIENTE);
        invitacionProductor.setFechaExpiracion(LocalDateTime.now().plusDays(1));
        
        when(invitacionRepository.findByToken("valid-token-prod")).thenReturn(Optional.of(invitacionProductor));

        ValidateCodeRequest req = new ValidateCodeRequest();
        req.setToken("valid-token-prod");
        req.setEmail("otro@test.com");

        assertThatThrownBy(() -> authService.validateCode(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El email no coincide con el de la invitación");
    }

    @Test
    @DisplayName("Debe rechazar token de productor si no provee email")
    void validateCode_Productor_ThrowsWhenEmailNull() {
        Invitacion invitacionProductor = new Invitacion();
        invitacionProductor.setToken("valid-token-prod");
        invitacionProductor.setEmailDestino("productor@test.com");
        invitacionProductor.setRol(Rol.PRODUCTOR);
        invitacionProductor.setTipoInvitacion(TipoInvitacion.PRODUCTOR);
        invitacionProductor.setEstadoInvitacion(EstadoInvitacion.PENDIENTE);
        invitacionProductor.setFechaExpiracion(LocalDateTime.now().plusDays(1));
        
        when(invitacionRepository.findByToken("valid-token-prod")).thenReturn(Optional.of(invitacionProductor));

        ValidateCodeRequest req = new ValidateCodeRequest();
        req.setToken("valid-token-prod");
        req.setEmail(null);

        assertThatThrownBy(() -> authService.validateCode(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El email es requerido para el registro con código");
    }
}
