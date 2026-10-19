package com.api.quimia.domain.catalog.internal.image;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class ImageStorageConfiguration {
    private static final Duration CONEXAO = Duration.ofSeconds(3);
    private static final Duration LEITURA = Duration.ofSeconds(15);
    /** O PDF da FDS chega a 10 MB: o prazo conta do envio do corpo até a resposta. */
    private static final Duration LEITURA_DO_PDF = Duration.ofSeconds(60);

    @Bean
    ArmazenamentoDeArquivos imageStorage(
            @Value("${quimia.cloudinary.cloud-name:}") String cloudName,
            @Value("${quimia.cloudinary.api-key:}") String apiKey,
            @Value("${quimia.cloudinary.api-secret:}") String apiSecret) {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            return new DisabledImageStorage();
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(CONEXAO).build();
        return new CloudinaryImageStorage(
                cliente(http, LEITURA), cliente(http, LEITURA_DO_PDF), Clock.systemUTC(), cloudName, apiKey, apiSecret);
    }

    private static RestClient cliente(HttpClient http, Duration leitura) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(leitura);
        return RestClient.builder().requestFactory(fabrica).build();
    }
}
