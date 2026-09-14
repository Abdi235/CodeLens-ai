package com.secureai.service;

import com.secureai.config.OAuthProperties;
import com.secureai.dto.AuthResponse;
import com.secureai.model.AuthProvider;
import com.secureai.model.Role;
import com.secureai.model.User;
import com.secureai.repository.UserRepository;
import com.secureai.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthService {

    private static final long STATE_TTL_SECONDS = 600;
    private static final long EXCHANGE_TTL_SECONDS = 120;

    private final OAuthProperties oauthProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final WelcomeEmailService welcomeEmailService;
    private final RestClient restClient = RestClient.create();
    private final SecureRandom secureRandom = new SecureRandom();

    private final ConcurrentHashMap<String, PendingState> pendingStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExchangeTicket> exchangeTickets = new ConcurrentHashMap<>();

    public record ProviderStatus(boolean google, boolean microsoft, boolean mailEnabled) {}

    public ProviderStatus providerStatus(boolean mailEnabled) {
        return new ProviderStatus(
                oauthProperties.google() != null && oauthProperties.google().isConfigured(),
                oauthProperties.microsoft() != null && oauthProperties.microsoft().isConfigured(),
                mailEnabled
        );
    }

    public String buildAuthorizationUrl(String providerKey) {
        AuthProvider provider = parseProvider(providerKey);
        OAuthProperties.Provider cfg = configFor(provider);
        if (!cfg.isConfigured()) {
            throw new IllegalArgumentException(provider.name() + " sign-in is not configured");
        }
        if (blank(oauthProperties.apiPublicUrl()) || blank(oauthProperties.frontendUrl())) {
            throw new IllegalArgumentException("OAuth redirect URLs are not configured");
        }

        purgeExpired();
        String state = randomToken();
        pendingStates.put(state, new PendingState(provider, Instant.now().plusSeconds(STATE_TTL_SECONDS)));

        String redirectUri = callbackUrl(provider);
        return switch (provider) {
            case GOOGLE -> UriComponentsBuilder
                    .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                    .queryParam("client_id", cfg.clientId())
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("response_type", "code")
                    .queryParam("scope", "openid email profile")
                    .queryParam("access_type", "online")
                    .queryParam("prompt", "select_account")
                    .queryParam("state", state)
                    .build(true)
                    .toUriString();
            case MICROSOFT -> UriComponentsBuilder
                    .fromUriString("https://login.microsoftonline.com/common/oauth2/v2.0/authorize")
                    .queryParam("client_id", cfg.clientId())
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("response_type", "code")
                    .queryParam("scope", "openid email profile User.Read offline_access")
                    .queryParam("response_mode", "query")
                    .queryParam("prompt", "select_account")
                    .queryParam("state", state)
                    .build(true)
                    .toUriString();
            default -> throw new IllegalArgumentException("Unsupported provider");
        };
    }

    @Transactional
    public String handleCallback(String providerKey, String code, String state) {
        if (blank(code) || blank(state)) {
            throw new IllegalArgumentException("Missing OAuth code or state");
        }
        purgeExpired();
        PendingState pending = pendingStates.remove(state);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("OAuth state expired — try signing in again");
        }
        AuthProvider provider = parseProvider(providerKey);
        if (pending.provider() != provider) {
            throw new IllegalArgumentException("OAuth provider mismatch");
        }

        OAuthProfile profile = exchangeAndFetchProfile(provider, code);
        if (blank(profile.email())) {
            throw new IllegalArgumentException("Your email provider did not return an email address");
        }

        UserOutcome outcome = upsertOAuthUser(provider, profile);
        if (outcome.created()) {
            welcomeEmailService.sendWelcomeEmail(outcome.user());
        }

        AuthResponse auth = buildAuthResponse(outcome.user());
        String ticket = randomToken();
        exchangeTickets.put(ticket, new ExchangeTicket(auth, Instant.now().plusSeconds(EXCHANGE_TTL_SECONDS)));

        return UriComponentsBuilder
                .fromUriString(trimSlash(oauthProperties.frontendUrl()) + "/oauth/callback")
                .queryParam("code", ticket)
                .queryParam("welcome", outcome.created() ? "1" : "0")
                .build(true)
                .toUriString();
    }

    public AuthResponse exchangeTicket(String ticket) {
        purgeExpired();
        ExchangeTicket entry = exchangeTickets.remove(ticket);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Sign-in session expired — try again");
        }
        return entry.auth();
    }

    public String frontendErrorRedirect(String message) {
        String base = blank(oauthProperties.frontendUrl())
                ? "http://localhost:3000"
                : trimSlash(oauthProperties.frontendUrl());
        return UriComponentsBuilder
                .fromUriString(base + "/login")
                .queryParam("oauth_error", message == null ? "OAuth sign-in failed" : message)
                .build()
                .encode()
                .toUriString();
    }

    private OAuthProfile exchangeAndFetchProfile(AuthProvider provider, String code) {
        return switch (provider) {
            case GOOGLE -> fetchGoogleProfile(code);
            case MICROSOFT -> fetchMicrosoftProfile(code);
            default -> throw new IllegalArgumentException("Unsupported provider");
        };
    }

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private OAuthProfile fetchGoogleProfile(String code) {
        OAuthProperties.Provider cfg = oauthProperties.google();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", cfg.clientId());
        form.add("client_secret", cfg.clientSecret());
        form.add("redirect_uri", callbackUrl(AuthProvider.GOOGLE));
        form.add("grant_type", "authorization_code");

        Map<String, Object> tokenJson = restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MAP_TYPE);
        String accessToken = requiredText(tokenJson, "access_token");

        Map<String, Object> profile = restClient.get()
                .uri("https://www.googleapis.com/oauth2/v3/userinfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(MAP_TYPE);

        return new OAuthProfile(
                text(profile, "sub"),
                text(profile, "email"),
                text(profile, "name")
        );
    }

    private OAuthProfile fetchMicrosoftProfile(String code) {
        OAuthProperties.Provider cfg = oauthProperties.microsoft();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", cfg.clientId());
        form.add("client_secret", cfg.clientSecret());
        form.add("redirect_uri", callbackUrl(AuthProvider.MICROSOFT));
        form.add("grant_type", "authorization_code");
        form.add("scope", "openid email profile User.Read offline_access");

        Map<String, Object> tokenJson = restClient.post()
                .uri("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MAP_TYPE);
        String accessToken = requiredText(tokenJson, "access_token");

        Map<String, Object> profile = restClient.get()
                .uri("https://graph.microsoft.com/v1.0/me")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(MAP_TYPE);

        String email = firstNonBlank(
                text(profile, "mail"),
                text(profile, "userPrincipalName")
        );
        return new OAuthProfile(
                text(profile, "id"),
                email,
                text(profile, "displayName")
        );
    }

    private UserOutcome upsertOAuthUser(AuthProvider provider, OAuthProfile profile) {
        String email = profile.email().trim().toLowerCase();
        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getAuthProvider() == AuthProvider.LOCAL || user.getAuthProvider() == null) {
                user.setAuthProvider(provider);
            }
            if (blank(user.getOauthSubject())) {
                user.setOauthSubject(profile.subject());
            }
            return new UserOutcome(userRepository.save(user), false);
        }

        User created = User.builder()
                .email(email)
                .username(null)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.USER)
                .authProvider(provider)
                .oauthSubject(profile.subject())
                .build();
        return new UserOutcome(userRepository.save(created), true);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(
                user.getLoginIdentity(),
                Map.of("role", user.getRole().name(), "uid", user.getId())
        );
        return new AuthResponse(token, user.getEmail(), user.getUsername(), user.getRole());
    }

    private OAuthProperties.Provider configFor(AuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> oauthProperties.google() != null ? oauthProperties.google() : new OAuthProperties.Provider("", "");
            case MICROSOFT -> oauthProperties.microsoft() != null ? oauthProperties.microsoft() : new OAuthProperties.Provider("", "");
            default -> new OAuthProperties.Provider("", "");
        };
    }

    private String callbackUrl(AuthProvider provider) {
        String base = trimSlash(oauthProperties.apiPublicUrl());
        String path = switch (provider) {
            case GOOGLE -> "/api/auth/oauth/google/callback";
            case MICROSOFT -> "/api/auth/oauth/microsoft/callback";
            default -> throw new IllegalArgumentException("Unsupported provider");
        };
        return base + path;
    }

    private AuthProvider parseProvider(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Provider is required");
        }
        return switch (key.toLowerCase()) {
            case "google", "gmail" -> AuthProvider.GOOGLE;
            case "microsoft", "outlook", "hotmail", "live" -> AuthProvider.MICROSOFT;
            default -> throw new IllegalArgumentException("Unsupported email service: " + key);
        };
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        pendingStates.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        exchangeTickets.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String trimSlash(String value) {
        if (value == null) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String text(Map<String, Object> node, String field) {
        if (node == null || node.get(field) == null) {
            return null;
        }
        String value = String.valueOf(node.get(field));
        return blank(value) || "null".equals(value) ? null : value;
    }

    private static String requiredText(Map<String, Object> node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException("OAuth token response missing " + field);
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return null;
    }

    private record PendingState(AuthProvider provider, Instant expiresAt) {}
    private record ExchangeTicket(AuthResponse auth, Instant expiresAt) {}
    private record OAuthProfile(String subject, String email, String displayName) {}
    private record UserOutcome(User user, boolean created) {}
}
