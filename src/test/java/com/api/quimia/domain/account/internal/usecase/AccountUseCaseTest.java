package com.api.quimia.domain.account.internal.usecase;

import com.api.quimia.TestJwtKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.account.internal.dto.ResendVerificationRequest;
import com.api.quimia.domain.account.internal.dto.UpdateProfileRequest;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(classes = {com.api.quimia.Application.class, com.api.quimia.domain.account.AccountTestConfig.class})
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class AccountUseCaseTest {
    @Autowired
    private UsuarioRepository users;

    @Autowired
    private PerfilUseCase perfil;

    @Autowired
    private ReenviarVerificacaoUseCase reenviar;

    @Autowired
    private VerificarEmailUseCase verificar;

    @Autowired
    private com.api.quimia.domain.account.AccountTestConfig.TokenProbe sender;

    @Autowired
    private RegistrarUseCase registrar;

    @Autowired
    private PasswordEncoder passwords;

    @Test
    void resendIsGenericAndProfileValidates() {
        users.deleteAll();
        reenviar.execute(new ResendVerificationRequest("ghost@example.com"));

        var created = registrar.execute(new com.api.quimia.domain.account.internal.dto.RegisterRequest(
                "Case User", "case@example.com", "senha-forte-case", LocalDate.of(1988, 3, 3)));
        reenviar.execute(new ResendVerificationRequest("case@example.com"));
        String token = sender.lastToken();
        assertThat(token).isNotBlank();
        verificar.execute(token);

        var viewed = perfil.current(created.id());
        assertThat(viewed.nome()).isEqualTo("Case User");

        var updated = perfil.update(created.id(), new UpdateProfileRequest("Renamed", null));
        assertThat(updated.nome()).isEqualTo("Renamed");

        assertThatThrownBy(() ->
                        perfil.update(created.id(), new UpdateProfileRequest(null, LocalDate.now().minusYears(10))))
                .isInstanceOf(AccountException.class)
                .extracting(error -> ((AccountException) error).code())
                .isEqualTo("underage");
        assertThatThrownBy(() -> perfil.current(UUID.randomUUID()))
                .isInstanceOf(AccountException.class)
                .extracting(error -> ((AccountException) error).code())
                .isEqualTo("not_found");
        assertThat(created.email()).isEqualTo("case@example.com");
    }

    @Test
    void registrationStoresDelegatingBcryptWithCostTwelve() {
        users.deleteAll();
        registrar.execute(new com.api.quimia.domain.account.internal.dto.RegisterRequest(
                "Bcrypt User", "bcrypt@example.com", "senha-forte-bcrypt", LocalDate.of(1988, 3, 3)));

        String encoded = users.findByEmail("bcrypt@example.com").orElseThrow().getSenhaHash();
        assertThat(encoded).matches("\\{bcrypt}\\$2[aby]\\$12\\$.*");
        assertThat(passwords.matches("senha-forte-bcrypt", encoded)).isTrue();
    }
}
