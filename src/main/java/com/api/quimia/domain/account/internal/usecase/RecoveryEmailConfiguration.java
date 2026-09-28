package com.api.quimia.domain.account.internal.usecase;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
    RecoveryCodeSender noOpRecoveryCodeSender() {
        return new NoOpRecoveryCodeSender();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.email", name = "sender-mode", havingValue = "resend")
    RecoveryCodeSender resendRecoveryCodeSender(
            org.springframework.core.env.Environment environment) {
        String apiKey = environment.getProperty("app.email.resend.api-key", "");
        String from = environment.getProperty("app.email.resend.from", "");
        if (apiKey.isBlank() || from.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY and APP_EMAIL_FROM are required when email sender mode is resend");
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return new ResendRecoveryCodeSender(RestClient.builder().requestFactory(requestFactory).build(), apiKey, from);
    }

    private static final class NoOpRecoveryCodeSender implements RecoveryCodeSender {
        private static final Logger log = LoggerFactory.getLogger(NoOpRecoveryCodeSender.class);

        @Override
        public boolean send(UUID userId, String email, String code) {
            log.info("Recovery email disabled user={}", userId);
            return false;
        }
    }

    private static final class ResendRecoveryCodeSender implements RecoveryCodeSender {
        private final RestClient client;
        private final String apiKey;
        private final String from;

        private ResendRecoveryCodeSender(RestClient client, String apiKey, String from) {
            this.client = client;
            this.apiKey = apiKey;
            this.from = from;
        }

        @Override
        public boolean send(UUID userId, String email, String code) {
            Map<String, Object> payload = Map.of(
                    "from", from,
                    "to", List.of(email),
                    "subject", "Código de recuperação de senha",
                    "text", "Seu código de recuperação é " + code + ". Ele expira em 15 minutos. Se você não solicitou a recuperação, ignore esta mensagem.");
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
