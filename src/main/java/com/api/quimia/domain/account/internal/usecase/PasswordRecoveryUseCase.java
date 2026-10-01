package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.account.internal.dto.RecoveryChallengeResponse;
import com.api.quimia.domain.account.internal.dto.RecoveryGrantResponse;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperação do app por código (telas LoginPassCode). Sem tabela no schema, o desafio volta ao
 * cliente como token assinado com o HMAC do código; tentativas e cooldown ficam em memória.
 */
@Service
public class PasswordRecoveryUseCase {
    private static final int CODE_DIGITS = 4;
    private static final int CODE_BOUND = 10_000;
    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(10);
    private static final int MAX_CODE_FAILURES = 5;
    private static final Duration CODE_FAILURE_LOCK = Duration.ofHours(1);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(15);
    private static final int MAX_REQUESTS_PER_HOUR = 5;
    private static final String CHALLENGE_TOKEN = "recovery_challenge";
    private static final String CLAIM_DIGEST = "chd";
    private static final String EMAIL_SUBJECT = "Código de recuperação de senha";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository users;
    private final PasswordEncoder passwords;
    private final SignedTokenCodec codec;
    private final SecretHasher hasher;
    private final CredentialFingerprint fingerprints;
    private final PasswordResetTokens resetTokens;
    private final AccountThrottle throttle;
    private final ApplicationEventPublisher events;
    private final AuthenticationAuditRecorder audit;
    private final EmailTemplates templates;

    public PasswordRecoveryUseCase(
            UsuarioRepository users,
            PasswordEncoder passwords,
            SignedTokenCodec codec,
            SecretHasher hasher,
            CredentialFingerprint fingerprints,
            PasswordResetTokens resetTokens,
            AccountThrottle throttle,
            ApplicationEventPublisher events,
            AuthenticationAuditRecorder audit,
            EmailTemplates templates) {
        this.users = users;
        this.passwords = passwords;
        this.codec = codec;
        this.hasher = hasher;
        this.fingerprints = fingerprints;
        this.resetTokens = resetTokens;
        this.throttle = throttle;
        this.events = events;
        this.audit = audit;
        this.templates = templates;
    }

    /** Sempre devolve um desafio com o mesmo formato, exista ou não a conta (anti-enumeração). */
    @Transactional(readOnly = true)
    public RecoveryChallengeResponse requestCode(String emailInput) {
        String email = Emails.normalize(emailInput);
        String challengeId = UUID.randomUUID().toString();
        Optional<Usuario> found = users.findByEmail(email);
        String digest;
        if (found.isPresent() && throttle.tryAcquire(requestKey(email), RESEND_COOLDOWN, MAX_REQUESTS_PER_HOUR)) {
            Usuario user = found.get();
            AccountPrincipal principal = SessionIssuer.principalOf(user);
            String code = String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", RANDOM.nextInt(CODE_BOUND));
            digest = challengeDigest(challengeId, email, code, fingerprints.of(principal, user.getSenha()));
            audit.record("password_recovery_requested", principal);
            events.publishEvent(new AccountEmailRequestedEvent(
                    principal,
                    user.getEmail(),
                    EMAIL_SUBJECT,
                    emailText(code),
                    templates.render(EmailTemplates.RECOVERY_CODE, Map.of("code", code))));
        } else {
            digest = hasher.hmac("recovery-decoy", challengeId);
            found.ifPresentOrElse(
                    user -> audit.record("password_recovery_throttled", SessionIssuer.principalOf(user)),
                    () -> audit.record("password_recovery_requested"));
        }
        String challenge = codec.sign(
                CHALLENGE_TOKEN, challengeId, Map.of(CLAIM_DIGEST, digest), Instant.now().plus(CODE_TTL));
        return new RecoveryChallengeResponse(challenge, CODE_TTL.toSeconds());
    }

    @Transactional(readOnly = true)
    public RecoveryGrantResponse verifyCode(String challengeToken, String emailInput, String code) {
        String email = Emails.normalize(emailInput);
        String failureKey = codeFailureKey(email);
        SignedTokenCodec.VerifiedToken challenge =
                codec.verify(challengeToken, CHALLENGE_TOKEN).orElseThrow(PasswordRecoveryUseCase::invalidCode);
        String consumedKey = consumedKey(challenge.subject());
        if (throttle.isBlocked(failureKey) || throttle.isBlocked(consumedKey)) {
            audit.record("password_recovery_code_rejected");
            throw invalidCode();
        }
        Optional<Usuario> found = users.findByEmail(email);
        boolean valid = found.isPresent() && hasher.matches(
                challengeDigest(
                        challenge.subject(),
                        email,
                        code,
                        fingerprints.of(SessionIssuer.principalOf(found.get()), found.get().getSenha())),
                challenge.text(CLAIM_DIGEST));
        if (!valid) {
            throttle.recordFailure(failureKey, MAX_CODE_FAILURES, CODE_FAILURE_LOCK);
            audit.record("password_recovery_code_failed");
            throw invalidCode();
        }
        throttle.block(consumedKey, CODE_TTL);
        Usuario user = found.get();
        AccountPrincipal principal = SessionIssuer.principalOf(user);
        audit.record("password_recovery_code_verified", principal);
        String resetToken = resetTokens.issue(principal, user.getSenha(), RESET_TOKEN_TTL);
        return new RecoveryGrantResponse(resetToken, RESET_TOKEN_TTL.toSeconds());
    }

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        PasswordResetTokens.ResetGrant grant = resetTokens.read(resetToken, AccountPrincipal.Type.USUARIO);
        Usuario user = users.findById(grant.principal().usuarioId()).orElseThrow(PasswordResetTokens::invalid);
        resetTokens.requireCurrent(grant, user.getSenha());
        PasswordPolicy.validate(newPassword);
        user.setSenha(passwords.encode(newPassword));
        String email = Emails.normalize(user.getEmail());
        throttle.reset(AutenticarUseCase.loginKey(email));
        throttle.reset(codeFailureKey(email));
        audit.record("password_recovery_completed", grant.principal());
    }

    private String challengeDigest(String challengeId, String email, String code, String credential) {
        return hasher.hmac("recovery-code", challengeId, email, code, credential);
    }

    private static String emailText(String code) {
        return "Olá!\n\nRecebemos uma solicitação para redefinir a senha da sua conta no Quimia. "
                + "Se foi você quem pediu, digite o código abaixo no aplicativo para criar uma nova senha:\n\n"
                + code + "\n\n"
                + "Este código é válido por apenas 15 minutos e só pode ser usado uma vez. "
                + "Não compartilhe este código com ninguém: a equipe Quimia nunca pede o seu código.\n\n"
                + "Se não foi você quem solicitou essa alteração, pode ignorar este e-mail tranquilamente. "
                + "Sua senha continua a mesma.\n\nAtenciosamente,\nEquipe Quimia";
    }

    private static String requestKey(String email) {
        return "recovery-request:usuario:" + email;
    }

    private static String codeFailureKey(String email) {
        return "recovery-code:usuario:" + email;
    }

    private static String consumedKey(String challengeId) {
        return "recovery-challenge-consumed:" + challengeId;
    }

    private static AccountException invalidCode() {
        return new AccountException("invalid_recovery_code", 400);
    }
}
