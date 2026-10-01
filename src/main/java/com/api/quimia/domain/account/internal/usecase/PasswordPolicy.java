package com.api.quimia.domain.account.internal.usecase;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Regra do protótipo: pelo menos 8 caracteres, uma letra e um número. */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    /** O BCrypt considera no máximo 72 bytes. */
    public static final int MAX_LENGTH = 72;

    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "senha123", "senha1234", "password1", "password123", "12345678a", "a12345678",
            "quimia123", "qwerty123", "abc12345");

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_LENGTH) {
            throw new AccountException("password_too_long", 422);
        }
        boolean hasLetter = password.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit || WEAK_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            throw new AccountException("weak_password", 422);
        }
    }
}
