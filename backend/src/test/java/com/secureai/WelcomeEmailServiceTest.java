package com.secureai;

import com.secureai.model.User;
import com.secureai.service.WelcomeEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WelcomeEmailServiceTest {

    @Autowired
    private WelcomeEmailService welcomeEmailService;

    @Test
    void welcomeHtmlDescribesCodeLens() {
        String html = welcomeEmailService.buildHtml(User.builder()
                .email("dev@example.com")
                .passwordHash("x")
                .build());

        assertThat(html).contains("Welcome to CodeLens");
        assertThat(html).contains("distributed code intelligence");
        assertThat(html).contains("Ops Agent");
        assertThat(html).contains("Analysis");
    }
}
