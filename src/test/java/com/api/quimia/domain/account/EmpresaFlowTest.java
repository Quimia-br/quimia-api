package com.api.quimia.domain.account;

import com.api.quimia.TestJwtKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.account.internal.dto.EmpresaRegisterRequest;
import com.api.quimia.domain.account.internal.dto.LoginRequest;
import com.api.quimia.domain.account.internal.dto.UpdateEmpresaRequest;
import com.api.quimia.domain.account.internal.persistence.EmpresaRepository;
import com.api.quimia.domain.account.internal.usecase.AccountEmailRequestedEvent;
import com.api.quimia.domain.account.internal.usecase.AccountException;
import com.api.quimia.domain.account.internal.usecase.AutenticarEmpresaUseCase;
import com.api.quimia.domain.account.internal.usecase.EmpresaPasswordRecoveryUseCase;
import com.api.quimia.domain.account.internal.usecase.PerfilEmpresaUseCase;
import com.api.quimia.domain.account.internal.usecase.RegistrarEmpresaUseCase;
import com.api.quimia.domain.account.internal.usecase.RenovarUseCase;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
@SpringBootTest(classes = {com.api.quimia.Application.class, AccountTestConfig.class})
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class EmpresaFlowTest {
    private static final String VALID_CNPJ = "11.222.333/0001-81";
    private static final String ALPHANUMERIC_CNPJ = "12.ABC.345/01DE-35";
    private static final Pattern RESET_LINK_TOKEN = Pattern.compile("\\?token=(\\S+)");

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private RegistrarEmpresaUseCase registrar;

    @Autowired
    private AutenticarEmpresaUseCase autenticar;

    @Autowired
    private RenovarUseCase renovar;

    @Autowired
    private PerfilEmpresaUseCase perfil;

    @Autowired
    private EmpresaPasswordRecoveryUseCase recovery;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    void clean() {
        empresas.deleteAll();
    }

    @Test
    void registerLoginRefreshAndUpdateProfile() {
        var created = registrar.execute(new EmpresaRegisterRequest("Omo", VALID_CNPJ, "Contato@Omo.com", "senhaOmo123"));
        assertThat(created.email()).isEqualTo("contato@omo.com");
        assertThat(jdbc.queryForObject("SELECT cnpj FROM empresa WHERE id = ?", String.class, created.id()))
                .isEqualTo("11222333000181");

        var session = autenticar.execute(new LoginRequest("contato@omo.com", "senhaOmo123"));
        assertThat(session.empresa().id()).isEqualTo(created.id());
        assertThat(renovar.renewEmpresa(session.tokens().refreshToken()).empresa().nome()).isEqualTo("Omo");

        var updated = perfil.update(
                created.id(), new UpdateEmpresaRequest("Omo Brasil", "novo@omo.com", "https://cdn.example.com/omo.png"));
        assertThat(updated.nome()).isEqualTo("Omo Brasil");
        assertThat(updated.email()).isEqualTo("novo@omo.com");
        assertThat(updated.ativo()).isTrue();
    }

    @Test
    void registrationEnforcesCnpjAndApplicationLevelEmailUniqueness() {
        registrar.execute(new EmpresaRegisterRequest("Alfa", ALPHANUMERIC_CNPJ, "alfa@example.com", "senhaAlfa123"));

        assertAccountError(
                () -> registrar.execute(new EmpresaRegisterRequest("X", "4i84329", "x@example.com", "senhaX1234")),
                "invalid_cnpj");
        assertAccountError(
                () -> registrar.execute(new EmpresaRegisterRequest("Beta", VALID_CNPJ, "ALFA@example.com", "senhaBeta123")),
                "email_in_use");
        assertAccountError(
                () -> registrar.execute(new EmpresaRegisterRequest("Gama", "12abc34501de35", "gama@example.com", "senhaGama123")),
                "cnpj_in_use");
    }

    @Test
    void inactiveOrAmbiguousEmpresaCannotLogIn() {
        var created = registrar.execute(new EmpresaRegisterRequest("Inativa", VALID_CNPJ, "inativa@example.com", "senhaInativa1"));
        jdbc.update("UPDATE empresa SET ativo = FALSE WHERE id = ?", created.id());
        assertAccountError(() -> autenticar.execute(new LoginRequest("inativa@example.com", "senhaInativa1")), "inactive");

        String hash = jdbc.queryForObject("SELECT senha FROM empresa WHERE id = ?", String.class, created.id());
        jdbc.update("INSERT INTO empresa (nome, email, senha, ativo) VALUES (?, ?, ?, TRUE)", "Copia 1", "dup@example.com", hash);
        jdbc.update("INSERT INTO empresa (nome, email, senha, ativo) VALUES (?, ?, ?, TRUE)", "Copia 2", "DUP@example.com", hash);
        assertAccountError(
                () -> autenticar.execute(new LoginRequest("dup@example.com", "senhaInativa1")), "invalid_credentials");
    }

    @Test
    void recoveryLinkResetsPasswordOnce() {
        registrar.execute(new EmpresaRegisterRequest("Link", VALID_CNPJ, "link@example.com", "senhaAntiga1"));
        var session = autenticar.execute(new LoginRequest("link@example.com", "senhaAntiga1"));

        recovery.requestLink("link@example.com");
        String token = resetTokenSentTo("link@example.com");
        recovery.resetPassword(token, "senhaNova123");

        assertAccountError(() -> recovery.resetPassword(token, "outraSenha123"), "invalid_reset_token");
        assertAccountError(() -> renovar.renewEmpresa(session.tokens().refreshToken()), "invalid_refresh");
        assertThat(autenticar.execute(new LoginRequest("link@example.com", "senhaNova123")).empresa().email())
                .isEqualTo("link@example.com");
    }

    private String resetTokenSentTo(String email) {
        String text = applicationEvents.stream(AccountEmailRequestedEvent.class)
                .filter(event -> event.to().equals(email))
                .findFirst()
                .orElseThrow()
                .text();
        Matcher matcher = RESET_LINK_TOKEN.matcher(text);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static void assertAccountError(ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AccountException.class, error -> assertThat(error.code()).isEqualTo(code));
    }
}
