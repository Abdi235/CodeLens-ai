package com.secureai.validation;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class RepositoryUrlValidator {

    private static final Pattern GIT_SSH = Pattern.compile("^git@[\\w.-]+:.+");
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "file");

    public void validate(String repository) {
        if (repository == null || repository.isBlank()) {
            throw new IllegalArgumentException("Repository URL or path is required");
        }
        String trimmed = repository.trim();

        if ("samples".equalsIgnoreCase(trimmed) || trimmed.startsWith("samples/") || trimmed.startsWith("./samples")) {
            return;
        }

        if (GIT_SSH.matcher(trimmed).matches()) {
            return;
        }

        try {
            URI uri = URI.create(trimmed);
            if (uri.getScheme() != null && ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase())) {
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }

        Path local = Path.of(trimmed);
        if (Files.exists(local)) {
            return;
        }

        throw new IllegalArgumentException(
                "Repository must be http(s) URL, git@ SSH URL, existing local path, or 'samples'"
        );
    }
}
