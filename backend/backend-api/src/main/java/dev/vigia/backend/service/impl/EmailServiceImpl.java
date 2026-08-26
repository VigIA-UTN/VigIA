package dev.vigia.backend.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import dev.vigia.backend.service.EmailService;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender emailSender;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mail.from:no-reply@vigia.dev}")
    private String mailFrom;

    @Override
    public void enviarInvitacionProductor(String to, String token, String establecimientoNombre) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject("Invitación a unirse como Productor en VigIA");
            message.setText("Hola,\n\n"
                    + "Has sido invitado para unirte a VigIA como Productor para el establecimiento: "
                    + establecimientoNombre + ".\n\n"
                    + "Por favor utiliza el siguiente código de invitación en la plataforma para registrarte:\n"
                    + token + "\n\n"
                    + "Este código es válido por 7 días.\n\n"
                    + "Saludos,\nEl equipo de VigIA.");

            emailSender.send(message);
            log.info("Email de invitación enviado a {}", to);
        } catch (Exception e) {
            log.error("Error al enviar email de invitación a {}: {}", to, e.getMessage());
            // No bloqueamos el flujo principal si el envío de correo falla por
            // configuración local
        }
    }

    @Override
    public void enviarInvitacionEmpleado(String to, String token, String establecimientoNombre, String rol) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject("Invitación a unirse como " + rol + " en VigIA");
            message.setText("Hola,\n\n"
                    + "Has sido invitado para unirte a VigIA como " + rol + " en el establecimiento: "
                    + establecimientoNombre + ".\n\n"
                    + "Por favor, haz clic en el siguiente enlace para registrarte:\n"
                    + frontendUrl + "/aceptar-invitacion?token=" + token + "\n\n"
                    + "Este enlace es válido por 7 días.\n\n"
                    + "Saludos,\nEl equipo de VigIA.");

            emailSender.send(message);
            log.info("Email de invitación enviado a {}", to);
        } catch (Exception e) {
            log.error("Error al enviar email de invitación a {}: {}", to, e.getMessage());
        }
    }

    @Override
    public void enviarNuevoEstablecimientoProductor(String to, String establecimientoNombre) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject("Nuevo establecimiento asignado en VigIA");
            message.setText("Hola,\n\n"
                    + "Te informamos que hemos dado de alta el establecimiento \""
                    + establecimientoNombre + "\" en tu cuenta de VigIA.\n\n"
                    + "Ya podés acceder a él desde la plataforma con tu usuario habitual.\n\n"
                    + "Saludos,\nEl equipo de VigIA.");

            emailSender.send(message);
            log.info("Email de nuevo establecimiento enviado a {}", to);
        } catch (Exception e) {
            log.error("Error al enviar email de nuevo establecimiento a {}: {}", to, e.getMessage());
        }
    }

    @Override
    public void enviarInvitacionPendienteUsuarioExistente(String to, String establecimientoNombre, String rol) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject("Nueva invitación pendiente en VigIA");
            message.setText("Hola,\n\n"
                    + "Te han enviado una invitación para unirte como " + rol + " en el establecimiento: "
                    + establecimientoNombre + ".\n\n"
                    + "Por favor iniciá sesión en tu cuenta de VigIA para aceptarla o rechazarla desde la sección 'Mis Invitaciones'.\n\n"
                    + "Saludos,\nEl equipo de VigIA.");

            emailSender.send(message);
            log.info("Email de invitación pendiente enviado a usuario existente {}", to);
        } catch (Exception e) {
            log.error("Error al enviar email de invitación pendiente a {}: {}", to, e.getMessage());
        }
    }
}
