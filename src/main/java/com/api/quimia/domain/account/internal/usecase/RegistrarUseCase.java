package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.NivelAcesso;
import com.api.quimia.domain.account.dto.UserSummary;
import com.api.quimia.domain.account.internal.dto.RegisterRequest;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrarUseCase {
    private static final Set<String> WEAK = Set.of(
            "1234567890", "password123", "quimia12345", "senha123456", "abcdefghij", "qwerty1234");

    private final UsuarioRepository users;
    private final PasswordEncoder passwords;
    private final VerificationTokenGenerator tokens;
    private final EmailVerificationSender sender;
    private final AuthenticationAuditRecorder audit;

    public RegistrarUseCase(
            UsuarioRepository users,
            PasswordEncoder passwords,
            VerificationTokenGenerator tokens,
            EmailVerificationSender sender,
            AuthenticationAuditRecorder audit) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.sender = sender;
        this.audit = audit;
    }

    @Transactional
    public UserSummary execute(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new AccountException("email_in_use", 409);
        }
        if (WEAK.contains(request.senha())) {
            throw new AccountException("weak_password", 422);
        }
        if (request.dataNasc() != null && Period.between(request.dataNasc(), LocalDate.now()).getYears() < 18) {
            throw new AccountException("underage", 422);
        }
        Usuario user = new Usuario();
        user.setId(UUID.randomUUID());
        user.setNome(request.nome().trim());
        user.setEmail(email);
        user.setDataNasc(request.dataNasc());
        user.setNivelAcesso(NivelAcesso.USUARIO);
        user.setSenhaHash(passwords.encode(request.senha()));
        String raw = tokens.generate();
        user.setVerificationTokenHash(tokens.hash(raw));
        user.setVerificationExpiraEm(OffsetDateTime.now().plusHours(24));
        users.save(user);
        sender.send(user.getId(), user.getEmail(), raw);
        audit.record(user.getId(), "verification_sent");
        return new UserSummary(user.getId(), user.getNome(), user.getEmail());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
