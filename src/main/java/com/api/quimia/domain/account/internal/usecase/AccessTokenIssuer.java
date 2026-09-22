package com.api.quimia.domain.account.internal.usecase;

public interface AccessTokenIssuer {
    IssuedToken issue(String subject, String role);

    record IssuedToken(String token, long expiresInSeconds) {}
}
