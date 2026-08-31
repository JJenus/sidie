package com.jjenus.tracker.userauth.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

import com.jjenus.tracker.shared.exception.ValidationException;

@Service
public class PasswordService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final BCryptPasswordEncoder encoder;

    public PasswordService(@Value("${userauth.password.bcrypt-strength:12}") int strength) {
        if (strength < 4 || strength > 31) {
            throw new IllegalArgumentException("bcrypt strength must be between 4 and 31");
        }
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    public void validateStrength(String rawPassword) {
        if (rawPassword == null) {
            throw new ValidationException("PASSWORD_REQUIRED", "password is required");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("PASSWORD_TOO_SHORT",
                "password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    public String hash(String rawPassword) {
        validateStrength(rawPassword);
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) return false;
        return encoder.matches(rawPassword, storedHash);
    }
}
