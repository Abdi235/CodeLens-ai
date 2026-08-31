package com.secureai;

import com.secureai.validation.RepositoryUrlValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryUrlValidatorTest {

    private final RepositoryUrlValidator validator = new RepositoryUrlValidator();

    @Test
    void acceptsSamplesAlias() {
        assertDoesNotThrow(() -> validator.validate("samples"));
    }

    @Test
    void acceptsHttpsUrl() {
        assertDoesNotThrow(() -> validator.validate("https://github.com/example/repo.git"));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("  "));
    }

    @Test
    void rejectsInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("not-a-valid-repo"));
    }
}
