package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.AccountPrincipal;
import org.springframework.stereotype.Component;

/**
 * Vincula tokens ao hash de senha atual. Trocar a senha invalida refresh, códigos e links de
 * redefinição emitidos antes, sem precisar de tabela de revogação.
 */
@Component
public class CredentialFingerprint {
    private final SecretHasher hasher;

    public CredentialFingerprint(SecretHasher hasher) {
        this.hasher = hasher;
    }

    public String of(AccountPrincipal principal, String passwordHash) {
        return hasher.hmac("credential", principal.type().claim(), principal.id(), passwordHash);
    }

    public boolean matches(AccountPrincipal principal, String passwordHash, String fingerprint) {
        return hasher.matches(of(principal, passwordHash), fingerprint);
    }
}
