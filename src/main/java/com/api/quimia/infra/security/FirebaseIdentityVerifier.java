package com.api.quimia.infra.security;

import com.api.quimia.domain.account.internal.usecase.AccountException;
import com.api.quimia.domain.account.internal.usecase.ExternalIdentityVerifier;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Valida ID tokens do Firebase Authentication conforme a documentação do Firebase: RS256, chaves
 * públicas do securetoken, `iss` = https://securetoken.google.com/&lt;projeto&gt;, `aud` = projeto e
 * `sub` não vazio. Sem `FIREBASE_PROJECT_ID` o login social fica indisponível (503).
 */
@Component
public class FirebaseIdentityVerifier implements ExternalIdentityVerifier {
    private static final String JWK_SET_URI =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
    private static final String ISSUER_PREFIX = "https://securetoken.google.com/";

    private final JwtDecoder decoder;

    @Autowired
    public FirebaseIdentityVerifier(@Value("${app.firebase.project-id:}") String projectId) {
        this.decoder = projectId == null || projectId.isBlank() ? null : decoder(projectId.trim());
    }

    FirebaseIdentityVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public ExternalIdentity verify(String idToken) {
        if (decoder == null) {
            throw new AccountException("firebase_disabled", 503);
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException invalid) {
            throw new AccountException("invalid_firebase_token", 401);
        }
        return new ExternalIdentity(
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("name"));
    }

    private static JwtDecoder decoder(String projectId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().equals(List.of(projectId))
                ? OAuth2TokenValidatorResult.success()
                : failure("invalid audience");
        OAuth2TokenValidator<Jwt> subject = jwt -> jwt.getSubject() != null && !jwt.getSubject().isBlank()
                ? OAuth2TokenValidatorResult.success()
                : failure("missing subject");
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER_PREFIX + projectId), audience, subject));
        return decoder;
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
