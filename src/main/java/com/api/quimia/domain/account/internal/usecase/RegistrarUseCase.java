package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.NivelAcesso;
import com.api.quimia.domain.account.dto.UserSummary;
import com.api.quimia.domain.account.internal.dto.RegisterRequest;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrarUseCase {
    private final UsuarioRepository users;
    private final PasswordEncoder passwords;
    private final AuthenticationAuditRecorder audit;

    public RegistrarUseCase(
            UsuarioRepository users,
            PasswordEncoder passwords,
            AuthenticationAuditRecorder audit) {
        this.users = users;
        this.passwords = passwords;
        this.audit = audit;
    }

    @Transactional
    public UserSummary execute(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new AccountException("email_in_use", 409);
        }
        PasswordPolicy.validate(request.senha());
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
        users.save(user);
        audit.record(user.getId(), "registration_succeeded");
        return new UserSummary(user.getId(), user.getNome(), user.getEmail());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
