package com.secureai.service;

import com.secureai.dto.AuthResponse;
import com.secureai.dto.LoginRequest;
import com.secureai.dto.RegisterRequest;
import com.secureai.model.AuthProvider;
import com.secureai.model.Role;
import com.secureai.model.User;
import com.secureai.repository.UserRepository;
import com.secureai.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]{3,32}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final WelcomeEmailService welcomeEmailService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (request.password().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        String email = normalizeEmail(request.email());
        String username = normalizeUsername(request.username());

        if (email == null && username == null) {
            throw new IllegalArgumentException("Provide an email or a username");
        }
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        if (username != null && !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Username must be 3–32 characters (letters, numbers, underscore)");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (username != null && userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username already taken");
        }

        User user = User.builder()
                .email(email)
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build();

        userRepository.save(user);
        welcomeEmailService.sendWelcomeEmail(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        String login = request.login().trim();
        User user = userRepository.findByLogin(login)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getLoginIdentity(), request.password())
        );

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(
                user.getLoginIdentity(),
                Map.of("role", user.getRole().name(), "uid", user.getId())
        );
        return new AuthResponse(token, user.getEmail(), user.getUsername(), user.getRole());
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim();
    }
}
