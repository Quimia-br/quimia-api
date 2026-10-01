package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.account.internal.model.Empresa;
import com.api.quimia.domain.account.internal.persistence.EmpresaRepository;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recuperação do portal web por link de 24 horas (card de email do protótipo). */
@Service
public class EmpresaPasswordRecoveryUseCase {
    private static final Duration LINK_TTL = Duration.ofHours(24);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_REQUESTS_PER_HOUR = 5;
    private static final String EMAIL_SUBJECT = "Redefinição de senha do Quimia";

    private final EmpresaRepository empresas;
    private final PasswordEncoder passwords;
    private final PasswordResetTokens resetTokens;
    private final AccountThrottle throttle;
    private final ApplicationEventPublisher events;
    private final AuthenticationAuditRecorder audit;
    private final String resetUrl;

    public EmpresaPasswordRecoveryUseCase(
            EmpresaRepository empresas,
            PasswordEncoder passwords,
            PasswordResetTokens resetTokens,
            AccountThrottle throttle,
            ApplicationEventPublisher events,
            AuthenticationAuditRecorder audit,
            @Value("${app.auth.empresa-reset-url}") String resetUrl) {
        this.empresas = empresas;
        this.passwords = passwords;
        this.resetTokens = resetTokens;
        this.throttle = throttle;
        this.events = events;
        this.audit = audit;
        this.resetUrl = resetUrl;
    }

    @Transactional(readOnly = true)
    public void requestLink(String emailInput) {
        String email = Emails.normalize(emailInput);
        Optional<Empresa> found = AutenticarEmpresaUseCase.findUnique(empresas, email);
        if (found.isEmpty()) {
            audit.record("password_recovery_requested");
            return;
        }
        Empresa empresa = found.get();
        AccountPrincipal principal = SessionIssuer.principalOf(empresa);
        if (!throttle.tryAcquire("recovery-request:empresa:" + email, RESEND_COOLDOWN, MAX_REQUESTS_PER_HOUR)) {
            audit.record("password_recovery_throttled", principal);
            return;
        }
        String token = resetTokens.issue(principal, empresa.getSenha(), LINK_TTL);
        audit.record("password_recovery_requested", principal);
        events.publishEvent(new AccountEmailRequestedEvent(principal, empresa.getEmail(), EMAIL_SUBJECT, emailText(token)));
    }

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        PasswordResetTokens.ResetGrant grant = resetTokens.read(resetToken, AccountPrincipal.Type.EMPRESA);
        Empresa empresa = empresas.findById(grant.principal().empresaId()).orElseThrow(PasswordResetTokens::invalid);
        resetTokens.requireCurrent(grant, empresa.getSenha());
        PasswordPolicy.validate(newPassword);
        empresa.setSenha(passwords.encode(newPassword));
        throttle.reset(AutenticarEmpresaUseCase.loginKey(Emails.normalize(empresa.getEmail())));
        audit.record("password_recovery_completed", grant.principal());
    }

    private String emailText(String token) {
        return "Olá!\n\nRecebemos uma solicitação para redefinir a senha da sua conta no Quimia. "
                + "Se foi você quem pediu, acesse o link abaixo para criar uma nova senha:\n\n"
                + resetUrl + "?token=" + token + "\n\n"
                + "Este link é válido por apenas 24 horas. Se não foi você quem solicitou essa alteração, "
                + "pode ignorar este e-mail tranquilamente.\n\nAtenciosamente,\nEquipe Quimia";
    }
}
