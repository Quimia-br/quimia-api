package com.api.quimia.domain.account.internal.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

class RecoveryEmailConfigurationTest {
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RecoveryEmailConfiguration.class, EmailTemplates.class)
            .withBean(JavaMailSender.class, () -> mailSender);

    @Test
    void smtpModeSendsHtmlWithPlainTextAlternativeAndInlineLogo() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        runner.withPropertyValues(
                        "app.email.sender-mode=smtp",
                        "app.email.from=Quimia <quimia.app@gmail.com>",
                        "spring.mail.username=quimia.app@gmail.com",
                        "spring.mail.password=app-password")
                .run(context -> {
                    boolean delivered = context.getBean(AccountMailer.class).send(
                            "pessoa@example.com", "Código de recuperação", "Seu código é 1234.", "<p>1234</p>");

                    ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
                    verify(mailSender).send(sent.capture());
                    MimeMessage message = sent.getValue();
                    String raw = raw(message);
                    assertThat(delivered).isTrue();
                    assertThat(message.getFrom()[0].toString()).isEqualTo("Quimia <quimia.app@gmail.com>");
                    assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString())
                            .isEqualTo("pessoa@example.com");
                    assertThat(message.getSubject()).isEqualTo("Código de recuperação");
                    assertThat(raw).contains(
                            "multipart/alternative",
                            "text/plain",
                            "text/html",
                            "multipart/related",
                            "image/png",
                            "Content-ID: <quimia-logo>");
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
        runner.run(context -> assertThat(
                        context.getBean(AccountMailer.class).send("x@example.com", "s", "t", "<p>t</p>"))
                .isFalse());
    }

    private static String raw(MimeMessage message) throws Exception {
        message.saveChanges();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        return output.toString(StandardCharsets.UTF_8);
    }
}
