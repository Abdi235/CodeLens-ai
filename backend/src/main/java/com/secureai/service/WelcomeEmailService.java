package com.secureai.service;

import com.secureai.config.MailProperties;
import com.secureai.model.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WelcomeEmailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final MailProperties mailProperties;

    public boolean isConfigured() {
        return mailProperties.enabled() && mailSender.getIfAvailable() != null;
    }

    public void sendWelcomeEmail(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.info("Skipping welcome email — user {} has no email", user.getId());
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!mailProperties.enabled() || sender == null) {
            log.info("Welcome email skipped (mail not configured) for {}", user.getEmail());
            return;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String from = mailProperties.from() == null || mailProperties.from().isBlank()
                    ? "noreply@codelens.app"
                    : mailProperties.from();
            String fromName = mailProperties.fromName() == null || mailProperties.fromName().isBlank()
                    ? "CodeLens"
                    : mailProperties.fromName();
            helper.setFrom(from, fromName);
            helper.setTo(user.getEmail());
            helper.setSubject("Welcome to CodeLens — your code intelligence platform");
            helper.setText(buildHtml(user), true);
            sender.send(message);
            log.info("Sent welcome email to {}", user.getEmail());
        } catch (Exception ex) {
            log.warn("Failed to send welcome email to {}: {}", user.getEmail(), ex.getMessage());
        }
    }

    public String buildHtml(User user) {
        String name = user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername()
                : (user.getEmail() != null ? user.getEmail().split("@")[0] : "there");

        return """
                <div style="font-family:IBM Plex Sans,Segoe UI,sans-serif;max-width:640px;margin:0 auto;color:#0b1624;line-height:1.55">
                  <h1 style="font-family:Georgia,serif;font-weight:600;font-size:28px;margin:0 0 12px">Welcome to CodeLens</h1>
                  <p style="margin:0 0 16px;color:#334155">Hi %s — thanks for joining.</p>
                  <p style="margin:0 0 16px;color:#334155">
                    <strong>CodeLens</strong> is a distributed code intelligence platform. It analyzes repositories
                    for security issues, indexes source for BM25 search, streams job status live, monitors service
                    health, and runs an Ops Agent that can remediate stuck jobs and sleeping workers.
                  </p>
                  <h2 style="font-size:18px;margin:24px 0 8px">What you can do</h2>
                  <ul style="margin:0 0 16px;padding-left:18px;color:#334155">
                    <li><strong>Analysis</strong> — submit a GitHub repo or the bundled <code>samples</code> project and review findings.</li>
                    <li><strong>Search</strong> — ask natural-language questions over indexed code.</li>
                    <li><strong>Dashboard</strong> — watch uptime, latency, error rate, and pipeline counts.</li>
                    <li><strong>Ops Agent</strong> — run live remediations or incident simulations with a tool transcript.</li>
                  </ul>
                  <p style="margin:0 0 16px;color:#334155">
                    Tip: free-tier backends may sleep when idle — the first request after a quiet period can take
                    30–90 seconds.
                  </p>
                  <p style="margin:24px 0 0;color:#64748b;font-size:13px">— The CodeLens team</p>
                </div>
                """.formatted(escape(name));
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
