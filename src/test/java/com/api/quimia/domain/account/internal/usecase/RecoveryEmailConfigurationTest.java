package com.api.quimia.domain.account.internal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class RecoveryEmailConfigurationTest {
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RecoveryEmailConfiguration.class)
            .withBean(JavaMailSender.class, () -> mailSender);

    @Test
    void smtpModeSendsPlainTextFromConfiguredSender() {
        runner.withPropertyValues(
                        "app.email.sender-mode=smtp",
                        "app.email.from=Quimia <quimia.app@gmail.com>",
                        "spring.mail.username=quimia.app@gmail.com",
                        "spring.mail.password=app-password")
                .run(context -> {
                    boolean delivered = context.getBean(AccountMailer.class)
                            .send("pessoa@example.com", "Código de recuperação", "Seu código é 1234.");

                    ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
                    verify(mailSender).send(sent.capture());
                    assertThat(delivered).isTrue();
                    assertThat(sent.getValue().getFrom()).isEqualTo("Quimia <quimia.app@gmail.com>");
                    assertThat(sent.getValue().getTo()).containsExactly("pessoa@example.com");
                    assertThat(sent.getValue().getSubject()).isEqualTo("Código de recuperação");
                    assertThat(sent.getValue().getText()).isEqualTo("Seu código é 1234.");
                });
    }

    @Test
    void smtpModeRefusesToStartWithoutCredentials() {
        runner.withPropertyValues("app.email.sender-mode=smtp", "app.email.from=quimia.app@gmail.com")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("SPRING_MAIL_PASSWORD"));
    }

    @Test
    void defaultModeDoesNotDeliver() {
        runner.run(context -> assertThat(context.getBean(AccountMailer.class).send("x@example.com", "s", "t"))
                .isFalse());
    }
}
