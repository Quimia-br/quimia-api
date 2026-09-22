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
import com.api.quimia.domain.account.internal.usecase.PerfilUseCase;
import com.api.quimia.domain.account.internal.usecase.RegistrarUseCase;
import com.api.quimia.domain.account.internal.usecase.RenovarUseCase;
import com.api.quimia.domain.account.internal.usecase.TokenHasher;
import com.api.quimia.domain.account.internal.usecase.VerificarEmailUseCase;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private VerificarEmailUseCase verificar;

    @Autowired
    private AutenticarUseCase autenticar;

    @Autowired
    private RenovarUseCase renovar;

    @Autowired
    private EncerrarSessaoUseCase encerrar;

    @Autowired
    private PerfilUseCase perfil;

    @Autowired
    private TokenHasher hasher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AccountTestConfig.TokenProbe sender;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM auditoria_autenticacao");
        users.deleteAll();
    }

    @Test
    void registerVerifyLoginRefreshMeLogout() {
        var summary = registrar.execute(new RegisterRequest(
                "Maria Silva", "maria@example.com", "senha-forte-123", LocalDate.of(1990, 5, 20)));
        assertThat(summary.email()).isEqualTo("maria@example.com");
        assertThat(sender.lastToken()).isNotBlank();

        assertThatThrownBy(() -> autenticar.execute(new LoginRequest("maria@example.com", "senha-forte-123")))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("email_not_verified"));

        verificar.execute(sender.lastToken());

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
                "verification_sent",
                "login_failed",
                "verified",
                "login_success",
                "refresh",
                "refresh_reuse",
                "refresh_reuse",
                "logout");
    }

    @Test
    void failedLoginsBlockAccount() {
        var created = registrar.execute(new RegisterRequest(
                "Joao Souza", "joao-block@example.com", "senha-forte-456", LocalDate.of(1992, 1, 10)));
        verificar.execute(sender.lastToken());
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
        verificar.execute(sender.lastToken());
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
        verificar.execute(sender.lastToken());

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
        verificar.execute(sender.lastToken());

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
