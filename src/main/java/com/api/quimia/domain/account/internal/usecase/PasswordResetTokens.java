package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.AccountPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Token de redefinição sem estado; vira inválido assim que a senha muda (uso único). */
@Component
public class PasswordResetTokens {
    private static final String RESET_TOKEN = "password_reset";
    private static final String CLAIM_CREDENTIAL = "cred";

    private final SignedTokenCodec codec;
    private final CredentialFingerprint fingerprints;

    public PasswordResetTokens(SignedTokenCodec codec, CredentialFingerprint fingerprints) {
        this.codec = codec;
        this.fingerprints = fingerprints;
    }

    public String issue(AccountPrincipal principal, String passwordHash, Duration ttl) {
        return codec.sign(
                RESET_TOKEN,
                principal.id(),
                Map.of(
                        SessionIssuer.CLAIM_TYPE, principal.type().claim(),
                        CLAIM_CREDENTIAL, fingerprints.of(principal, passwordHash)),
                Instant.now().plus(ttl));
    }

    public ResetGrant read(String token, AccountPrincipal.Type expectedType) {
        SignedTokenCodec.VerifiedToken verified = codec.verify(token, RESET_TOKEN)
                .filter(candidate -> expectedType.claim().equals(candidate.text(SessionIssuer.CLAIM_TYPE)))
                .orElseThrow(PasswordResetTokens::invalid);
        return new ResetGrant(new AccountPrincipal(expectedType, verified.subject()), verified.text(CLAIM_CREDENTIAL));
    }

    public void requireCurrent(ResetGrant grant, String passwordHash) {
        if (!fingerprints.matches(grant.principal(), passwordHash, grant.credential())) {
            throw invalid();
        }
    }

    public static AccountException invalid() {
        return new AccountException("invalid_reset_token", 400);
    }

    public record ResetGrant(AccountPrincipal principal, String credential) {}
}
