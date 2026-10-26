package dev.vigia.backend.service.impl;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.service.EmailService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender emailSender;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mail.from:no-reply@vigia.dev}")
    private String mailFrom;

    @Value("${app.mail.logo-url}")
    private String customLogoUrl;

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public EmailServiceImpl(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public void enviarInvitacionProductor(String to, String token, String establecimientoNombre) {
        String subject = "Invitación a unirse como Productor en VigIA";
        String link = frontendUrl + "/aceptar-invitacion?token=" + token;

        String plainText = "Hola,\n\n"
                + "Has sido invitado para unirte a VigIA como Productor para el establecimiento: "
                + establecimientoNombre + ".\n\n"
                + "Por favor, haz clic en el siguiente enlace para registrarte:\n"
                + link + "\n\n"
                + "Este enlace es válido por 7 días.\n\n"
                + "Saludos,\nEl equipo de VigIA.";

        String html = loadTemplate("invitacion-productor.html", Map.of(
                "ESTABLECIMIENTO", HtmlUtils.htmlEscape(establecimientoNombre),
                "LINK", HtmlUtils.htmlEscape(link)));

        sendEmail(to, subject, plainText, html);
    }

    @Override
    public void enviarInvitacionEmpleado(String to, String token, String establecimientoNombre, String rol) {
        String subject = "Invitación a unirse como " + rol + " en VigIA";
        String link = frontendUrl + "/aceptar-invitacion?token=" + token;

        String plainText = "Hola,\n\n"
                + "Has sido invitado para unirte a VigIA como " + rol + " en el establecimiento: "
                + establecimientoNombre + ".\n\n"
                + "Por favor, haz clic en el siguiente enlace para registrarte:\n"
                + link + "\n\n"
                + "Este enlace es válido por 7 días.\n\n"
                + "Saludos,\nEl equipo de VigIA.";

        String html = loadTemplate("invitacion-empleado.html", Map.of(
                "ESTABLECIMIENTO", HtmlUtils.htmlEscape(establecimientoNombre),
                "ROL", HtmlUtils.htmlEscape(rol),
                "LINK", HtmlUtils.htmlEscape(link)));

        sendEmail(to, subject, plainText, html);
    }

    @Override
    public void enviarNuevoEstablecimientoProductor(String to, String establecimientoNombre) {
        String subject = "Nuevo establecimiento asignado en VigIA";
        String link = frontendUrl + "/establecimientos";

        String plainText = "Hola,\n\n"
                + "Te informamos que hemos dado de alta el establecimiento \""
                + establecimientoNombre + "\" en tu cuenta de VigIA.\n\n"
                + "Ya podés acceder a él desde la plataforma con tu usuario habitual.\n\n"
                + "Saludos,\nEl equipo de VigIA.";

        String html = loadTemplate("nuevo-establecimiento.html", Map.of(
                "ESTABLECIMIENTO", HtmlUtils.htmlEscape(establecimientoNombre),
                "LINK", HtmlUtils.htmlEscape(link)));

        sendEmail(to, subject, plainText, html);
    }

    @Override
    public void enviarInvitacionPendienteUsuarioExistente(String to, String establecimientoNombre, String rol) {
        String subject = "Nueva invitación pendiente en VigIA";
        String link = frontendUrl + "/invitaciones";

        String plainText = "Hola,\n\n"
                + "Te han enviado una invitación para unirte como " + rol + " en el establecimiento: "
                + establecimientoNombre + ".\n\n"
                + "Por favor iniciá sesión en tu cuenta de VigIA para aceptarla o rechazarla desde la sección 'Mis Invitaciones'.\n\n"
                + "Saludos,\nEl equipo de VigIA.";

        String html = loadTemplate("invitacion-pendiente.html", Map.of(
                "ESTABLECIMIENTO", HtmlUtils.htmlEscape(establecimientoNombre),
                "ROL", HtmlUtils.htmlEscape(rol),
                "LINK", HtmlUtils.htmlEscape(link)));

        sendEmail(to, subject, plainText, html);
    }

    @Override
    public void enviarAlerta(String to, String establecimientoNombre, Alerta alerta) {
        dev.vigia.backend.model.SeveridadAlerta severidad = alerta.getSeveridad() != null
                ? alerta.getSeveridad()
                : dev.vigia.backend.model.SeveridadAlerta.MEDIA;

        String colorSeveridad;
        String bgSeveridad;
        switch (severidad) {
            case ALTA -> {
                colorSeveridad = "#DC2626";
                bgSeveridad = "#FEE2E2";
            }
            case MEDIA -> {
                colorSeveridad = "#EA580C";
                bgSeveridad = "#FFEDD5";
            }
            case BAJA -> {
                colorSeveridad = "#2563EB";
                bgSeveridad = "#EFF6FF";
            }
            default -> {
                colorSeveridad = "#1E3A5F";
                bgSeveridad = "#F1F5F9";
            }
        }

        String tipoAlerta = alerta.getTipo() != null && !alerta.getTipo().isBlank()
                ? alerta.getTipo()
                : (alerta.getDisparador() != null ? alerta.getDisparador().name().replace('_', ' ') : "Alerta general");

        String fechaHora = alerta.getTsGeneracion() != null
                ? alerta.getTsGeneracion().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "Reciente";

        String accionRecomendada = alerta.getAccionRecomendada() != null && !alerta.getAccionRecomendada().isBlank()
                ? alerta.getAccionRecomendada()
                : "Verifica el estado en el mapa y tablero de control.";

        String subject = "Alerta " + severidad.name() + " en VigIA: " + tipoAlerta;
        String link = frontendUrl + "/alertas";

        String plainText = "Hola,\n\n"
                + "Se ha detectado una situación que requiere tu atención en el establecimiento: "
                + establecimientoNombre + ".\n\n"
                + "Tipo de evento: " + tipoAlerta + "\n"
                + "Severidad: " + severidad.name() + "\n"
                + "Fecha: " + fechaHora + "\n"
                + "Acción sugerida: " + accionRecomendada + "\n\n"
                + "Por favor iniciá sesión en tu cuenta de VigIA para ver la alerta:\n"
                + link + "\n\n"
                + "Saludos,\nEl equipo de VigIA.";

        Map<String, String> variables = new HashMap<>();
        variables.put("ESTABLECIMIENTO", HtmlUtils.htmlEscape(establecimientoNombre));
        variables.put("SEVERIDAD", HtmlUtils.htmlEscape(severidad.name()));
        variables.put("COLOR_SEVERIDAD", colorSeveridad);
        variables.put("BG_SEVERIDAD", bgSeveridad);
        variables.put("TIPO", HtmlUtils.htmlEscape(tipoAlerta));
        variables.put("FECHA", HtmlUtils.htmlEscape(fechaHora));
        variables.put("ACCION_RECOMENDADA", HtmlUtils.htmlEscape(accionRecomendada));
        variables.put("LINK", HtmlUtils.htmlEscape(link));

        String html = loadTemplate("alerta.html", variables);

        sendEmail(to, subject, plainText, html);
    }

    private void sendEmail(String to, String subject, String plainText, String html) {
        try {
            MimeMessage mimeMessage = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText, html);

            emailSender.send(mimeMessage);
            log.info("Email HTML enviado exitosamente a {}", to);
        } catch (Exception e) {
            log.error("Error al enviar email a {}: {}", to, e.getMessage());
        }
    }

    private String loadTemplate(String templateName, Map<String, String> variables) {
        String template = templateCache.computeIfAbsent(templateName, name -> {
            try {
                ClassPathResource resource = new ClassPathResource("templates/email/" + name);
                return resource.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Error al cargar la plantilla de email {}: {}", name, e.getMessage());
                throw new IllegalStateException("No se pudo cargar la plantilla de email: " + name, e);
            }
        });

        Map<String, String> allVars = new HashMap<>(variables);
        allVars.put("LOGO_URL", getLogoUrl());

        for (Map.Entry<String, String> entry : allVars.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue() != null ? entry.getValue() : "");
        }
        return template;
    }

    private String getLogoUrl() {
        if (customLogoUrl != null && !customLogoUrl.isBlank()) {
            return customLogoUrl.trim();
        }
        return frontendUrl + "/logo.png";
    }
}