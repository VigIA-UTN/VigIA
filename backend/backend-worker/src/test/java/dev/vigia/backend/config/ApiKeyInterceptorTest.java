package dev.vigia.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyInterceptor — Tests Unitarios (autenticación del webhook TTN)")
class ApiKeyInterceptorTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private ApiKeyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ApiKeyInterceptor();
    }

    @SuppressWarnings("null")
    private void configurarApiKey(String apiKey) {
        ReflectionTestUtils.setField(interceptor, "configuredApiKey", apiKey);
    }

    @Test
    @DisplayName("Sin API Key configurada, falla al arrancar")
    void sinApiKeyConfigurada_fallaAlArrancar() {
        configurarApiKey("");

        assertThatThrownBy(() -> interceptor.validarConfiguracion())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Con API Key configurada y Bearer token correcto, permite la solicitud")
    void conApiKeyCorrecta_permite() throws Exception {
        configurarApiKey("secreto-ttn");
        when(request.getHeader("Authorization")).thenReturn("Bearer secreto-ttn");

        boolean permitido = interceptor.preHandle(request, response, new Object());

        assertThat(permitido).isTrue();
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("Con API Key configurada y sin header Authorization, responde 401")
    void conApiKeyConfigurada_sinHeader_rechaza() throws Exception {
        configurarApiKey("secreto-ttn");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        boolean permitido = interceptor.preHandle(request, response, new Object());

        assertThat(permitido).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("Con API Key configurada y token incorrecto, responde 401")
    void conApiKeyConfigurada_tokenIncorrecto_rechaza() throws Exception {
        configurarApiKey("secreto-ttn");
        when(request.getHeader("Authorization")).thenReturn("Bearer otro-token");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        boolean permitido = interceptor.preHandle(request, response, new Object());

        assertThat(permitido).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("Con API Key configurada y header sin prefijo 'Bearer ', responde 401")
    void conApiKeyConfigurada_sinPrefijoBearer_rechaza() throws Exception {
        configurarApiKey("secreto-ttn");
        when(request.getHeader("Authorization")).thenReturn("secreto-ttn");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        boolean permitido = interceptor.preHandle(request, response, new Object());

        assertThat(permitido).isFalse();
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
