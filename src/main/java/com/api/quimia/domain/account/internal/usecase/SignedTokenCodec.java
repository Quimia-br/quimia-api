package com.api.quimia.domain.account.internal.usecase;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Tokens assinados e autocontidos: o schema não tem tabelas de sessão nem de recuperação. */
public interface SignedTokenCodec {
    String sign(String tokenUse, String subject, Map<String, Object> claims, Instant expiresAt);

    Optional<VerifiedToken> verify(String token, String tokenUse);

    record VerifiedToken(String subject, Map<String, Object> claims) {
        public String text(String name) {
            Object value = claims.get(name);
            return value == null ? null : value.toString();
        }

        public Long number(String name) {
            return claims.get(name) instanceof Number value ? value.longValue() : null;
        }
    }
}
