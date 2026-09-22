package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.internal.persistence.RefreshTokenRepository;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncerrarSessaoUseCase {
    private final RefreshTokenRepository refreshes;
    private final UsuarioRepository users;
    private final TokenHasher hasher;
    private final AuthenticationAuditRecorder audit;

    public EncerrarSessaoUseCase(
            RefreshTokenRepository refreshes,
            UsuarioRepository users,
            TokenHasher hasher,
            AuthenticationAuditRecorder audit) {
        this.refreshes = refreshes;
        this.users = users;
        this.hasher = hasher;
        this.audit = audit;
    }

    @Transactional
    public void execute(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            return;
        }
        String hash = hasher.hash(rawRefresh);
        var userId = refreshes.findUserIdByTokenHash(hash);
        if (userId.isEmpty() || users.findByIdForUpdate(userId.get()).isEmpty()) {
            return;
        }
        refreshes.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(OffsetDateTime.now());
            }
            audit.record(token.getUserId(), "logout");
        });
    }
}
