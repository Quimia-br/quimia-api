package com.api.quimia.domain.account.internal.usecase;

import java.util.Set;

public final class PasswordPolicy {
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "1234567890", "password123", "quimia12345", "senha123456", "abcdefghij", "qwerty1234");

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (WEAK_PASSWORDS.contains(password)) {
            throw new AccountException("weak_password", 422);
        }
    }
}
