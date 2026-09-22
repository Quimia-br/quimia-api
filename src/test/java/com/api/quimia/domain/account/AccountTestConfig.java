package com.api.quimia.domain.account;

import com.api.quimia.domain.account.internal.usecase.EmailVerificationSender;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class AccountTestConfig {
    private final AtomicReference<String> lastToken = new AtomicReference<>();

    @Bean
    @Primary
    EmailVerificationSender recordingSender() {
        return (UUID userId, String email, String verificationToken) -> lastToken.set(verificationToken);
    }

    @Bean
    TokenProbe tokenProbe() {
        return () -> lastToken.get();
    }

    public interface TokenProbe {
        String lastToken();
    }
}
