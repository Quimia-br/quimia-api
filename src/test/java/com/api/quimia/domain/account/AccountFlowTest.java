package com.api.quimia.domain.account;

import com.api.quimia.TestJwtKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.account.internal.dto.LoginRequest;
import com.api.quimia.domain.account.internal.dto.RegisterRequest;
import com.api.quimia.domain.account.internal.model.RefreshToken;
import com.api.quimia.domain.account.internal.persistence.RefreshTokenRepository;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import com.api.quimia.domain.account.internal.usecase.AccountException;
import com.api.quimia.domain.account.internal.usecase.AutenticarUseCase;
import com.api.quimia.domain.account.internal.usecase.EncerrarSessaoUseCase;
import com.api.quimia.domain.account.internal.usecase.PasswordRecoveryUseCase;
import com.api.quimia.domain.account.internal.usecase.PerfilUseCase;
import com.api.quimia.domain.account.internal.usecase.RecoveryCodeRequestedEvent;
import com.api.quimia.domain.account.internal.usecase.RegistrarUseCase;
import com.api.quimia.domain.account.internal.usecase.RenovarUseCase;
import com.api.quimia.domain.account.internal.usecase.TokenHasher;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@RecordApplicationEvents
@SpringBootTest(classes = {com.api.quimia.Application.class, AccountTestConfig.class})
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class AccountFlowTest {
    @Autowired
    private UsuarioRepository users;

    @Autowired
    private RefreshTokenRepository refreshes;

    @Autowired
    private RegistrarUseCase registrar;

    @Autowired
    private AutenticarUseCase autenticar;

    @Autowired
    private RenovarUseCase renovar;

    @Autowired
    private EncerrarSessaoUseCase encerrar;

    @Autowired
    private PasswordRecoveryUseCase recovery;

    @Autowired
    private PerfilUseCase perfil;

    @Autowired
    private TokenHasher hasher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM auditoria_autenticacao");
        users.deleteAll();
    }

    @Test
    void registerLoginRefreshMeLogout() {
        var summary = registrar.execute(new RegisterRequest(
                "Maria Silva", "maria@example.com", "senha-forte-123", LocalDate.of(1990, 5, 20)));
        assertThat(summary.email()).isEqualTo("maria@example.com");
        var session =
                autenticar.execute(new LoginRequest("maria@example.com", "senha-forte-123"));
        assertThat(session.response().accessToken()).isNotBlank();

        var me = perfil.current(summary.id());
        assertThat(me.email()).isEqualTo("maria@example.com");

        var renewed = renovar.execute(session.refreshToken());
        assertThat(renewed.refreshToken()).isNotEqualTo(session.refreshToken());
        assertThat(findRefresh(renewed.refreshToken()).getExpiresAt())
                .isEqualTo(findRefresh(session.refreshToken()).getExpiresAt());

        assertThatThrownBy(() -> renovar.execute(session.refreshToken()))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_refresh"));

        assertThatThrownBy(() -> renovar.execute(renewed.refreshToken()))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_refresh"));

        encerrar.execute(renewed.refreshToken());

        assertThat(auditEvents()).containsSequence(
                "registration_succeeded",
                "login_success",
                "refresh",
                "refresh_reuse",
                "refresh_reuse",
                "logout");
    }

    @Test
    void persistsLowercaseNivelAcessoAndReadsItAsTheApiEnum() {
        var user = registrar.execute(new RegisterRequest(
                "Enum Mapping User", "enum-mapping@example.com", "senha-forte-enum-123", LocalDate.of(1990, 5, 20)));

        String storedRole = jdbc.queryForObject(
                "SELECT nivel_acesso FROM usuario WHERE id = ?", String.class, user.id());

        assertThat(storedRole).isEqualTo("usuario");
        assertThat(users.findById(user.id()).orElseThrow().getNivelAcesso()).isEqualTo(NivelAcesso.USUARIO);
    }

    @Test
    void readsExistingLowercaseNivelAcessoFromPostgres() {
        var userId = java.util.UUID.randomUUID();
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, nivel_acesso, falhas_login) VALUES (?, ?, ?, ?, ?)",
                userId,
                "Legacy Role User",
                "legacy-role@example.com",
                "usuario",
                0);

        assertThat(users.findById(userId).orElseThrow().getNivelAcesso()).isEqualTo(NivelAcesso.USUARIO);
    }

    @Test
    void recoveryCodeIsConsumedBeforePasswordResetAndRevokesRefreshSessions() {
        var user = registrar.execute(new RegisterRequest(
                "Recovery User", "recovery-flow@example.com", "senha-inicial-forte-456", LocalDate.of(1990, 1, 1)));
        var session = autenticar.execute(new LoginRequest("recovery-flow@example.com", "senha-inicial-forte-456"));

        recovery.requestCode("recovery-flow@example.com");
        var event = applicationEvents.stream(RecoveryCodeRequestedEvent.class)
                .filter(candidate -> candidate.userId().equals(user.id()))
                .findFirst()
                .orElseThrow();

        var grant = recovery.verifyCode("recovery-flow@example.com", event.code());
        assertThat(grant.resetToken()).isNotBlank();
        assertThat(grant.expiresIn()).isEqualTo(600);
        assertThat(jdbc.queryForObject(
                        "SELECT codigo_hash FROM recuperacao_senha WHERE id_usuario = ?",
                        String.class,
                        user.id()))
                .isNull();
        assertThatThrownBy(() -> recovery.verifyCode("recovery-flow@example.com", event.code()))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_recovery_code"));

        recovery.resetPassword(grant.resetToken(), "Senha-nova-forte-2026!");

        assertThatThrownBy(() -> recovery.resetPassword(grant.resetToken(), "Senha-nova-forte-2026!"))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_reset_token"));
        assertThatThrownBy(() -> renovar.execute(session.refreshToken()))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_refresh"));
        var newSession = autenticar.execute(
                new LoginRequest("recovery-flow@example.com", "Senha-nova-forte-2026!"));
        assertThat(newSession.response().accessToken())
                .isNotBlank();
    }

    @Test
    void recoveryCodesExpireAndLockAfterFiveIncorrectAttempts() {
        var limitedUser = registrar.execute(new RegisterRequest(
                "Recovery Limit", "recovery-limit@example.com", "senha-forte-limit-123", LocalDate.of(1990, 1, 1)));
        recovery.requestCode("recovery-limit@example.com");
        var limitedEvent = applicationEvents.stream(RecoveryCodeRequestedEvent.class)
                .filter(candidate -> candidate.userId().equals(limitedUser.id()))
                .findFirst()
                .orElseThrow();
        String wrongCode = "00000000".equals(limitedEvent.code()) ? "00000001" : "00000000";

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> recovery.verifyCode("recovery-limit@example.com", wrongCode))
                    .isInstanceOfSatisfying(
                            AccountException.class,
                            error -> assertThat(error.code()).isEqualTo("invalid_recovery_code"));
        }
        assertThatThrownBy(() -> recovery.verifyCode("recovery-limit@example.com", limitedEvent.code()))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_recovery_code"));
        assertThat(jdbc.queryForObject(
                        "SELECT tentativas FROM recuperacao_senha WHERE id_usuario = ?",
                        Integer.class,
                        limitedUser.id()))
                .isEqualTo(5);

        var expiredUser = registrar.execute(new RegisterRequest(
                "Recovery Expired", "recovery-expired@example.com", "senha-forte-expired-123", LocalDate.of(1990, 1, 1)));
        recovery.requestCode("recovery-expired@example.com");
        var expiredEvent = applicationEvents.stream(RecoveryCodeRequestedEvent.class)
                .filter(candidate -> candidate.userId().equals(expiredUser.id()))
                .findFirst()
                .orElseThrow();
        jdbc.update(
                "UPDATE recuperacao_senha SET expira_em = ? WHERE id_usuario = ?",
                OffsetDateTime.now().minusSeconds(1),
                expiredUser.id());

        assertThatThrownBy(() -> recovery.verifyCode("recovery-expired@example.com", expiredEvent.code()))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_recovery_code"));
    }

    @Test
    void failedLoginsBlockAccount() {
        var created = registrar.execute(new RegisterRequest(
                "Joao Souza", "joao-block@example.com", "senha-forte-456", LocalDate.of(1992, 1, 10)));
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> autenticar.execute(new LoginRequest("joao-block@example.com", "errada-123456")))
                    .isInstanceOfSatisfying(
                            AccountException.class,
                            error -> assertThat(error.code()).isEqualTo("invalid_credentials"));
        }
        assertThatThrownBy(() -> autenticar.execute(new LoginRequest("joao-block@example.com", "errada-123456")))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("blocked"));
        assertThatThrownBy(() -> autenticar.execute(new LoginRequest("joao-block@example.com", "senha-forte-456")))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("blocked"));
        assertThat(auditEvents()).filteredOn("login_failed"::equals).hasSize(5);
        assertThat(auditEvents()).filteredOn("login_blocked"::equals).hasSize(1);
    }

    @Test
    void blockedAccountCannotRenewAnExistingRefreshToken() {
        registrar.execute(new RegisterRequest(
                "Blocked Refresh", "blocked-refresh@example.com", "senha-forte-refresh", LocalDate.of(1990, 1, 1)));
        var session = autenticar.execute(new LoginRequest("blocked-refresh@example.com", "senha-forte-refresh"));

        var user = users.findByEmail("blocked-refresh@example.com").orElseThrow();
        user.setBloqueadoAte(OffsetDateTime.now().plusMinutes(15));
        users.saveAndFlush(user);

        assertThatThrownBy(() -> renovar.execute(session.refreshToken()))
                .isInstanceOfSatisfying(AccountException.class, error -> {
                    assertThat(error.code()).isEqualTo("blocked");
                    assertThat(error.status()).isEqualTo(423);
                });
    }

    @Test
    void missingRefreshIsRejectedAsInvalidRefresh() {
        assertThatThrownBy(() -> renovar.execute(null))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_refresh"));
        assertThatThrownBy(() -> renovar.execute("  "))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_refresh"));
    }

    @Test
    void issuedAndRotatedRefreshTokensTrackActivityAndExpireByInactivity() {
        registrar.execute(new RegisterRequest(
                "Inactive User", "inactive@example.com", "senha-forte-789", LocalDate.of(1990, 1, 1)));

        var session = autenticar.execute(new LoginRequest("inactive@example.com", "senha-forte-789"));
        var initial = findRefresh(session.refreshToken());
        assertThat(initial.getLastUsedAt()).isNotNull();

        var renewed = renovar.execute(session.refreshToken());
        var rotated = findRefresh(renewed.refreshToken());
        assertThat(rotated.getLastUsedAt()).isNotNull();

        rotated.setLastUsedAt(OffsetDateTime.now().minusDays(8));
        refreshes.saveAndFlush(rotated);

        assertThatThrownBy(() -> renovar.execute(renewed.refreshToken()))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_refresh"));
    }

    @Test
    void refreshExpiresAtItsAbsoluteDeadline() {
        registrar.execute(new RegisterRequest(
                "Expired User", "expired@example.com", "senha-forte-expired", LocalDate.of(1990, 1, 1)));

        var session = autenticar.execute(new LoginRequest("expired@example.com", "senha-forte-expired"));
        var refresh = findRefresh(session.refreshToken());
        refresh.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        refreshes.saveAndFlush(refresh);

        assertThatThrownBy(() -> renovar.execute(session.refreshToken()))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_refresh"));
    }

    private RefreshToken findRefresh(String rawToken) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> refreshes.findByTokenHash(hasher.hash(rawToken)).orElseThrow());
    }

    private java.util.List<String> auditEvents() {
        return jdbc.queryForList("SELECT evento FROM auditoria_autenticacao ORDER BY id", String.class);
    }
}
