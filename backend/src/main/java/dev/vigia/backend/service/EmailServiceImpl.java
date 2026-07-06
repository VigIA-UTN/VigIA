package dev.vigia.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender emailSender;

    @Override
    public void enviarInvitacionProductor(String to, String token, String establecimientoNombre) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("no-reply@vigia.dev");
            message.setTo(to);
            message.setSubject("Invitación a unirse como Productor en VigIA");
            message.setText("Hola,\n\n"
                    + "Has sido invitado para unirte a VigIA como Productor para el establecimiento: " + establecimientoNombre + ".\n\n"
                    + "Por favor utiliza el siguiente código de invitación en la plataforma para registrarte:\n"
                    + token + "\n\n"
                    + "Este código es válido por 7 días.\n\n"
                    + "Saludos,\nEl equipo de VigIA.");
            
            emailSender.send(message);
            log.info("Email de invitación enviado a {}", to);
        } catch (Exception e) {
            log.error("Error al enviar email de invitación a {}: {}", to, e.getMessage());
            // No bloqueamos el flujo principal si el envío de correo falla por configuración local
        }
    }
}
