package com.api.quimia.domain.catalog.internal.fds;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class ExtratorFdsConfiguration {
    private static final Duration CONEXAO = Duration.ofSeconds(3);

    /**
     * Sem URL, a leitura de FDS fica desligada. A API não sobe com configuração que quebraria em uso: URL sem token
     * (o extrator exige), prazo não positivo, ou FDS obrigatória sem extrator (nenhum produto novo seria criado).
     */
    @Bean
    ExtratorFds extratorFds(
            @Value("${quimia.extrator.url:}") String url,
            @Value("${quimia.extrator.token:}") String token,
            @Value("${quimia.extrator.timeout-segundos:60}") long timeoutSegundos,
            @Value("${quimia.fds.obrigatoria:false}") boolean fdsObrigatoria) {
        if (url.isBlank()) {
            if (fdsObrigatoria) {
                throw new IllegalStateException("QUIMIA_EXTRATOR_URL is required when QUIMIA_FDS_OBRIGATORIA is true");
            }
            return new ExtratorFdsDesligado();
        }
        if (token.isBlank()) {
            throw new IllegalStateException("QUIMIA_EXTRATOR_TOKEN is required when QUIMIA_EXTRATOR_URL is set");
        }
        if (timeoutSegundos <= 0) {
            throw new IllegalStateException("QUIMIA_EXTRATOR_TIMEOUT_SEGUNDOS must be positive");
        }
        JdkClientHttpRequestFactory fabrica =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONEXAO).build());
        fabrica.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        return new HttpExtratorFds(RestClient.builder().requestFactory(fabrica).build(), url.trim(), token.trim());
    }
}
