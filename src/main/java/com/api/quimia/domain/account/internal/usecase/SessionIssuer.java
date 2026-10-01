package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.domain.account.AccountPrincipal;
import com.api.quimia.domain.account.dto.EmpresaSummary;
import com.api.quimia.domain.account.dto.UserSummary;
import com.api.quimia.domain.account.internal.model.Empresa;
import com.api.quimia.domain.account.internal.model.Usuario;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Emite access e refresh como JWT sem estado no servidor. O refresh carrega a impressão da senha
 * atual e o instante do login, preservando expiração por inatividade e prazo absoluto.
 */
@Component
public class SessionIssuer {
    public static final String ACCESS_TOKEN = "access";
    public static final String REFRESH_TOKEN = "refresh";
    public static final String CLAIM_TYPE = "tipo";
    public static final String CLAIM_ROLE = "role";
    private static final String CLAIM_CREDENTIAL = "cred";
    private static final String CLAIM_AUTH_TIME = "auth_time";
    private static final String EMPRESA_ROLE = "EMPRESA";

    private final SignedTokenCodec codec;
    private final CredentialFingerprint fingerprints;
    private final Duration accessTtl;
    private final Duration refreshInactivity;
    private final Duration refreshAbsoluteTtl;

    public SessionIssuer(
            SignedTokenCodec codec,
            CredentialFingerprint fingerprints,
            @Value("${quimia.jwt.access-ttl-minutes:15}") long accessTtlMinutes,
            @Value("${app.auth.refresh-inactivity-days:7}") long refreshInactivityDays,
            @Value("${app.auth.refresh-ttl-days:30}") long refreshTtlDays) {
        this.codec = codec;
        this.fingerprints = fingerprints;
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
        this.refreshInactivity = Duration.ofDays(refreshInactivityDays);
        this.refreshAbsoluteTtl = Duration.ofDays(refreshTtlDays);
    }

    public UsuarioSession openUsuario(Usuario user, Instant authTime) {
        user.setUltimaSessao(OffsetDateTime.now(ZoneOffset.UTC));
        AccountPrincipal principal = principalOf(user);
        SessionTokens tokens = issue(principal, user.getNivelAcesso().name(), user.getSenha(), authTime);
        return new UsuarioSession(tokens, new UserSummary(user.getId(), user.getNome(), user.getEmail()));
    }

    public EmpresaSession openEmpresa(Empresa empresa, Instant authTime) {
        AccountPrincipal principal = principalOf(empresa);
        SessionTokens tokens = issue(principal, EMPRESA_ROLE, empresa.getSenha(), authTime);
        return new EmpresaSession(tokens, new EmpresaSummary(empresa.getId(), empresa.getNome(), empresa.getEmail()));
    }

    public RefreshGrant readRefresh(String token, AccountPrincipal.Type expectedType) {
        if (token == null || token.isBlank()) {
            throw invalidRefresh();
        }
        SignedTokenCodec.VerifiedToken verified = codec.verify(token, REFRESH_TOKEN).orElseThrow(SessionIssuer::invalidRefresh);
        Long authTime = verified.number(CLAIM_AUTH_TIME);
        if (!expectedType.claim().equals(verified.text(CLAIM_TYPE)) || authTime == null) {
            throw invalidRefresh();
        }
        return new RefreshGrant(
                new AccountPrincipal(expectedType, verified.subject()),
                verified.text(CLAIM_CREDENTIAL),
                Instant.ofEpochSecond(authTime));
    }

    public boolean isCurrentCredential(RefreshGrant grant, String passwordHash) {
        return fingerprints.matches(grant.principal(), passwordHash, grant.credential());
    }

    public static AccountPrincipal principalOf(Usuario user) {
        return new AccountPrincipal(AccountPrincipal.Type.USUARIO, user.getId().toString());
    }

    public static AccountPrincipal principalOf(Empresa empresa) {
        return new AccountPrincipal(AccountPrincipal.Type.EMPRESA, empresa.getId().toString());
    }

    public static AccountException invalidRefresh() {
        return new AccountException("invalid_refresh", 401);
    }

    private SessionTokens issue(AccountPrincipal principal, String role, String passwordHash, Instant authTime) {
        Instant now = Instant.now();
        String access = codec.sign(
                ACCESS_TOKEN,
                principal.id(),
                Map.of(CLAIM_TYPE, principal.type().claim(), CLAIM_ROLE, role),
                now.plus(accessTtl));
        Instant inactivityLimit = now.plus(refreshInactivity);
        Instant absoluteLimit = authTime.plus(refreshAbsoluteTtl);
        String refresh = codec.sign(
                REFRESH_TOKEN,
                principal.id(),
                Map.of(
                        CLAIM_TYPE, principal.type().claim(),
                        CLAIM_CREDENTIAL, fingerprints.of(principal, passwordHash),
                        CLAIM_AUTH_TIME, authTime.getEpochSecond()),
                inactivityLimit.isBefore(absoluteLimit) ? inactivityLimit : absoluteLimit);
        return new SessionTokens(access, accessTtl.toSeconds(), refresh);
    }

    public record SessionTokens(String accessToken, long expiresIn, String refreshToken) {}

    public record UsuarioSession(SessionTokens tokens, UserSummary user) {}

    public record EmpresaSession(SessionTokens tokens, EmpresaSummary empresa) {}

    public record RefreshGrant(AccountPrincipal principal, String credential, Instant authTime) {}
}
