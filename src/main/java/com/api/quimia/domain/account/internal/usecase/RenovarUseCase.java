package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.dto.UserSummary;
import com.api.quimia.domain.account.internal.dto.LoginResponse;
import com.api.quimia.domain.account.internal.model.RefreshToken;
import com.api.quimia.domain.account.internal.persistence.RefreshTokenRepository;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RenovarUseCase {
    private final UsuarioRepository users;
    private final RefreshTokenRepository refreshes;
    private final TokenHasher hasher;
    private final AccessTokenIssuer tokens;
    private final int inactivityDays;
    private final AuthenticationAuditRecorder audit;

    public RenovarUseCase(
            UsuarioRepository users,
            RefreshTokenRepository refreshes,
            TokenHasher hasher,
            AccessTokenIssuer tokens,
            @Value("${app.auth.refresh-inactivity-days:7}") int inactivityDays,
            AuthenticationAuditRecorder audit) {
        this.users = users;
        this.refreshes = refreshes;
        this.hasher = hasher;
        this.tokens = tokens;
        this.inactivityDays = inactivityDays;
        this.audit = audit;
    }

    @Transactional(noRollbackFor = AccountException.class)
    public IssuedSession execute(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            audit.record(null, "refresh_failure");
            throw new AccountException("invalid_refresh", 401);
        }
        String hash = hasher.hash(rawRefresh);
        var userId = refreshes.findUserIdByTokenHash(hash);
        if (userId.isEmpty()) {
            audit.record(null, "refresh_failure");
            throw new AccountException("invalid_refresh", 401);
        }
        var userFound = users.findByIdForUpdate(userId.get());
        if (userFound.isEmpty()) {
            audit.record(null, "refresh_failure");
            throw new AccountException("invalid_refresh", 401);
        }
        var found = refreshes.findByTokenHash(hash);
        if (found.isEmpty()) {
            audit.record(userId.get(), "refresh_failure");
            throw new AccountException("invalid_refresh", 401);
        }
        RefreshToken current = found.get();
        if (current.getRevokedAt() != null) {
            revokeFamily(current.getFamilyId());
            audit.record(current.getUserId(), "refresh_reuse");
            throw new AccountException("invalid_refresh", 401);
        }
        if (current.getExpiresAt().isBefore(OffsetDateTime.now())) {
            revokeFamily(current.getFamilyId());
            audit.record(current.getUserId(), "refresh_failure");
            throw new AccountException("invalid_refresh", 401);
        }
        if (current.getLastUsedAt() != null
                && current.getLastUsedAt().plusDays(inactivityDays).isBefore(OffsetDateTime.now())) {
            revokeFamily(current.getFamilyId());
            audit.record(current.getUserId(), "refresh_failure");
            throw new AccountException("invalid_refresh", 401);
        }
        var user = userFound.get();
        if (user.getBloqueadoAte() != null && user.getBloqueadoAte().isAfter(OffsetDateTime.now())) {
            audit.record(user.getId(), "refresh_failure");
            throw new AccountException("blocked", 423);
        }
        RefreshToken next = new RefreshToken();
        next.setId(UUID.randomUUID());
        next.setUserId(user.getId());
        String raw = randomToken();
        next.setTokenHash(hasher.hash(raw));
        next.setFamilyId(current.getFamilyId());
        OffsetDateTime rotatedAt = OffsetDateTime.now();
        next.setExpiresAt(current.getExpiresAt());
        next.setLastUsedAt(rotatedAt);
        next.setCreatedAt(rotatedAt);
        current.setRevokedAt(rotatedAt);
        current.setReplacedBy(next.getId());
        refreshes.save(next);
        user.setUltimaSessao(OffsetDateTime.now());
        audit.record(user.getId(), "refresh");
        AccessTokenIssuer.IssuedToken access =
                tokens.issue(user.getId().toString(), user.getNivelAcesso().name());
        LoginResponse response = new LoginResponse(
                access.token(),
                "Bearer",
                access.expiresInSeconds(),
                new UserSummary(user.getId(), user.getNome(), user.getEmail()));
        return new IssuedSession(response, raw);
    }

    private void revokeFamily(UUID familyId) {
        refreshes.findByFamilyId(familyId).forEach(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(OffsetDateTime.now());
            }
        });
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
