package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.dto.UserView;
import com.api.quimia.domain.account.internal.dto.UpdateProfileRequest;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerfilUseCase {
    private final UsuarioRepository users;
    private final PasswordEncoder passwords;
    private final AccountThrottle throttle;
    private final AuthenticationAuditRecorder audit;
    private final int maxFailures;
    private final Duration blockDuration;

    public PerfilUseCase(
            UsuarioRepository users,
            PasswordEncoder passwords,
            AccountThrottle throttle,
            AuthenticationAuditRecorder audit,
            @Value("${app.auth.max-failures:5}") int maxFailures,
            @Value("${app.auth.block-minutes:15}") long blockMinutes) {
        this.users = users;
        this.passwords = passwords;
        this.throttle = throttle;
        this.audit = audit;
        this.maxFailures = maxFailures;
        this.blockDuration = Duration.ofMinutes(blockMinutes);
    }

    public static String emailChangeKey(UUID id) {
        return "email-change:usuario:" + id;
    }

    @Transactional(readOnly = true)
    public UserView current(UUID id) {
        return view(find(id));
    }

    @Transactional
    public UserView update(UUID id, UpdateProfileRequest request) {
        Usuario user = find(id);
        if (request.nome() != null) {
            user.setNome(request.nome().trim());
        }
        boolean removeDataNasc = Boolean.TRUE.equals(request.removerDataNasc());
        if (removeDataNasc && request.dataNasc() != null) {
            throw new AccountException("data_nasc_conflict", 400);
        }
        if (removeDataNasc) {
            user.setDataNasc(null);
        } else if (request.dataNasc() != null) {
            RegistrarUseCase.requireAdult(request.dataNasc());
            user.setDataNasc(request.dataNasc());
        }
        if (request.fotoUrl() != null) {
            user.setFotoUrl(request.fotoUrl());
        }
        if (request.email() != null) {
            changeEmail(user, request);
        }
        return view(user);
    }

    /**
     * O e-mail é o login e o destino da recuperação de senha. Sem verificação do endereço novo, a senha atual é o que
     * protege a troca; as tentativas erradas contam para o mesmo bloqueio temporário do login.
     */
    private void changeEmail(Usuario user, UpdateProfileRequest request) {
        String email = Emails.normalize(request.email());
        if (email.equals(user.getEmail())) {
            return;
        }
        String key = emailChangeKey(user.getId());
        if (throttle.isBlocked(key)) {
            throw AutenticarUseCase.blocked();
        }
        if (request.senhaAtual() == null || request.senhaAtual().isEmpty()) {
            throw new AccountException("current_password_required", 422);
        }
        if (!passwords.matches(request.senhaAtual(), user.getSenha())) {
            boolean nowBlocked = throttle.recordFailure(key, maxFailures, blockDuration);
            audit.record("email_change_failed", SessionIssuer.principalOf(user));
            throw nowBlocked ? AutenticarUseCase.blocked() : new AccountException("invalid_current_password", 403);
        }
        throttle.reset(key);
        if (users.existsByEmail(email)) {
            throw new AccountException("email_in_use", 409);
        }
        user.setEmail(email);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException concurrent) {
            throw new AccountException("email_in_use", 409);
        }
        audit.record("email_changed", SessionIssuer.principalOf(user));
    }

    private Usuario find(UUID id) {
        return users.findById(id).orElseThrow(() -> new AccountException("not_found", 404));
    }

    private static UserView view(Usuario user) {
        return new UserView(
                user.getId(),
                user.getNome(),
                user.getEmail(),
                user.getDataNasc(),
                user.getFotoUrl(),
                user.getNivelAcesso(),
                user.getUltimaSessao());
    }
}
