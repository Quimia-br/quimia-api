package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.account.internal.model.Empresa;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.persistence.EmpresaRepository;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Renova sessões sem estado: o refresh só vale enquanto a senha do titular não mudar. */
@Service
public class RenovarUseCase {
    private final UsuarioRepository users;
    private final EmpresaRepository empresas;
    private final SessionIssuer sessions;
    private final AccountThrottle throttle;
    private final AuthenticationAuditRecorder audit;

    public RenovarUseCase(
            UsuarioRepository users,
            EmpresaRepository empresas,
            SessionIssuer sessions,
            AccountThrottle throttle,
            AuthenticationAuditRecorder audit) {
        this.users = users;
        this.empresas = empresas;
        this.sessions = sessions;
        this.throttle = throttle;
        this.audit = audit;
    }

    @Transactional
    public SessionIssuer.UsuarioSession renewUsuario(String refreshToken) {
        SessionIssuer.RefreshGrant grant = sessions.readRefresh(refreshToken, AccountPrincipal.Type.USUARIO);
        Usuario user = users.findById(grant.principal().usuarioId())
                .filter(candidate -> sessions.isCurrentCredential(grant, candidate.getSenha()))
                .orElseThrow(() -> rejected(grant));
        if (throttle.isBlocked(AutenticarUseCase.loginKey(user.getEmail()))) {
            audit.record("refresh_blocked", grant.principal());
            throw AutenticarUseCase.blocked();
        }
        audit.record("refresh", grant.principal());
        return sessions.openUsuario(user, grant.authTime());
    }

    @Transactional(readOnly = true)
    public SessionIssuer.EmpresaSession renewEmpresa(String refreshToken) {
        SessionIssuer.RefreshGrant grant = sessions.readRefresh(refreshToken, AccountPrincipal.Type.EMPRESA);
        Empresa empresa = empresas.findById(grant.principal().empresaId())
                .filter(candidate -> sessions.isCurrentCredential(grant, candidate.getSenha()))
                .orElseThrow(() -> rejected(grant));
        if (!empresa.isAtiva()) {
            audit.record("refresh_inactive", grant.principal());
            throw AutenticarEmpresaUseCase.inactive();
        }
        if (throttle.isBlocked(AutenticarEmpresaUseCase.loginKey(Emails.normalize(empresa.getEmail())))) {
            audit.record("refresh_blocked", grant.principal());
            throw AutenticarUseCase.blocked();
        }
        audit.record("refresh", grant.principal());
        return sessions.openEmpresa(empresa, grant.authTime());
    }

    private AccountException rejected(SessionIssuer.RefreshGrant grant) {
        audit.record("refresh_failure", grant.principal());
        return SessionIssuer.invalidRefresh();
    }
}
