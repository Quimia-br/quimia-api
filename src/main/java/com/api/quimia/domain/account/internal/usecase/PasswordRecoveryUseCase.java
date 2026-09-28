package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.internal.dto.RecoveryGrantResponse;
import com.api.quimia.domain.account.internal.model.PasswordRecovery;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.persistence.PasswordRecoveryRepository;
import com.api.quimia.domain.account.internal.persistence.RefreshTokenRepository;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryUseCase {
    private static final int CODE_TTL_MINUTES = 15;
    private static final int RESET_TOKEN_TTL_MINUTES = 10;
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_REQUESTS_PER_HOUR = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository users;
    private final PasswordRecoveryRepository recoveries;
    private final RefreshTokenRepository refreshes;
    private final PasswordEncoder passwords;
    private final TokenHasher tokenHasher;
    private final RecoveryCodeHasher codeHasher;
    private final ApplicationEventPublisher events;
    private final AuthenticationAuditRecorder audit;

    public PasswordRecoveryUseCase(
            UsuarioRepository users,
            PasswordRecoveryRepository recoveries,
            RefreshTokenRepository refreshes,
            PasswordEncoder passwords,
            TokenHasher tokenHasher,
            RecoveryCodeHasher codeHasher,
            ApplicationEventPublisher events,
            AuthenticationAuditRecorder audit) {
        this.users = users;
        this.recoveries = recoveries;
        this.refreshes = refreshes;
        this.passwords = passwords;
        this.tokenHasher = tokenHasher;
        this.codeHasher = codeHasher;
        this.events = events;
        this.audit = audit;
    }

    @Transactional(noRollbackFor = AccountException.class)
    public void requestCode(String emailInput) {
        String email = normalize(emailInput);
        var found = users.findByEmailForUpdate(email);
        if (found.isEmpty()) {
            audit.record(null, "password_recovery_requested");
            return;
        }

        Usuario user = found.get();
        OffsetDateTime now = OffsetDateTime.now();
        var latest = recoveries.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (latest.isPresent()
                && latest.get().getCreatedAt().isAfter(now.minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            audit.record(user.getId(), "password_recovery_throttled");
            return;
        }
        if (recoveries.countByUserIdAndCreatedAtAfter(user.getId(), now.minusHours(1)) >= MAX_REQUESTS_PER_HOUR) {
            audit.record(user.getId(), "password_recovery_throttled");
            return;
        }

        latest.ifPresent(challenge -> challenge.setRevokedAt(now));
        String code = String.format(Locale.ROOT, "%08d", RANDOM.nextInt(100_000_000));
        PasswordRecovery challenge = new PasswordRecovery();
        challenge.setId(UUID.randomUUID());
        challenge.setUserId(user.getId());
        challenge.setCodeHash(codeHasher.hash(challenge.getId(), code));
        challenge.setExpiresAt(now.plusMinutes(CODE_TTL_MINUTES));
        challenge.setAttempts(0);
        challenge.setCreatedAt(now);
        recoveries.save(challenge);
        audit.record(user.getId(), "password_recovery_requested");
        events.publishEvent(new RecoveryCodeRequestedEvent(user.getId(), user.getEmail(), code));
    }

    @Transactional(noRollbackFor = AccountException.class)
    public RecoveryGrantResponse verifyCode(String emailInput, String code) {
        String email = normalize(emailInput);
        var found = users.findByEmailForUpdate(email);
        if (found.isEmpty()) {
            audit.record(null, "password_recovery_code_failed");
            throw invalidRecoveryCode();
        }

        Usuario user = found.get();
        var candidate = recoveries.findFirstByUserIdAndRevokedAtIsNullAndCompletedAtIsNullOrderByCreatedAtDesc(
                user.getId());
        if (candidate.isEmpty()) {
            audit.record(user.getId(), "password_recovery_code_failed");
            throw invalidRecoveryCode();
        }

        PasswordRecovery challenge = candidate.get();
        OffsetDateTime now = OffsetDateTime.now();
        if (challenge.getCodeConsumedAt() != null
                || !challenge.getExpiresAt().isAfter(now)
                || challenge.getAttempts() >= MAX_CODE_ATTEMPTS
                || challenge.getCodeHash() == null) {
            audit.record(user.getId(), "password_recovery_code_failed");
            throw invalidRecoveryCode();
        }
        if (!codeHasher.matches(challenge.getId(), code, challenge.getCodeHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            if (challenge.getAttempts() >= MAX_CODE_ATTEMPTS) {
                challenge.setRevokedAt(now);
            }
            audit.record(user.getId(), "password_recovery_code_failed");
            throw invalidRecoveryCode();
        }

        String resetToken = randomToken();
        challenge.setCodeConsumedAt(now);
        challenge.setCodeHash(null);
        challenge.setResetTokenHash(tokenHasher.hash(resetToken));
        challenge.setResetTokenExpiresAt(now.plusMinutes(RESET_TOKEN_TTL_MINUTES));
        audit.record(user.getId(), "password_recovery_code_verified");
        return new RecoveryGrantResponse(resetToken, RESET_TOKEN_TTL_MINUTES * 60L);
    }

    @Transactional(noRollbackFor = AccountException.class)
    public void resetPassword(String resetToken, String newPassword) {
        String resetHash = tokenHasher.hash(resetToken);
        UUID userId = recoveries.findUserIdByResetTokenHash(resetHash)
                .orElseThrow(PasswordRecoveryUseCase::invalidResetToken);
        Usuario user = users.findByIdForUpdate(userId).orElseThrow(PasswordRecoveryUseCase::invalidResetToken);
        PasswordRecovery challenge = recoveries.findByResetTokenHash(resetHash)
                .orElseThrow(PasswordRecoveryUseCase::invalidResetToken);
        OffsetDateTime now = OffsetDateTime.now();
        if (challenge.getCompletedAt() != null
                || challenge.getRevokedAt() != null
                || challenge.getResetTokenExpiresAt() == null
                || !challenge.getResetTokenExpiresAt().isAfter(now)) {
            throw invalidResetToken();
        }

        PasswordPolicy.validate(newPassword);
        user.setSenhaHash(passwords.encode(newPassword));
        user.setFalhasLogin(0);
        user.setBloqueadoAte(null);
        user.setUltimaFalhaEm(null);
        refreshes.revokeActiveByUserId(user.getId(), now);
        challenge.setCompletedAt(now);
        challenge.setResetTokenHash(null);
        challenge.setResetTokenExpiresAt(null);
        audit.record(user.getId(), "password_recovery_completed");
    }

    private static AccountException invalidRecoveryCode() {
        return new AccountException("invalid_recovery_code", 400);
    }

    private static AccountException invalidResetToken() {
        return new AccountException("invalid_reset_token", 400);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
