package dev.vigia.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class RootRedirectController {

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @SuppressWarnings("null")
    @GetMapping({ "/", "/login", "/login/**" })
    public RedirectView redirectToFrontend() {
        String frontendUrl = redirectUri.replaceAll("/oauth2/callback/?$", "/login");
        return new RedirectView(frontendUrl);
    }
}
