package com.api.quimia.infra.security;

import com.api.quimia.domain.account.internal.usecase.AccessTokenIssuer;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {
    private final JwtService jwt;
    private final long expiresInSeconds;

    public JwtAccessTokenIssuer(
            JwtService jwt,
            @Value("${quimia.jwt.access-ttl-minutes:15}") long accessTtlMinutes) {
        this.jwt = jwt;
        this.expiresInSeconds = accessTtlMinutes * 60;
    }

    @Override
    public IssuedToken issue(String subject, String role) {
        return new IssuedToken(jwt.issue(UUID.fromString(subject), role), expiresInSeconds);
    }
}
