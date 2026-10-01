package com.api.quimia.domain.account;

import com.api.quimia.TestJwtKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.api.quimia.domain.account.internal.dto.LoginRequest;
import com.api.quimia.domain.account.internal.dto.RegisterRequest;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import com.api.quimia.domain.account.internal.usecase.AccountException;
import com.api.quimia.domain.account.internal.usecase.AutenticarFirebaseUseCase;
import com.api.quimia.domain.account.internal.usecase.AutenticarUseCase;
import com.api.quimia.domain.account.internal.usecase.ExternalIdentityVerifier;
import com.api.quimia.domain.account.internal.usecase.ExternalIdentityVerifier.ExternalIdentity;
import com.api.quimia.domain.account.internal.usecase.RegistrarUseCase;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = {com.api.quimia.Application.class, AccountTestConfig.class})
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class FirebaseLoginTest {
    @MockitoBean
    private ExternalIdentityVerifier verifier;

    @Autowired
    private AutenticarFirebaseUseCase firebase;

    @Autowired
    private AutenticarUseCase autenticar;

    @Autowired
    private RegistrarUseCase registrar;

    @Autowired
    private UsuarioRepository users;

    @BeforeEach
    void clean() {
        users.deleteAll();
    }

    @Test
    void firstSocialLoginCreatesUserWithUnusablePasswordAndLaterLoginsReuseIt() {
        when(verifier.verify("google-token"))
                .thenReturn(new ExternalIdentity("Social@Example.com", true, "Pessoa Social"));

        var first = firebase.execute("google-token");
        var second = firebase.execute("google-token");

        assertThat(second.user().id()).isEqualTo(first.user().id());
        assertThat(first.user().email()).isEqualTo("social@example.com");
        assertThat(users.findById(first.user().id()).orElseThrow().getNome()).isEqualTo("Pessoa Social");
        assertThat(users.count()).isEqualTo(1);
        assertThatThrownBy(() -> autenticar.execute(new LoginRequest("social@example.com", "qualquer1")))
                .isInstanceOf(AccountException.class);
    }

    @Test
    void verifiedEmailLinksToExistingPasswordAccount() {
        var existing = registrar.execute(new RegisterRequest(
                "Conta Senha", "linked@example.com", "senhaForte1", LocalDate.of(1990, 1, 1), null));
        when(verifier.verify("microsoft-token")).thenReturn(new ExternalIdentity("linked@example.com", true, null));

        assertThat(firebase.execute("microsoft-token").user().id()).isEqualTo(existing.id());
    }

    @Test
    void unverifiedEmailIsRejected() {
        when(verifier.verify("unverified-token")).thenReturn(new ExternalIdentity("x@example.com", false, "X"));

        assertThatThrownBy(() -> firebase.execute("unverified-token"))
                .isInstanceOfSatisfying(AccountException.class, error -> {
                    assertThat(error.code()).isEqualTo("email_not_verified");
                    assertThat(error.status()).isEqualTo(403);
                });
        assertThat(users.count()).isZero();
    }
}
