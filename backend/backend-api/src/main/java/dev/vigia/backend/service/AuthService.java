package dev.vigia.backend.service;

import dev.vigia.backend.dto.AuthResponse;
import dev.vigia.backend.dto.RegisterRequest;
import dev.vigia.backend.dto.ValidateCodeRequest;
import dev.vigia.backend.dto.ValidateCodeResponse;

public interface AuthService {
    ValidateCodeResponse validateCode(ValidateCodeRequest request);
    AuthResponse register(RegisterRequest request);
    AuthResponse processOAuth2User(String email, String nombre, String apellido, String provider);
    boolean isAccountLocked(String email);
    void handleFailedLogin(String email);
    void resetFailedLogin(String email);
    void clearCacheForTests();
}
