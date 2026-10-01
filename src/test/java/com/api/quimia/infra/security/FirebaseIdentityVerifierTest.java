package com.api.quimia.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.account.internal.usecase.AccountException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

class FirebaseIdentityVerifierTest {
    @Test
    void missingProjectIdDisablesSocialLogin() {
        assertThatThrownBy(() -> new FirebaseIdentityVerifier("").verify("token"))
                .isInstanceOfSatisfying(AccountException.class, error -> {
                    assertThat(error.code()).isEqualTo("firebase_disabled");
                    assertThat(error.status()).isEqualTo(503);
                });
    }

    @Test
    void rejectedTokenBecomesUnauthorized() {
        var verifier = new FirebaseIdentityVerifier(token -> {
            throw new BadJwtException("bad signature");
        });

        assertThatThrownBy(() -> verifier.verify("token"))
                .isInstanceOfSatisfying(AccountException.class, error -> {
                    assertThat(error.code()).isEqualTo("invalid_firebase_token");
                    assertThat(error.status()).isEqualTo(401);
                });
    }

    @Test
    void mapsFirebaseClaims() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("firebase-uid")
                .claim("email", "pessoa@example.com")
                .claim("email_verified", true)
                .claim("name", "Pessoa")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        var identity = new FirebaseIdentityVerifier(token -> jwt).verify("token");

        assertThat(identity.email()).isEqualTo("pessoa@example.com");
        assertThat(identity.emailVerified()).isTrue();
        assertThat(identity.name()).isEqualTo("Pessoa");
    }
}
