package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.NivelAcesso;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login social via Firebase. A conta é vinculada pelo email, por isso o email precisa estar
 * verificado pelo provedor; caso contrário um terceiro poderia assumir uma conta existente.
 */
@Service
public class AutenticarFirebaseUseCase {
    private static final int UNUSABLE_PASSWORD_BYTES = 32;
    private static final int MAX_NAME_LENGTH = 255;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExternalIdentityVerifier verifier;
    private final UsuarioRepository users;
    private final PasswordEncoder passwords;
    private final SessionIssuer sessions;
    private final AuthenticationAuditRecorder audit;

    public AutenticarFirebaseUseCase(
            ExternalIdentityVerifier verifier,
            UsuarioRepository users,
            PasswordEncoder passwords,
            SessionIssuer sessions,
            AuthenticationAuditRecorder audit) {
        this.verifier = verifier;
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional
    public SessionIssuer.UsuarioSession execute(String idToken) {
        ExternalIdentityVerifier.ExternalIdentity identity = verifier.verify(idToken);
        if (identity.email() == null || identity.email().isBlank() || !identity.emailVerified()) {
            audit.record("firebase_email_unverified");
            throw new AccountException("email_not_verified", 403);
        }
        String email = Emails.normalize(identity.email());
        Usuario user = users.findByEmail(email).orElseGet(() -> register(identity, email));
        audit.record("firebase_login_success", SessionIssuer.principalOf(user));
        return sessions.openUsuario(user, Instant.now());
    }

    /** `usuario.senha` é NOT NULL: contas sociais recebem um hash aleatório que nenhuma senha satisfaz. */
    private Usuario register(ExternalIdentityVerifier.ExternalIdentity identity, String email) {
        Usuario user = new Usuario();
        user.setId(UUID.randomUUID());
        user.setNome(displayName(identity.name(), email));
        user.setEmail(email);
        user.setNivelAcesso(NivelAcesso.USUARIO);
        user.setSenha(passwords.encode(unusablePassword()));
        users.saveAndFlush(user);
        audit.record("firebase_registration_succeeded", SessionIssuer.principalOf(user));
        return user;
    }

    private static String displayName(String name, String email) {
        String value = name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name.trim();
        return value.length() > MAX_NAME_LENGTH ? value.substring(0, MAX_NAME_LENGTH) : value;
    }

    private static String unusablePassword() {
        byte[] bytes = new byte[UNUSABLE_PASSWORD_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
