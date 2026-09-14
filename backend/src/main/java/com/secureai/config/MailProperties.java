package com.secureai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secureai.mail")
public record MailProperties(
        boolean enabled,
        String from,
        String fromName
) {}
