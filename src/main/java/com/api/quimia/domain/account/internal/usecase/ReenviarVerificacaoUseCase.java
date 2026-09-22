package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.internal.dto.ResendVerificationRequest;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReenviarVerificacaoUseCase {
    private final UsuarioRepository users;
    private final VerificationTokenGenerator tokens;
    private final EmailVerificationSender sender;
    private final AuthenticationAuditRecorder audit;

    public ReenviarVerificacaoUseCase(
            UsuarioRepository users,
            VerificationTokenGenerator tokens,
            EmailVerificationSender sender,
            AuthenticationAuditRecorder audit) {
        this.users = users;
        this.tokens = tokens;
        this.sender = sender;
        this.audit = audit;
    }

    @Transactional
    public void execute(ResendVerificationRequest request) {
        String email = request.email().trim().toLowerCase();
        var found = users.findByEmail(email);
        if (found.isEmpty() || found.get().getEmailVerificadoEm() != null) {
            return;
        }
        var user = found.get();
        String raw = tokens.generate();
        user.setVerificationTokenHash(tokens.hash(raw));
        user.setVerificationExpiraEm(OffsetDateTime.now().plusHours(24));
        sender.send(user.getId(), user.getEmail(), raw);
        audit.record(user.getId(), "verification_sent");
    }
}
