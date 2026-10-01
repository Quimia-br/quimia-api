package com.api.quimia.domain.account.internal.usecase;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
public class RecoveryEmailConfiguration {
    @Bean(name = "recoveryEmailExecutor")
    Executor recoveryEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("account-recovery-email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.email", name = "sender-mode", havingValue = "noop", matchIfMissing = true)
    AccountMailer noOpAccountMailer() {
        return new NoOpAccountMailer();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.email", name = "sender-mode", havingValue = "resend")
    AccountMailer resendAccountMailer(
            org.springframework.core.env.Environment environment, EmailTemplates templates) {
        String apiKey = environment.getProperty("app.email.resend.api-key", "");
        String from = environment.getProperty("app.email.from", "");
        if (apiKey.isBlank() || from.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY and APP_EMAIL_FROM are required when email sender mode is resend");
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return new ResendAccountMailer(
                RestClient.builder().requestFactory(requestFactory).build(), apiKey, from, templates.logo());
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.email", name = "sender-mode", havingValue = "smtp")
    AccountMailer smtpAccountMailer(
            JavaMailSender mailSender, org.springframework.core.env.Environment environment, EmailTemplates templates) {
        String username = environment.getProperty("spring.mail.username", "");
        String password = environment.getProperty("spring.mail.password", "");
        String from = environment.getProperty("app.email.from", "");
        if (username.isBlank() || password.isBlank() || from.isBlank()) {
            throw new IllegalStateException(
                    "SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD and APP_EMAIL_FROM are required when email sender mode is smtp");
        }
        return new SmtpAccountMailer(mailSender, from, templates.logo());
    }

    private static final class NoOpAccountMailer implements AccountMailer {
        private static final Logger log = LoggerFactory.getLogger(NoOpAccountMailer.class);

        @Override
        public boolean send(String to, String subject, String text, String html) {
            log.info("Account email disabled subject={}", subject);
            return false;
        }
    }

    /** Gmail exige que o remetente seja a própria conta autenticada (ou um alias verificado nela). */
    private static final class SmtpAccountMailer implements AccountMailer {
        private final JavaMailSender mailSender;
        private final String from;
        private final byte[] logo;

        private SmtpAccountMailer(JavaMailSender mailSender, String from, byte[] logo) {
            this.mailSender = mailSender;
            this.from = from;
            this.logo = logo;
        }

        @Override
        public boolean send(String to, String subject, String text, String html) {
            MimeMessage message = mailSender.createMimeMessage();
            try {
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                helper.setFrom(from);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(text, html);
                helper.addInline(EmailTemplates.LOGO_CONTENT_ID, new ByteArrayResource(logo), MediaType.IMAGE_PNG_VALUE);
            } catch (MessagingException error) {
                throw new MailPreparationException("Could not build account email", error);
            }
            mailSender.send(message);
            return true;
        }
    }

    private static final class ResendAccountMailer implements AccountMailer {
        private final RestClient client;
        private final String apiKey;
        private final String from;
        private final Map<String, Object> inlineLogo;

        private ResendAccountMailer(RestClient client, String apiKey, String from, byte[] logo) {
            this.client = client;
            this.apiKey = apiKey;
            this.from = from;
            this.inlineLogo = Map.of(
                    "content", Base64.getEncoder().encodeToString(logo),
                    "filename", EmailTemplates.LOGO_FILENAME,
                    "content_id", EmailTemplates.LOGO_CONTENT_ID);
        }

        @Override
        public boolean send(String to, String subject, String text, String html) {
            Map<String, Object> payload = Map.of(
                    "from", from,
                    "to", List.of(to),
                    "subject", subject,
                    "text", text,
                    "html", html,
                    "attachments", List.of(inlineLogo));
            client.post()
                    .uri("https://api.resend.com/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        }
    }
}
