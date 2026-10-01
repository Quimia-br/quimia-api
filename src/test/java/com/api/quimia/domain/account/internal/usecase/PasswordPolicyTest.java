package com.api.quimia.domain.account.internal.usecase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    @Test
    void acceptsLettersAndDigits() {
        assertThatCode(() -> PasswordPolicy.validate("Quimia2026")).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingLetterOrDigitAndKnownWeakPasswords() {
        assertRejected("12345678", "weak_password");
        assertRejected("somenteletras", "weak_password");
        assertRejected("Senha123", "weak_password");
    }

    @Test
    void rejectsPasswordsBeyondBcryptByteLimit() {
        assertRejected("ç1".repeat(30), "password_too_long");
    }

    private static void assertRejected(String password, String code) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(AccountException.class)
                .hasMessage(code);
    }
}
