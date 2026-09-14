package com.secureai.controller;

import com.secureai.dto.AuthResponse;
import com.secureai.service.OAuthService;
import com.secureai.service.WelcomeEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oauthService;
    private final WelcomeEmailService welcomeEmailService;

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        var status = oauthService.providerStatus(welcomeEmailService.isConfigured());
        return Map.of(
                "google", status.google(),
                "microsoft", status.microsoft(),
                "outlook", status.microsoft(),
                "mailEnabled", status.mailEnabled()
        );
    }

    @GetMapping("/{provider}/start")
    public RedirectView start(@PathVariable String provider) {
        try {
            String url = oauthService.buildAuthorizationUrl(provider);
            RedirectView view = new RedirectView(url);
            view.setStatusCode(HttpStatus.FOUND);
            return view;
        } catch (IllegalArgumentException ex) {
            RedirectView view = new RedirectView(oauthService.frontendErrorRedirect(ex.getMessage()));
            view.setStatusCode(HttpStatus.FOUND);
            return view;
        }
    }

    @GetMapping("/{provider}/callback")
    public RedirectView callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription
    ) {
        try {
            if (error != null) {
                String message = errorDescription != null ? errorDescription : error;
                RedirectView view = new RedirectView(oauthService.frontendErrorRedirect(message));
                view.setStatusCode(HttpStatus.FOUND);
                return view;
            }
            String redirect = oauthService.handleCallback(provider, code, state);
            RedirectView view = new RedirectView(redirect);
            view.setStatusCode(HttpStatus.FOUND);
            return view;
        } catch (Exception ex) {
            RedirectView view = new RedirectView(oauthService.frontendErrorRedirect(
                    ex.getMessage() != null ? ex.getMessage() : "OAuth sign-in failed"
            ));
            view.setStatusCode(HttpStatus.FOUND);
            return view;
        }
    }

    @PostMapping("/exchange")
    public AuthResponse exchange(@RequestBody Map<String, String> body) {
        String ticket = body.get("code");
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalArgumentException("Missing OAuth exchange code");
        }
        return oauthService.exchangeTicket(ticket);
    }
}
