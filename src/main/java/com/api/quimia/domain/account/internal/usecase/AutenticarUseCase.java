package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.internal.dto.LoginRequest;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticarUseCase {
    private final UsuarioRepository users;
    private final PasswordEncoder passwords;
    private final SessionIssuer sessions;
    private final AccountThrottle throttle;
    private final AuthenticationAuditRecorder audit;
    private final int maxFailures;
    private final Duration blockDuration;

    public AutenticarUseCase(
            UsuarioRepository users,
            PasswordEncoder passwords,
            SessionIssuer sessions,
            AccountThrottle throttle,
            AuthenticationAuditRecorder audit,
            @Value("${app.auth.max-failures:5}") int maxFailures,
            @Value("${app.auth.block-minutes:15}") long blockMinutes) {
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.throttle = throttle;
        this.audit = audit;
        this.maxFailures = maxFailures;
        this.blockDuration = Duration.ofMinutes(blockMinutes);
    }

    public static String loginKey(String normalizedEmail) {
        return "login:usuario:" + normalizedEmail;
    }

    @Transactional
    public SessionIssuer.UsuarioSession execute(LoginRequest request) {
        String email = Emails.normalize(request.email());
        String key = loginKey(email);
        if (throttle.isBlocked(key)) {
            audit.record("login_blocked");
            throw blocked();
        }
        Optional<Usuario> found = users.findByEmail(email);
        if (found.isEmpty() || !passwords.matches(request.senha(), found.get().getSenha())) {
            boolean nowBlocked = throttle.recordFailure(key, maxFailures, blockDuration);
            found.ifPresentOrElse(
                    user -> audit.record("login_failed", SessionIssuer.principalOf(user)),
                    () -> audit.record("login_failed"));
            throw nowBlocked ? blocked() : new AccountException("invalid_credentials", 401);
        }
        throttle.reset(key);
        Usuario user = found.get();
        audit.record("login_success", SessionIssuer.principalOf(user));
        return sessions.openUsuario(user, Instant.now());
    }

    static AccountException blocked() {
        return new AccountException("blocked", 423);
    }
}
