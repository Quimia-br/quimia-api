package com.api.quimia.domain.account;

import com.api.quimia.TestJwtKeys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.quimia.domain.account.internal.model.RefreshToken;
import com.api.quimia.domain.account.internal.model.Usuario;
import com.api.quimia.domain.account.internal.dto.LoginRequest;
import com.api.quimia.domain.account.internal.dto.RegisterRequest;
import com.api.quimia.domain.account.internal.persistence.RefreshTokenRepository;
import com.api.quimia.domain.account.internal.persistence.UsuarioRepository;
import com.api.quimia.domain.account.internal.usecase.AccountException;
import com.api.quimia.domain.account.internal.usecase.AutenticarUseCase;
import com.api.quimia.domain.account.internal.usecase.IssuedSession;
import com.api.quimia.domain.account.internal.usecase.RegistrarUseCase;
import com.api.quimia.domain.account.internal.usecase.RenovarUseCase;
import com.api.quimia.domain.account.internal.usecase.VerificarEmailUseCase;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = {com.api.quimia.Application.class, AccountTestConfig.class})
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestJwtKeys.class)
class RefreshTokenConcurrencyTest {
    private final java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    private RefreshTokenRepository refreshes;

    @Autowired
    private UsuarioRepository users;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RegistrarUseCase registrar;

    @Autowired
    private VerificarEmailUseCase verificar;

    @Autowired
    private AutenticarUseCase autenticar;

    @Autowired
    private RenovarUseCase renovar;

    @Autowired
    private AccountTestConfig.TokenProbe sender;

    @BeforeEach
    void clean() {
        refreshes.deleteAll();
        users.deleteAll();
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void refreshLookupSerializesConcurrentRotationOfTheSameToken() throws Exception {
        String hash = "a".repeat(64);
        UUID userId = UUID.randomUUID();
        Usuario user = new Usuario();
        user.setId(userId);
        user.setNome("Concurrent User");
        user.setEmail("concurrent@example.com");
        user.setNivelAcesso(NivelAcesso.USUARIO);
        users.saveAndFlush(user);

        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setTokenHash(hash);
        token.setFamilyId(UUID.randomUUID());
        token.setExpiresAt(OffsetDateTime.now().plusDays(30));
        token.setCreatedAt(OffsetDateTime.now());
        refreshes.saveAndFlush(token);

        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);

        var first = executor.submit(() -> transactions.executeWithoutResult(status -> {
            refreshes.findByTokenHash(hash).orElseThrow();
            firstLocked.countDown();
            await(releaseFirst);
        }));
        assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();

        var second = executor.submit(() -> transactions.executeWithoutResult(status -> {
            secondStarted.countDown();
            refreshes.findByTokenHash(hash).orElseThrow();
        }));
        assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            releaseFirst.countDown();
        }

        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
    }

    @Test
    void concurrentRotationProducesOnlyOneSessionAndRevokesItsFamily() throws Exception {
        registrar.execute(new RegisterRequest(
                "Concurrent Flow", "concurrent-flow@example.com", "senha-forte-concurrent", LocalDate.of(1990, 1, 1)));
        verificar.execute(sender.lastToken());
        var initial = autenticar.execute(
                new LoginRequest("concurrent-flow@example.com", "senha-forte-concurrent"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var first = executor.submit(() -> rotateTogether(initial.refreshToken(), ready, start));
        var second = executor.submit(() -> rotateTogether(initial.refreshToken(), ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        Object firstResult = first.get(10, TimeUnit.SECONDS);
        Object secondResult = second.get(10, TimeUnit.SECONDS);
        var results = Stream.of(firstResult, secondResult).toList();
        assertThat(results).filteredOn(IssuedSession.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(AccountException.class::isInstance).hasSize(1);

        IssuedSession winner = (IssuedSession) results.stream()
                .filter(IssuedSession.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThatThrownBy(() -> renovar.execute(winner.refreshToken()))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        error -> assertThat(error.code()).isEqualTo("invalid_refresh"));
    }

    @Test
    void reuseOfAncestorCannotLeaveAConcurrentDescendantActive() throws Exception {
        registrar.execute(new RegisterRequest(
                "Family Flow", "family-flow@example.com", "senha-forte-family", LocalDate.of(1990, 1, 1)));
        verificar.execute(sender.lastToken());
        var firstSession = autenticar.execute(
                new LoginRequest("family-flow@example.com", "senha-forte-family"));
        var currentSession = renovar.execute(firstSession.refreshToken());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var rotation = executor.submit(() -> rotateTogether(currentSession.refreshToken(), ready, start));
        var reuse = executor.submit(() -> rotateTogether(firstSession.refreshToken(), ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        Object rotated = rotation.get(10, TimeUnit.SECONDS);
        Object reused = reuse.get(10, TimeUnit.SECONDS);
        assertThat(reused).isInstanceOf(AccountException.class);
        if (rotated instanceof IssuedSession issued) {
            assertThatThrownBy(() -> renovar.execute(issued.refreshToken()))
                    .isInstanceOfSatisfying(
                            AccountException.class,
                            error -> assertThat(error.code()).isEqualTo("invalid_refresh"));
        } else {
            assertThat(rotated).isInstanceOf(AccountException.class);
            assertThatThrownBy(() -> renovar.execute(currentSession.refreshToken()))
                    .isInstanceOfSatisfying(
                            AccountException.class,
                            error -> assertThat(error.code()).isEqualTo("invalid_refresh"));
        }
    }

    private Object rotateTogether(String token, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            return renovar.execute(token);
        } catch (AccountException error) {
            return error;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for test coordination");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
