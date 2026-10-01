package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.NivelAcesso;
import com.api.quimia.domain.account.dto.UserSummary;
import com.api.quimia.domain.account.internal.dto.RegisterRequest;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrarUseCase {
    private static final int MINIMUM_AGE = 18;

    private final UsuarioRepository users;
    private final PasswordEncoder passwords;
    private final LocalizacaoUseCase localizacao;
    private final AuthenticationAuditRecorder audit;

    public RegistrarUseCase(
            UsuarioRepository users,
            PasswordEncoder passwords,
            LocalizacaoUseCase localizacao,
            AuthenticationAuditRecorder audit) {
        this.users = users;
        this.passwords = passwords;
        this.localizacao = localizacao;
        this.audit = audit;
    }

    @Transactional
    public UserSummary execute(RegisterRequest request) {
        String email = Emails.normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new AccountException("email_in_use", 409);
        }
        PasswordPolicy.validate(request.senha());
        requireAdult(request.dataNasc());
        Usuario user = new Usuario();
        user.setId(UUID.randomUUID());
        user.setNome(request.nome().trim());
        user.setEmail(email);
        user.setDataNasc(request.dataNasc());
        user.setNivelAcesso(NivelAcesso.USUARIO);
        user.setSenha(passwords.encode(request.senha()));
        users.saveAndFlush(user);
        if (request.localizacao() != null) {
            localizacao.replace(user.getId(), request.localizacao());
        }
        audit.record("registration_succeeded", SessionIssuer.principalOf(user));
        return new UserSummary(user.getId(), user.getNome(), user.getEmail());
    }

    static void requireAdult(LocalDate dataNasc) {
        if (dataNasc != null && Period.between(dataNasc, LocalDate.now()).getYears() < MINIMUM_AGE) {
            throw new AccountException("underage", 422);
        }
    }
}
