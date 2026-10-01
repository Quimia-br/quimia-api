package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.internal.dto.LoginRequest;
import com.api.quimia.domain.account.internal.model.Empresa;
import com.api.quimia.domain.account.internal.persistence.EmpresaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticarEmpresaUseCase {
    private static final Logger log = LoggerFactory.getLogger(AutenticarEmpresaUseCase.class);

    private final EmpresaRepository empresas;
    private final PasswordEncoder passwords;
    private final SessionIssuer sessions;
    private final AccountThrottle throttle;
    private final AuthenticationAuditRecorder audit;
    private final int maxFailures;
    private final Duration blockDuration;

    public AutenticarEmpresaUseCase(
            EmpresaRepository empresas,
            PasswordEncoder passwords,
            SessionIssuer sessions,
            AccountThrottle throttle,
            AuthenticationAuditRecorder audit,
            @Value("${app.auth.max-failures:5}") int maxFailures,
            @Value("${app.auth.block-minutes:15}") long blockMinutes) {
        this.empresas = empresas;
        this.passwords = passwords;
        this.sessions = sessions;
        this.throttle = throttle;
        this.audit = audit;
        this.maxFailures = maxFailures;
        this.blockDuration = Duration.ofMinutes(blockMinutes);
    }

    public static String loginKey(String normalizedEmail) {
        return "login:empresa:" + normalizedEmail;
    }

    @Transactional(readOnly = true)
    public SessionIssuer.EmpresaSession execute(LoginRequest request) {
        String email = Emails.normalize(request.email());
        String key = loginKey(email);
        if (throttle.isBlocked(key)) {
            audit.record("login_blocked");
            throw AutenticarUseCase.blocked();
        }
        Optional<Empresa> found = findUnique(empresas, email);
        if (found.isEmpty() || !passwords.matches(request.senha(), found.get().getSenha())) {
            boolean nowBlocked = throttle.recordFailure(key, maxFailures, blockDuration);
            found.ifPresentOrElse(
                    empresa -> audit.record("login_failed", SessionIssuer.principalOf(empresa)),
                    () -> audit.record("login_failed"));
            throw nowBlocked ? AutenticarUseCase.blocked() : new AccountException("invalid_credentials", 401);
        }
        throttle.reset(key);
        Empresa empresa = found.get();
        if (!empresa.isAtiva()) {
            audit.record("login_inactive", SessionIssuer.principalOf(empresa));
            throw inactive();
        }
        audit.record("login_success", SessionIssuer.principalOf(empresa));
        return sessions.openEmpresa(empresa, Instant.now());
    }

    /** Emails duplicados (legado sem UNIQUE) não autenticam, para não escolher uma empresa arbitrária. */
    static Optional<Empresa> findUnique(EmpresaRepository empresas, String normalizedEmail) {
        List<Empresa> matches = empresas.findAllByNormalizedEmail(normalizedEmail);
        if (matches.size() > 1) {
            log.warn("Ambiguous empresa email; {} rows share it", matches.size());
        }
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    static AccountException inactive() {
        return new AccountException("inactive", 403);
    }
}
