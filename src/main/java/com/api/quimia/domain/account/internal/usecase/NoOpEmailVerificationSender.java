package com.api.quimia.domain.account.internal.usecase;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoOpEmailVerificationSender implements EmailVerificationSender {
    private static final Logger log = LoggerFactory.getLogger(NoOpEmailVerificationSender.class);

    @Override
    public void send(UUID userId, String email, String verificationToken) {
        log.info("verification issued user={}", userId);
    }
}
