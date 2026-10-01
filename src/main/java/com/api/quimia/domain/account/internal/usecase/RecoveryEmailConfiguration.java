package com.api.quimia.domain.account.internal.usecase;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
    AccountMailer noOpAccountMailer() {
        return new NoOpAccountMailer();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.email", name = "sender-mode", havingValue = "resend")
    AccountMailer resendAccountMailer(
            org.springframework.core.env.Environment environment) {
        String apiKey = environment.getProperty("app.email.resend.api-key", "");
        String from = environment.getProperty("app.email.resend.from", "");
        if (apiKey.isBlank() || from.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY and APP_EMAIL_FROM are required when email sender mode is resend");
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return new ResendAccountMailer(RestClient.builder().requestFactory(requestFactory).build(), apiKey, from);
    }

    private static final class NoOpAccountMailer implements AccountMailer {
        private static final Logger log = LoggerFactory.getLogger(NoOpAccountMailer.class);

        @Override
        public boolean send(String to, String subject, String text) {
            log.info("Account email disabled subject={}", subject);
            return false;
        }
    }

    private static final class ResendAccountMailer implements AccountMailer {
        private final RestClient client;
        private final String apiKey;
        private final String from;

        private ResendAccountMailer(RestClient client, String apiKey, String from) {
            this.client = client;
            this.apiKey = apiKey;
            this.from = from;
        }

        @Override
        public boolean send(String to, String subject, String text) {
            Map<String, Object> payload = Map.of(
                    "from", from,
                    "to", List.of(to),
                    "subject", subject,
                    "text", text);
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
