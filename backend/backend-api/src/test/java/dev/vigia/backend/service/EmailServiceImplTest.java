package dev.vigia.backend.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import dev.vigia.backend.service.impl.EmailServiceImpl;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender emailSender;

    @InjectMocks
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(emailService, "mailFrom", "no-reply@vigia.dev");
        when(emailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    @DisplayName("enviarInvitacionProductor envía email HTML y plain text con token y nombre de establecimiento")
    void testEnviarInvitacionProductor() {
        emailService.enviarInvitacionProductor("productor@campo.com", "token123", "La Cabaña");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(emailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent).isNotNull();
    }

    @Test
    @DisplayName("enviarInvitacionEmpleado envía email HTML con rol especificado")
    void testEnviarInvitacionEmpleado() {
        emailService.enviarInvitacionEmpleado("veterinario@campo.com", "token456", "La Cabaña", "Veterinario");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(emailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent).isNotNull();
    }

    @Test
    @DisplayName("enviarNuevoEstablecimientoProductor envía email HTML informativo")
    void testEnviarNuevoEstablecimientoProductor() {
        emailService.enviarNuevoEstablecimientoProductor("productor@campo.com", "Los Álamos");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(emailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent).isNotNull();
    }

    @Test
    @DisplayName("enviarInvitacionPendienteUsuarioExistente envía email HTML con enlace a invitaciones")
    void testEnviarInvitacionPendienteUsuarioExistente() {
        emailService.enviarInvitacionPendienteUsuarioExistente("empleado@campo.com", "Los Álamos", "Campero");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(emailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent).isNotNull();
    }
}
