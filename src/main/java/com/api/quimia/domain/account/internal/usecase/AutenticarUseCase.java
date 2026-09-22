package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.dto.UserSummary;
import com.api.quimia.domain.account.internal.dto.LoginRequest;
import com.api.quimia.domain.account.internal.dto.LoginResponse;
import com.api.quimia.domain.account.internal.model.RefreshToken;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.persistence.RefreshTokenRepository;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticarUseCase {
    private final UsuarioRepository users;
    private final RefreshTokenRepository refreshes;
    private final PasswordEncoder passwords;
    private final TokenHasher hasher;
    private final AccessTokenIssuer tokens;
    private final int maxFailures;
    private final int blockMinutes;
    private final int refreshTtlDays;
    private final AuthenticationAuditRecorder audit;

    public AutenticarUseCase(
            UsuarioRepository users,
            RefreshTokenRepository refreshes,
            PasswordEncoder passwords,
            TokenHasher hasher,
            AccessTokenIssuer tokens,
            @Value("${app.auth.max-failures:5}") int maxFailures,
            @Value("${app.auth.block-minutes:15}") int blockMinutes,
            @Value("${app.auth.refresh-ttl-days:30}") int refreshTtlDays,
            AuthenticationAuditRecorder audit) {
        this.users = users;
        this.refreshes = refreshes;
        this.passwords = passwords;
        this.hasher = hasher;
        this.tokens = tokens;
        this.maxFailures = maxFailures;
        this.blockMinutes = blockMinutes;
        this.refreshTtlDays = refreshTtlDays;
        this.audit = audit;
    }

    @Transactional(noRollbackFor = AccountException.class)
    public IssuedSession execute(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        var found = users.findByEmailForUpdate(email);
        if (found.isEmpty()) {
            audit.record(null, "login_failed");
            throw new AccountException("invalid_credentials", 401);
        }
        Usuario user = found.get();
        if (user.getBloqueadoAte() != null && user.getBloqueadoAte().isAfter(OffsetDateTime.now())) {
            audit.record(user.getId(), "login_blocked");
            throw new AccountException("blocked", 423);
        }
        if (user.getSenhaHash() == null || !passwords.matches(request.senha(), user.getSenhaHash())) {
            int failures = user.getFalhasLogin() + 1;
            OffsetDateTime blocked = failures >= maxFailures
                    ? OffsetDateTime.now().plusMinutes(blockMinutes)
                    : null;
            user.setFalhasLogin(failures);
            user.setBloqueadoAte(blocked);
            user.setUltimaFalhaEm(OffsetDateTime.now());
            audit.record(user.getId(), "login_failed");
            if (blocked != null) {
                throw new AccountException("blocked", 423);
            }
            throw new AccountException("invalid_credentials", 401);
        }
        if (user.getEmailVerificadoEm() == null) {
            audit.record(user.getId(), "login_failed");
            throw new AccountException("email_not_verified", 403);
        }
        user.setFalhasLogin(0);
        user.setBloqueadoAte(null);
        user.setUltimaSessao(OffsetDateTime.now());
        AccessTokenIssuer.IssuedToken access =
                tokens.issue(user.getId().toString(), user.getNivelAcesso().name());
        String rawRefresh = randomToken();
        RefreshToken refresh = new RefreshToken();
        refresh.setId(UUID.randomUUID());
        refresh.setUserId(user.getId());
        refresh.setTokenHash(hasher.hash(rawRefresh));
        refresh.setFamilyId(UUID.randomUUID());
        OffsetDateTime issuedAt = OffsetDateTime.now();
        refresh.setExpiresAt(issuedAt.plusDays(refreshTtlDays));
        refresh.setLastUsedAt(issuedAt);
        refresh.setCreatedAt(issuedAt);
        refreshes.save(refresh);
        audit.record(user.getId(), "login_success");
        LoginResponse response = new LoginResponse(
                access.token(),
                "Bearer",
                access.expiresInSeconds(),
                new UserSummary(user.getId(), user.getNome(), user.getEmail()));
        return new IssuedSession(response, rawRefresh);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
