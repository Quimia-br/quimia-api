package com.api.quimia.domain.account.internal.usecase;

import java.util.UUID;

public interface EmailVerificationSender {
    void send(UUID userId, String email, String verificationToken);
}
