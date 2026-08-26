package dev.vigia.backend.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import dev.vigia.backend.service.EmailService;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.sendgrid.api-key:}")
    private String sendgridApiKey;

    @Value("${app.sendgrid.from-email:}")
    private String sendgridFromEmail;

    private final RestClient restClient = RestClient.create();

    @SuppressWarnings("null")
    private void sendEmailHttp(String to, String subject, String text) {
        if (sendgridApiKey == null || sendgridApiKey.isBlank() || sendgridFromEmail == null
                || sendgridFromEmail.isBlank()) {
            log.warn(
                    "SENDGRID_API_KEY o SENDGRID_FROM_EMAIL no configurada. Simulando envío de mail a {}:\nAsunto: {}\nTexto: {}",
                    to, subject, text);
            return;
        }

        try {
            Map<String, Object> request = Map.of(
                    "personalizations", List.of(
                            Map.of(
                                    "to", List.of(Map.of("email", to)),
                                    "subject", subject)),
                    "from", Map.of("email", sendgridFromEmail, "name", "VigIA"),
                    "content", List.of(
                            Map.of(
                                    "type", "text/plain",
                                    "value", text)));

            restClient.post()
                    .uri("https://api.sendgrid.com/v3/mail/send")
                    .header("Authorization", "Bearer " + sendgridApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Email enviado a {} mediante SendGrid API", to);
        } catch (Exception e) {
            log.error("Error al enviar email HTTP a {}: {}", to, e.getMessage());
        }
    }

    @Override
    public void enviarInvitacionProductor(String to, String token, String establecimientoNombre) {
        String text = "Hola,\n\n"
                + "Has sido invitado para unirte a VigIA como Productor para el establecimiento: "
                + establecimientoNombre + ".\n\n"
                + "Por favor utiliza el siguiente código de invitación en la plataforma para registrarte:\n"
                + token + "\n\n"
                + "Este código es válido por 7 días.\n\n"
                + "Saludos,\nEl equipo de VigIA.";

        sendEmailHttp(to, "Invitación a unirse como Productor en VigIA", text);
    }

    @Override
    public void enviarInvitacionEmpleado(String to, String token, String establecimientoNombre, String rol) {
        String text = "Hola,\n\n"
                + "Has sido invitado para unirte a VigIA como " + rol + " en el establecimiento: "
                + establecimientoNombre + ".\n\n"
                + "Por favor, haz clic en el siguiente enlace para registrarte:\n"
                + frontendUrl + "/aceptar-invitacion?token=" + token + "\n\n"
                + "Este enlace es válido por 7 días.\n\n"
                + "Saludos,\nEl equipo de VigIA.";

        sendEmailHttp(to, "Invitación a unirse como " + rol + " en VigIA", text);
    }

    @Override
    public void enviarNuevoEstablecimientoProductor(String to, String establecimientoNombre) {
        String text = "Hola,\n\n"
                + "Te informamos que hemos dado de alta el establecimiento \""
                + establecimientoNombre + "\" en tu cuenta de VigIA.\n\n"
                + "Ya podés acceder a él desde la plataforma con tu usuario habitual.\n\n"
                + "Saludos,\nEl equipo de VigIA.";

        sendEmailHttp(to, "Nuevo establecimiento asignado en VigIA", text);
    }

    @Override
    public void enviarInvitacionPendienteUsuarioExistente(String to, String establecimientoNombre, String rol) {
        String text = "Hola,\n\n"
                + "Te han enviado una invitación para unirte como " + rol + " en el establecimiento: "
                + establecimientoNombre + ".\n\n"
                + "Por favor iniciá sesión en tu cuenta de VigIA para aceptarla o rechazarla desde la sección 'Mis Invitaciones'.\n\n"
                + "Saludos,\nEl equipo de VigIA.";

        sendEmailHttp(to, "Nueva invitación pendiente en VigIA", text);
    }
}
