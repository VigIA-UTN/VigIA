package dev.vigia.backend.service;

import dev.vigia.backend.dto.AuthResponse;
import dev.vigia.backend.dto.RegisterRequest;
import dev.vigia.backend.model.EstadoInvitacion;
import dev.vigia.backend.model.Invitacion;
import dev.vigia.backend.model.TipoInvitacion;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.security.CustomUserDetailsService;
import dev.vigia.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final InvitacionRepository invitacionRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    // Caché en memoria para evitar guardar en BD intentos fallidos (MVP)
    private final ConcurrentHashMap<String, Integer> failedLoginAttemptsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lockoutCache = new ConcurrentHashMap<>();

    public AuthResponse register(RegisterRequest request) {
        Invitacion invitacion = invitacionRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("El código de invitación es inválido."));

        if (invitacion.getEstadoInvitacion() != EstadoInvitacion.PENDIENTE) {
            throw new IllegalArgumentException("La invitación ya ha sido utilizada o cancelada.");
        }

        if (invitacion.getFechaExpiracion() != null && invitacion.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            invitacion.setEstadoInvitacion(EstadoInvitacion.EXPIRADA);
            invitacionRepository.save(invitacion);
            throw new IllegalArgumentException("El código de invitación ha expirado.");
        }

        String emailToUse;
        if (TipoInvitacion.PRODUCTOR.equals(invitacion.getTipoInvitacion())) {
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                throw new IllegalArgumentException("El email es requerido para el registro con código.");
            }
            if (!invitacion.getEmailDestino().equalsIgnoreCase(request.getEmail())) {
                throw new IllegalArgumentException("El email no coincide con el de la invitación.");
            }
            emailToUse = request.getEmail();
        } else {
            emailToUse = invitacion.getEmailDestino();
        }

        validatePassword(request.getPassword());

        if (usuarioRepository.findByEmail(emailToUse).isPresent()) {
            throw new IllegalArgumentException("El email ya se encuentra registrado.");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre());
        usuario.setApellido(request.getApellido());
        usuario.setEmail(emailToUse);
        usuario.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        usuario.setRol(invitacion.getRol());
        usuario.setActivo(true);
        usuario.setFechaAlta(LocalDate.now());

        Usuario savedUser = usuarioRepository.save(usuario);

        if (invitacion.getEstablecimiento() != null) {
            var establecimiento = invitacion.getEstablecimiento();
            establecimiento.getUsuarios().add(savedUser);
            establecimientoRepository.save(establecimiento);
        }

        invitacion.setEstadoInvitacion(EstadoInvitacion.USADA);
        invitacionRepository.save(invitacion);

        var userDetails = userDetailsService.loadUserByUsername(savedUser.getEmail());
        String token = jwtUtil.generateToken(userDetails);

        String roleStr = savedUser.getRol() != null ? savedUser.getRol().name() : "";

        return AuthResponse.builder()
                .token(token)
                .email(savedUser.getEmail())
                .rol(roleStr)
                .build();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 32) {
            throw new IllegalArgumentException("La contraseña debe tener entre 8 y 32 caracteres.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("La contraseña debe tener al menos una letra mayúscula.");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("La contraseña debe tener al menos una letra minúscula.");
        }
        if (!password.matches(".*[0-9\\W_].*")) {
            throw new IllegalArgumentException("La contraseña debe tener al menos un número o carácter especial.");
        }
    }

    public boolean isAccountLocked(String email) {
        LocalDateTime lockTime = lockoutCache.get(email);
        if (lockTime != null) {
            if (lockTime.isAfter(LocalDateTime.now())) {
                return true; // Sigue bloqueado
            } else {
                // El bloqueo expiró, limpiar caché
                lockoutCache.remove(email);
                failedLoginAttemptsCache.remove(email);
                return false;
            }
        }
        return false;
    }

    public void handleFailedLogin(String email) {
        if (!isAccountLocked(email)) {
            int attempts = failedLoginAttemptsCache.getOrDefault(email, 0) + 1;
            failedLoginAttemptsCache.put(email, attempts);
            if (attempts >= 5) {
                lockoutCache.put(email, LocalDateTime.now().plusMinutes(30));
            }
        }
    }

    public void resetFailedLogin(String email) {
        failedLoginAttemptsCache.remove(email);
        lockoutCache.remove(email);
    }

    public void clearCacheForTests() {
        failedLoginAttemptsCache.clear();
        lockoutCache.clear();
    }
}
