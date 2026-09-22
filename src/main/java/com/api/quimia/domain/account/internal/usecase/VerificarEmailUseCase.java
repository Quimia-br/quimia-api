package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificarEmailUseCase {
    private final UsuarioRepository users;
    private final VerificationTokenGenerator tokens;
    private final AuthenticationAuditRecorder audit;

    public VerificarEmailUseCase(
            UsuarioRepository users,
            VerificationTokenGenerator tokens,
            AuthenticationAuditRecorder audit) {
        this.users = users;
        this.tokens = tokens;
        this.audit = audit;
    }

    @Transactional
    public void execute(String rawToken) {
        String hash = tokens.hash(rawToken);
        var user = users.findByVerificationTokenHash(hash)
                .orElseThrow(() -> new AccountException("invalid_token", 400));
        if (user.getEmailVerificadoEm() != null) {
            throw new AccountException("invalid_token", 400);
        }
        if (user.getVerificationExpiraEm() == null || user.getVerificationExpiraEm().isBefore(OffsetDateTime.now())) {
            throw new AccountException("invalid_token", 400);
        }
        user.setEmailVerificadoEm(OffsetDateTime.now());
        user.setVerificationTokenHash(null);
        user.setVerificationExpiraEm(null);
        audit.record(user.getId(), "verified");
    }
}
