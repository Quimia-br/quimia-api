package com.api.quimia.domain.account;

import com.api.quimia.TestJwtKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.account.internal.dto.LocalizacaoRequest;
import com.api.quimia.domain.account.internal.dto.LoginRequest;
import com.api.quimia.domain.account.internal.dto.RegisterRequest;
import com.api.quimia.domain.account.internal.persistence.LocalizacaoUsuarioRepository;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import com.api.quimia.domain.account.internal.usecase.AccountEmailRequestedEvent;
import com.api.quimia.domain.account.internal.usecase.AccountException;
import com.api.quimia.domain.account.internal.usecase.AutenticarUseCase;
import com.api.quimia.domain.account.internal.usecase.PasswordRecoveryUseCase;
import com.api.quimia.domain.account.internal.usecase.PerfilUseCase;
import com.api.quimia.domain.account.internal.usecase.RegistrarUseCase;
import com.api.quimia.domain.account.internal.usecase.RenovarUseCase;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
@SpringBootTest(classes = {com.api.quimia.Application.class, AccountTestConfig.class})
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class AccountFlowTest {
    private static final Pattern RECOVERY_CODE = Pattern.compile("\\b(\\d{4})\\b");

    @Autowired
    private UsuarioRepository users;

    @Autowired
    private LocalizacaoUsuarioRepository localizacoes;

    @Autowired
    private RegistrarUseCase registrar;

    @Autowired
    private AutenticarUseCase autenticar;

    @Autowired
    private RenovarUseCase renovar;

    @Autowired
    private PasswordRecoveryUseCase recovery;

    @Autowired
    private PerfilUseCase perfil;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void clean() {
        localizacoes.deleteAll();
        users.deleteAll();
    }

    @Test
    void registerWithAddressLoginRefreshAndReadProfile() {
        var summary = registrar.execute(new RegisterRequest(
                "Maria Silva",
                "Maria@Example.com",
                "senhaForte1",
                LocalDate.of(1990, 5, 20),
                new LocalizacaoRequest("06000000", "sp", "Centro", "Rua A", 100, "Apto 1")));
        assertThat(summary.email()).isEqualTo("maria@example.com");
        assertThat(localizacoes.findByUsuarioId(summary.id()).orElseThrow().getCep()).isEqualTo("06000-000");

        var session = autenticar.execute(new LoginRequest("maria@example.com", "senhaForte1"));
        assertThat(session.tokens().accessToken()).isNotBlank();
        assertThat(session.tokens().expiresIn()).isEqualTo(900);

        var renewed = renovar.renewUsuario(session.tokens().refreshToken());
        assertThat(renewed.user().id()).isEqualTo(summary.id());

        var me = perfil.current(summary.id());
        assertThat(me.email()).isEqualTo("maria@example.com");
        assertThat(me.ultimaSessao()).isNotNull();
    }

    @Test
    void persistsLowercaseNivelAcessoAndPasswordInSenhaColumn() {
        var user = registrar.execute(register("enum-mapping@example.com", "senhaEnum123"));

        String storedRole = jdbc.queryForObject("SELECT nivel_acesso FROM usuario WHERE id = ?", String.class, user.id());
        String storedPassword = jdbc.queryForObject("SELECT senha FROM usuario WHERE id = ?", String.class, user.id());

        assertThat(storedRole).isEqualTo("usuario");
        assertThat(storedPassword).matches("\\{bcrypt}\\$2[aby]\\$12\\$.*").doesNotContain("senhaEnum123");
        assertThat(users.findById(user.id()).orElseThrow().getNivelAcesso()).isEqualTo(NivelAcesso.USUARIO);
    }

    @Test
    void legacyRowsWithoutEncoderPrefixAuthenticateOnlyWhenBcrypt() {
        insertLegacyUser("legacy-bcrypt@example.com", new BCryptPasswordEncoder(10).encode("legado123"));
        insertLegacyUser("legacy-plain@example.com", "legado123");

        assertThat(autenticar.execute(new LoginRequest("legacy-bcrypt@example.com", "legado123")).user().email())
                .isEqualTo("legacy-bcrypt@example.com");
        assertAccountError(
                () -> autenticar.execute(new LoginRequest("legacy-plain@example.com", "legado123")),
                "invalid_credentials");
    }

    @Test
    void recoveryCodeResetsPasswordOnceAndRevokesExistingSessions() {
        var user = registrar.execute(register("recovery-flow@example.com", "senhaInicial1"));
        var session = autenticar.execute(new LoginRequest("recovery-flow@example.com", "senhaInicial1"));

        var challenge = recovery.requestCode("recovery-flow@example.com");
        assertThat(challenge.expiresIn()).isEqualTo(900);
        String code = recoveryCodeSentTo("recovery-flow@example.com");

        var grant = recovery.verifyCode(challenge.challengeToken(), "recovery-flow@example.com", code);
        assertThat(grant.expiresIn()).isEqualTo(600);
        assertAccountError(
                () -> recovery.verifyCode(challenge.challengeToken(), "recovery-flow@example.com", code),
                "invalid_recovery_code");

        recovery.resetPassword(grant.resetToken(), "senhaNova2026");

        assertAccountError(() -> recovery.resetPassword(grant.resetToken(), "outraSenha2026"), "invalid_reset_token");
        assertAccountError(() -> renovar.renewUsuario(session.tokens().refreshToken()), "invalid_refresh");
        assertAccountError(
                () -> autenticar.execute(new LoginRequest("recovery-flow@example.com", "senhaInicial1")),
                "invalid_credentials");
        assertThat(autenticar.execute(new LoginRequest("recovery-flow@example.com", "senhaNova2026")).user().id())
                .isEqualTo(user.id());
    }

    @Test
    void recoveryLocksAfterFiveWrongCodes() {
        registrar.execute(register("recovery-limit@example.com", "senhaLimite1"));
        var challenge = recovery.requestCode("recovery-limit@example.com");
        String code = recoveryCodeSentTo("recovery-limit@example.com");
        String wrongCode = "0000".equals(code) ? "0001" : "0000";

        for (int attempt = 0; attempt < 5; attempt++) {
            assertAccountError(
                    () -> recovery.verifyCode(challenge.challengeToken(), "recovery-limit@example.com", wrongCode),
                    "invalid_recovery_code");
        }
        assertAccountError(
                () -> recovery.verifyCode(challenge.challengeToken(), "recovery-limit@example.com", code),
                "invalid_recovery_code");
    }

    @Test
    void unknownEmailReceivesSameChallengeShapeAndNoEmail() {
        var challenge = recovery.requestCode("ninguem@example.com");

        assertThat(challenge.challengeToken()).isNotBlank();
        assertThat(challenge.expiresIn()).isEqualTo(900);
        assertThat(applicationEvents.stream(AccountEmailRequestedEvent.class)
                        .filter(event -> event.to().equals("ninguem@example.com")))
                .isEmpty();
        assertAccountError(
                () -> recovery.verifyCode(challenge.challengeToken(), "ninguem@example.com", "1234"),
                "invalid_recovery_code");
    }

    @Test
    void failedLoginsBlockAccountAndItsRefreshTokens() {
        registrar.execute(register("joao-block@example.com", "senhaForte456"));
        var session = autenticar.execute(new LoginRequest("joao-block@example.com", "senhaForte456"));
        for (int i = 0; i < 4; i++) {
            assertAccountError(
                    () -> autenticar.execute(new LoginRequest("joao-block@example.com", "errada123")),
                    "invalid_credentials");
        }
        assertAccountError(() -> autenticar.execute(new LoginRequest("joao-block@example.com", "errada123")), "blocked");
        assertAccountError(
                () -> autenticar.execute(new LoginRequest("joao-block@example.com", "senhaForte456")), "blocked");
        assertThatThrownBy(() -> renovar.renewUsuario(session.tokens().refreshToken()))
                .isInstanceOfSatisfying(AccountException.class, error -> assertThat(error.status()).isEqualTo(423));
    }

    @Test
    void missingOrForeignRefreshIsRejected() {
        assertAccountError(() -> renovar.renewUsuario(null), "invalid_refresh");
        assertAccountError(() -> renovar.renewUsuario("  "), "invalid_refresh");
        registrar.execute(register("access-as-refresh@example.com", "senhaForte789"));
        var session = autenticar.execute(new LoginRequest("access-as-refresh@example.com", "senhaForte789"));
        assertAccountError(() -> renovar.renewUsuario(session.tokens().accessToken()), "invalid_refresh");
        assertAccountError(() -> renovar.renewEmpresa(session.tokens().refreshToken()), "invalid_refresh");
    }

    @Test
    void registrationRejectsWeakPasswordAndMinors() {
        assertAccountError(() -> registrar.execute(register("weak@example.com", "somenteletras")), "weak_password");
        assertAccountError(
                () -> registrar.execute(new RegisterRequest(
                        "Menor", "minor@example.com", "senhaForte1", LocalDate.now().minusYears(10), null)),
                "underage");
        registrar.execute(register("dup@example.com", "senhaForte1"));
        assertAccountError(() -> registrar.execute(register("DUP@example.com", "senhaForte1")), "email_in_use");
    }

    private static RegisterRequest register(String email, String senha) {
        return new RegisterRequest("Pessoa Teste", email, senha, LocalDate.of(1990, 1, 1), null);
    }

    private void insertLegacyUser(String email, String senha) {
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha, nivel_acesso) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), "Legado", email, senha, "usuario");
    }

    private String recoveryCodeSentTo(String email) {
        String text = applicationEvents.stream(AccountEmailRequestedEvent.class)
                .filter(event -> event.to().equals(email))
                .reduce((first, second) -> second)
                .orElseThrow()
                .text();
        Matcher matcher = RECOVERY_CODE.matcher(text);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static void assertAccountError(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AccountException.class, error -> assertThat(error.code()).isEqualTo(code));
    }
}
