package com.api.quimia.infra.security;

import com.api.quimia.domain.account.internal.usecase.SignedTokenCodec;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JwtSignedTokenCodec implements SignedTokenCodec {
    private final JwtService jwt;

    public JwtSignedTokenCodec(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public String sign(String tokenUse, String subject, Map<String, Object> claims, Instant expiresAt) {
        return jwt.sign(tokenUse, subject, claims, expiresAt);
    }

    @Override
    public Optional<VerifiedToken> verify(String token, String tokenUse) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = jwt.verify(token, tokenUse);
            return Optional.of(new VerifiedToken(claims.getSubject(), claims));
        } catch (JwtService.InvalidTokenException error) {
            return Optional.empty();
        }
    }
}
