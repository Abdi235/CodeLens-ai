package com.secureai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secureai.oauth")
public record OAuthProperties(
        String frontendUrl,
        String apiPublicUrl,
        Provider google,
        Provider microsoft
) {
    public record Provider(String clientId, String clientSecret) {
        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }
    }
}
